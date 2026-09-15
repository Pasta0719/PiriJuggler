package jp.pirijuggler.paper.data;

import jp.pirijuggler.paper.config.FixedGameRules;
import jp.pirijuggler.paper.game.GameRules;
import jp.pirijuggler.paper.game.RoleWeights;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.reel.InternalRole;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Admin-only synthetic play that advances the real current-period machine data. */
public final class MachineDataSimulator {
    public record Result(int machineId,int setting,long games,long big,long reg,long difference,long maxDifference,long currentGames) {}

    public static Result run(Path databaseFile,RoleWeights weights,int machineId,int setting,long games,String period,RandomGenerator random,long now) throws Exception {
        if(databaseFile==null||weights==null||random==null||period==null||period.isBlank())throw new IllegalArgumentException("simulation args");
        if(machineId<1||setting<1||setting>6||games<1||games>100_000L)throw new IllegalArgumentException("simulation bounds");
        Class.forName("org.sqlite.JDBC");
        try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+databaseFile.toAbsolutePath())){
            try(var statement=db.createStatement()){statement.execute("PRAGMA foreign_keys=ON");statement.execute("PRAGMA busy_timeout=5000");}
            db.setAutoCommit(false);
            try{
                if(!query(db,"SELECT session_id FROM player_sessions WHERE machine_id=? AND lifecycle IN ('ACTIVE','SUSPENDED_GRACE') LIMIT 1",machineId).isEmpty())
                    throw new DomainException("MACHINE_OCCUPIED");
                var rows=query(db,"SELECT total_games,big_count,reg_count,current_games,today_difference,today_max_difference FROM machine_period_stats WHERE machine_id=? AND business_period_id=?",machineId,period);
                if(rows.isEmpty())throw new IllegalArgumentException("Missing machine stats");
                Map<String,Object> s=rows.getFirst();
                long total=n(s,"total_games"),big=n(s,"big_count"),reg=n(s,"reg_count"),current=n(s,"current_games"),difference=n(s,"today_difference"),max=n(s,"today_max_difference");
                long addedBig=0,addedReg=0;
                boolean free=false;
                String lastBonus=null;Long lastBonusAt=null;
                for(long i=0;i<games;i++){
                    int bet=free?0:FixedGameRules.NORMAL_BET;
                    InternalRole role=weights.draw(setting,random);
                    total=Math.addExact(total,1);current=Math.addExact(current,1);
                    difference=Math.addExact(difference,(long)GameRules.payout(role)-bet);
                    max=Math.max(max,difference);
                    long at=now+i;
                    execute(db,"INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(?,?,?,?,?)",machineId,period,total,difference,at);
                    String bonus=GameRules.bonus(role);
                    if(bonus!=null){
                        if(bonus.equals("BIG")){big=Math.addExact(big,1);addedBig++;}else{reg=Math.addExact(reg,1);addedReg++;}
                        execute(db,"INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(?,?,?,?,?)",machineId,period,bonus,current,at);
                        difference=Math.addExact(difference,(long)GameRules.bonusGross(bonus)-GameRules.bonusTotalBet(bonus));
                        max=Math.max(max,difference);
                        current=0;
                        execute(db,"INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(?,?,?,?,?)",machineId,period,total,difference,at);
                        lastBonus=bonus;lastBonusAt=at;
                    }
                    free=role==InternalRole.REPLAY;
                }
                execute(db,"UPDATE machine_period_stats SET total_games=?,big_count=?,reg_count=?,current_games=?,today_difference=?,today_max_difference=?,last_bonus_type=COALESCE(?,last_bonus_type),last_bonus_at=CASE WHEN ? IS NULL THEN last_bonus_at ELSE ? END WHERE machine_id=? AND business_period_id=?",
                        total,big,reg,current,difference,max,lastBonus,lastBonus,lastBonusAt,machineId,period);
                db.commit();
                return new Result(machineId,setting,games,addedBig,addedReg,difference,max,current);
            }catch(Exception error){db.rollback();throw error;}
            finally{db.setAutoCommit(true);}
        }
    }

    private static int execute(Connection db,String sql,Object...values)throws Exception{
        try(var ps=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)ps.setObject(i+1,values[i]);return ps.executeUpdate();}
    }
    private static java.util.List<Map<String,Object>> query(Connection db,String sql,Object...values)throws Exception{
        try(var ps=db.prepareStatement(sql)){
            for(int i=0;i<values.length;i++)ps.setObject(i+1,values[i]);
            try(var rs=ps.executeQuery()){
                var out=new java.util.ArrayList<Map<String,Object>>();
                while(rs.next()){
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(int i=1;i<=rs.getMetaData().getColumnCount();i++)row.put(rs.getMetaData().getColumnLabel(i),rs.getObject(i));
                    out.add(row);
                }
                return out;
            }
        }
    }
    private static long n(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private MachineDataSimulator(){}
}
