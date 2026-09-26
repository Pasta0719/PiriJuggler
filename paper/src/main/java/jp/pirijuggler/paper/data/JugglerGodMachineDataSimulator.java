package jp.pirijuggler.paper.data;

import jp.pirijuggler.paper.config.FixedGameRules;
import jp.pirijuggler.paper.database.StartupProfile;
import jp.pirijuggler.paper.game.GameRules;
import jp.pirijuggler.paper.game.RoleWeights;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.reel.InternalRole;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * /piri sim implementation for JUGGLER_GOD.
 * Mirrors the production successor economy instead of falling back to ordinary JUGGLER weights.
 */
public final class JugglerGodMachineDataSimulator {
    private enum Mode { NORMAL, HEAVEN }
    private record QueuedBonus(String type,int historyGames,boolean continuationGame) {}
    private record BonusRoundResult(boolean ordinaryGod,boolean completed) {}
    private record BonusPlayResult(boolean ordinaryGod,boolean completed) {}

    private static final class State {
        final Connection db;
        final RoleWeights weights;
        final RandomGenerator random;
        final int machineId,setting,bonusScalePpm,smallRoleScalePpm;
        final int godDenominator,godInGodBigStock,guaranteedBigs,continuationPercent,bigPayout,regPayout;
        final String period;
        final long normalBigToHeavenPpm,normalRegToHeavenPpm,heavenToHeavenPpm,targetSpins;
        long total,big,reg,current,difference,max,addedBig,addedReg,clock,simulatedSpins;
        boolean free;
        String lastBonus;
        Long lastBonusAt;
        Mode mode=Mode.NORMAL;
        int heavenTarget,heavenProgress;

        State(Connection db,RoleWeights weights,RandomGenerator random,int machineId,int setting,String period,
              int bonusScalePpm,int smallRoleScalePpm,int godDenominator,int godInGodBigStock,int guaranteedBigs,
              int continuationPercent,int bigPayout,int regPayout,
              long normalBigToHeavenPpm,long normalRegToHeavenPpm,long heavenToHeavenPpm,long targetSpins,
              long total,long big,long reg,long current,long difference,long max,long clock){
            this.db=db;this.weights=weights;this.random=random;this.machineId=machineId;this.setting=setting;this.period=period;
            this.bonusScalePpm=bonusScalePpm;this.smallRoleScalePpm=smallRoleScalePpm;
            this.godDenominator=godDenominator;this.godInGodBigStock=godInGodBigStock;this.guaranteedBigs=guaranteedBigs;
            this.continuationPercent=continuationPercent;this.bigPayout=bigPayout;this.regPayout=regPayout;
            this.normalBigToHeavenPpm=normalBigToHeavenPpm;this.normalRegToHeavenPpm=normalRegToHeavenPpm;this.heavenToHeavenPpm=heavenToHeavenPpm;this.targetSpins=targetSpins;
            this.total=total;this.big=big;this.reg=reg;this.current=current;this.difference=difference;this.max=max;this.clock=clock;
        }

        long at(){ return clock++; }
        boolean hasBudget(){ return simulatedSpins<targetSpins; }
        boolean consumeBonusSpin(){
            if(!hasBudget())return false;
            simulatedSpins=Math.addExact(simulatedSpins,1);
            difference=Math.addExact(difference,12);
            max=Math.max(max,difference);
            return true;
        }

        void graph(long at)throws Exception{
            execute(db,"INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(?,?,?,?,?)",
                    machineId,period,total,difference,at);
        }

        void advanceNormalGame(int payout)throws Exception{
            int bet=free?0:FixedGameRules.NORMAL_BET;
            free=false;
            simulatedSpins=Math.addExact(simulatedSpins,1);
            total=Math.addExact(total,1);
            current=Math.addExact(current,1);
            difference=Math.addExact(difference,(long)payout-bet);
            max=Math.max(max,difference);
            graph(at());
        }

        void chargeGuaranteedChainBet(){
            difference=Math.subtractExact(difference,FixedGameRules.NORMAL_BET);
        }

        void advanceContinuationGame()throws Exception{
            simulatedSpins=Math.addExact(simulatedSpins,1);
            total=Math.addExact(total,1);
            current=Math.addExact(current,1);
            difference=Math.subtractExact(difference,FixedGameRules.NORMAL_BET);
            max=Math.max(max,difference);
            graph(at());
        }

        void godHistory(int games)throws Exception{
            execute(db,"INSERT INTO juggler_god_history(machine_id,business_period_id,event_type,games,occurred_at) VALUES(?,?,'GOD',?,?)",
                    machineId,period,games,at());
        }

        void finishBonus(String type,int historyGames,int bonusCost)throws Exception{
            if("BIG".equals(type)){big=Math.addExact(big,1);addedBig=Math.addExact(addedBig,1);}
            else if("REG".equals(type)){reg=Math.addExact(reg,1);addedReg=Math.addExact(addedReg,1);}
            else throw new IllegalArgumentException("bonus type");
            long eventAt=at();
            execute(db,"INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(?,?,?,?,?)",
                    machineId,period,type,historyGames,eventAt);
            // Bonus payout rounds and entry costs are applied incrementally before completion.
            max=Math.max(max,difference);
            current=0;
            graph(eventAt);
            lastBonus=type;lastBonusAt=eventAt;
        }

        void enterHeaven(){
            mode=Mode.HEAVEN;heavenTarget=random.nextInt(32)+1;heavenProgress=0;
        }

        int bonusGross(String type){return "BIG".equals(type)?bigPayout:regPayout;}
        int bonusGames(String type){return bonusGross(type)/14;}
        int bonusTotalBet(String type){return FixedGameRules.ENTRY_BET+FixedGameRules.BONUS_BET*bonusGames(type);}
    }

    public static MachineDataSimulator.Result run(
            Path databaseFile,RoleWeights weights,Map<String,Object> config,
            int machineId,int setting,long games,String period,RandomGenerator random,long now
    ) throws Exception {
        if(databaseFile==null||weights==null||config==null||random==null||period==null||period.isBlank())
            throw new IllegalArgumentException("simulation args");
        if(machineId<1||setting<1||setting>6||games<1||games>100_000L)
            throw new IllegalArgumentException("simulation bounds");

        Class.forName("org.sqlite.JDBC");
        try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+databaseFile.toAbsolutePath())){
            try(var statement=db.createStatement()){
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
            }
            db.setAutoCommit(false);
            try{
                var machineRows=query(db,"SELECT machine_type FROM machines WHERE machine_id=? AND deleted=0",machineId);
                if(machineRows.isEmpty())throw new IllegalArgumentException("Missing machine");
                boolean extreme="JUGGLER_GOD_EXTREME".equals(machineRows.getFirst().get("machine_type"));
                Map<String,Object> tuning=StartupProfile.map(config.get(extreme?"juggler_god_extreme":"juggler_god"));
                long normalBigPpm=number(tuning.get("normal_big_to_heaven_ppm"),0);
                long normalRegPpm=number(tuning.get("normal_reg_to_heaven_ppm"),0);
                long heavenPpm=number(tuning.get("heaven_to_heaven_ppm"),0);
                int godDenominator=(int)number(tuning.get("god_denominator"),8192);
                int godInGodBigStock=(int)number(tuning.get("god_in_god_big_stock"),7);
                int guaranteedBigs=(int)number(tuning.get("god_guaranteed_bigs"),5);
                int bigPayout=(int)number(tuning.get("big_payout"),FixedGameRules.BIG_PAYOUT);
                int regPayout=(int)number(tuning.get("reg_payout"),FixedGameRules.REG_PAYOUT);
                Map<String,Object> settings=StartupProfile.map(tuning.get("settings"));
                Map<String,Object> row=StartupProfile.map(settings.get(Integer.toString(setting)));
                int bonusScale=(int)number(row.get("bonus_scale_ppm"),1_000_000);
                int smallRoleScale=(int)number(row.get("small_role_scale_ppm"),1_000_000);
                int continuation=(int)number(row.get("god_continuation_percent"),new int[]{0,75,78,80,82,85,90}[setting]);
                if(!query(db,"SELECT session_id FROM player_sessions WHERE machine_id=? AND lifecycle IN ('ACTIVE','SUSPENDED_GRACE') LIMIT 1",machineId).isEmpty())
                    throw new DomainException("MACHINE_OCCUPIED");
                var rows=query(db,"SELECT total_games,big_count,reg_count,current_games,today_difference,today_max_difference FROM machine_period_stats WHERE machine_id=? AND business_period_id=?",machineId,period);
                if(rows.isEmpty())throw new IllegalArgumentException("Missing machine stats");
                Map<String,Object> stats=rows.getFirst();
                State s=new State(db,weights,random,machineId,setting,period,bonusScale,smallRoleScale,
                        godDenominator,godInGodBigStock,guaranteedBigs,continuation,bigPayout,regPayout,
                        normalBigPpm,normalRegPpm,heavenPpm,games,
                        n(stats,"total_games"),n(stats,"big_count"),n(stats,"reg_count"),n(stats,"current_games"),
                        n(stats,"today_difference"),n(stats,"today_max_difference"),now);

                long beforeBig=s.big,beforeReg=s.reg;
                while(s.simulatedSpins<games){
                    if((s.simulatedSpins&65535)==0&&Thread.currentThread().isInterrupted())
                        throw new java.util.concurrent.CancellationException("Simulator interrupted");

                    // GOD is an independent profile draw before the normal/heaven role table.
                    if(random.nextInt(s.godDenominator)==0){
                        s.advanceNormalGame(GameRules.payout(InternalRole.GOD));
                        s.godHistory((int)s.current);
                        resolveGodChain(s);
                        if(s.hasBudget())s.enterHeaven();
                        continue;
                    }

                    InternalRole role;
                    boolean originHeaven=s.mode==Mode.HEAVEN;
                    if(originHeaven){
                        s.heavenProgress++;
                        role=s.heavenProgress>=s.heavenTarget
                                ?weights.drawBonusFamily(setting,random)
                                :weights.drawJugglerGodNonBonus(setting,random,bonusScale,smallRoleScale);
                    }else{
                        role=weights.drawJugglerGod(setting,random,bonusScale,smallRoleScale);
                    }

                    s.advanceNormalGame(GameRules.payout(role));
                    if(role==InternalRole.REPLAY)s.free=true;
                    String bonus=GameRules.bonus(role);
                    if(bonus==null)continue;

                    int historyGames=(int)s.current;
                    boolean forceHeaven=resolveOrdinaryBonus(s,bonus,historyGames);

                    if(forceHeaven){
                        s.enterHeaven();
                    }else if(originHeaven){
                        if(random.nextLong(1_000_000)<s.heavenToHeavenPpm)s.enterHeaven();
                        else{s.mode=Mode.NORMAL;s.heavenTarget=0;s.heavenProgress=0;}
                    }else{
                        long chance="BIG".equals(bonus)?s.normalBigToHeavenPpm:s.normalRegToHeavenPpm;
                        if(random.nextLong(1_000_000)<chance)s.enterHeaven();
                    }
                }

                execute(db,"UPDATE machine_period_stats SET total_games=?,big_count=?,reg_count=?,current_games=?,today_difference=?,today_max_difference=?,last_bonus_type=COALESCE(?,last_bonus_type),last_bonus_at=CASE WHEN ? IS NULL THEN last_bonus_at ELSE ? END WHERE machine_id=? AND business_period_id=?",
                        s.total,s.big,s.reg,s.current,s.difference,s.max,s.lastBonus,s.lastBonus,s.lastBonusAt,machineId,period);
                db.commit();
                return new MachineDataSimulator.Result(machineId,setting,s.simulatedSpins,s.big-beforeBig,s.reg-beforeReg,s.difference,s.max,s.current);
            }catch(Exception error){
                db.rollback();throw error;
            }finally{
                db.setAutoCommit(true);
            }
        }
    }

    private static boolean resolveOrdinaryBonus(State s,String initial,int initialHistoryGames)throws Exception{
        ArrayDeque<String> stock=new ArrayDeque<>();
        stock.addLast(initial);
        boolean first=true;
        while(!stock.isEmpty()&&s.hasBudget()){
            String type=stock.removeFirst();
            int history=first?initialHistoryGames:0;
            first=false;
            BonusPlayResult played=playStockBonus(s,type,history,false,stock);
            if(!played.completed())return false;
            if(played.ordinaryGod()){
                // Production semantics: GOD during an ordinary/heaven BIG or REG starts a
                // complete parent GOD chain and compensates the interrupted bonus with +1 BIG.
                resolveGodChain(s);
                if(!s.hasBudget())return true;
                stock.addFirst("BIG");
                while(!stock.isEmpty()&&s.hasBudget()){
                    BonusPlayResult released=playStockBonus(s,stock.removeFirst(),0,true,stock);
                    if(!released.completed())break;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean resolveGodChain(State s)throws Exception{
        if(!playParentGodBig(s,true,false))return false;
        for(int i=1;i<s.guaranteedBigs;i++){
            if(!s.hasBudget()||!playParentGodBig(s,false,false))return false;
        }
        int rate=s.continuationPercent;
        while(s.hasBudget()&&s.random.nextInt(100)<rate){
            if(!playParentGodBig(s,false,true))return false;
        }
        return true;
    }

    private static boolean playParentGodBig(State s,boolean first,boolean continuation)throws Exception{
        if(!s.hasBudget())return false;
        if(!first){
            if(continuation){
                s.advanceContinuationGame();
                if(!s.hasBudget())return false;
            }else s.chargeGuaranteedChainBet();
        }
        int history=continuation?1:0;
        int cost=first
                ?FixedGameRules.BONUS_BET*s.bonusGames("BIG")
                :s.bonusTotalBet("BIG");
        if(!first)s.difference=Math.subtractExact(s.difference,FixedGameRules.ENTRY_BET);
        ArrayDeque<String> stock=new ArrayDeque<>();
        BonusRoundResult rounds=drawBonusRounds(s,"BIG",true,stock);
        if(!rounds.completed())return false;
        s.finishBonus("BIG",history,cost);
        while(!stock.isEmpty()&&s.hasBudget()){
            BonusPlayResult released=playStockBonus(s,stock.removeFirst(),0,true,stock);
            if(!released.completed())return false;
        }
        return true;
    }

    private static BonusPlayResult playStockBonus(State s,String type,int history,boolean insideGod,ArrayDeque<String> stock)throws Exception{
        if(!s.hasBudget())return new BonusPlayResult(false,false);
        s.difference=Math.subtractExact(s.difference,FixedGameRules.ENTRY_BET);
        BonusRoundResult rounds=drawBonusRounds(s,type,insideGod,stock);
        if(!rounds.completed())return new BonusPlayResult(rounds.ordinaryGod(),false);
        s.finishBonus(type,history,s.bonusTotalBet(type));
        return new BonusPlayResult(rounds.ordinaryGod(),true);
    }

    private static BonusRoundResult drawBonusRounds(State s,String type,boolean insideGod,ArrayDeque<String> stock)throws Exception{
        int rounds=s.bonusGames(type);
        for(int i=0;i<rounds;i++){
            if(!s.consumeBonusSpin())return new BonusRoundResult(false,false);
            if(s.random.nextInt(s.godDenominator)==0){
                s.difference=Math.addExact(s.difference,GameRules.payout(InternalRole.GOD));
                s.max=Math.max(s.max,s.difference);
                s.godHistory(0);
                if(!insideGod)return new BonusRoundResult(true,true);
                for(int n=0;n<s.godInGodBigStock;n++)stock.addLast("BIG");
                continue;
            }
            InternalRole hit=s.weights.drawJugglerGod(s.setting,s.random,s.bonusScalePpm,s.smallRoleScalePpm);
            String next=GameRules.bonus(hit);
            if(next!=null)stock.addLast(next);
        }
        return new BonusRoundResult(false,true);
    }

    private static long number(Object value,long fallback){
        return value instanceof Number n?n.longValue():fallback;
    }

    private static int execute(Connection db,String sql,Object...values)throws Exception{
        try(var ps=db.prepareStatement(sql)){
            for(int i=0;i<values.length;i++)ps.setObject(i+1,values[i]);
            return ps.executeUpdate();
        }
    }

    private static java.util.List<Map<String,Object>> query(Connection db,String sql,Object...values)throws Exception{
        try(var ps=db.prepareStatement(sql)){
            for(int i=0;i<values.length;i++)ps.setObject(i+1,values[i]);
            try(var rs=ps.executeQuery()){
                var out=new java.util.ArrayList<Map<String,Object>>();
                while(rs.next()){
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(int i=1;i<=rs.getMetaData().getColumnCount();i++)
                        row.put(rs.getMetaData().getColumnLabel(i),rs.getObject(i));
                    out.add(row);
                }
                return out;
            }
        }
    }

    private static long n(Map<String,Object> row,String key){
        return ((Number)row.get(key)).longValue();
    }

    private JugglerGodMachineDataSimulator(){}
}
