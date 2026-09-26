package jp.pirijuggler.paper.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 * /piri sim implementation for JUGGLER_GOD and JUGGLER_GOD_EXTREME.
 * One requested game is one normal game or one actual bonus payout round.
 * Zero-game transitions do not consume the requested game budget.
 */
public final class JugglerGodMachineDataSimulator {
    private enum Mode { NORMAL, HEAVEN }

    private static final class Cursor {
        final String kind;
        Mode mode=Mode.NORMAL;
        int heavenTarget;
        int heavenProgress;
        boolean freeReplay;

        String activeBonus="NONE";
        int activeRemaining;
        boolean activeInsideGod;
        boolean activeNeedsEntry;
        boolean activeEntryCharged;
        int activeHistoryGames;

        final ArrayDeque<String> stock=new ArrayDeque<>();
        boolean stockInsideGod;

        boolean postBonusPending;
        String postBonusOrigin="NORMAL";
        String postBonusType="BIG";

        boolean godChainActive;
        boolean godStartPending;
        int godGuaranteedRemaining;
        boolean continuationGamePending;
        boolean heavenAfterGod;
        int compensationBig;

        Cursor(String kind){this.kind=kind;}

        static Cursor from(String raw,String kind){
            Cursor c=new Cursor(kind);
            if(raw==null||raw.isBlank())return c;
            try{
                JsonObject j=JsonParser.parseString(raw).getAsJsonObject();
                if(!kind.equals(text(j,"kind","")))return c;
                c.mode=Mode.valueOf(text(j,"mode","NORMAL"));
                c.heavenTarget=value(j,"heavenTarget",0);
                c.heavenProgress=value(j,"heavenProgress",0);
                c.freeReplay=bool(j,"freeReplay",false);
                c.activeBonus=text(j,"activeBonus","NONE");
                c.activeRemaining=value(j,"activeRemaining",0);
                c.activeInsideGod=bool(j,"activeInsideGod",false);
                c.activeNeedsEntry=bool(j,"activeNeedsEntry",false);
                c.activeEntryCharged=bool(j,"activeEntryCharged",false);
                c.activeHistoryGames=value(j,"activeHistoryGames",0);
                if(j.has("stock")&&j.get("stock").isJsonArray())
                    for(var e:j.getAsJsonArray("stock")){
                        String type=e.getAsString();
                        if("BIG".equals(type)||"REG".equals(type))c.stock.addLast(type);
                    }
                c.stockInsideGod=bool(j,"stockInsideGod",false);
                c.postBonusPending=bool(j,"postBonusPending",false);
                c.postBonusOrigin=text(j,"postBonusOrigin","NORMAL");
                c.postBonusType=text(j,"postBonusType","BIG");
                c.godChainActive=bool(j,"godChainActive",false);
                c.godStartPending=bool(j,"godStartPending",false);
                c.godGuaranteedRemaining=value(j,"godGuaranteedRemaining",0);
                c.continuationGamePending=bool(j,"continuationGamePending",false);
                c.heavenAfterGod=bool(j,"heavenAfterGod",false);
                c.compensationBig=value(j,"compensationBig",0);
                if(c.activeRemaining<=0){
                    c.activeBonus="NONE";c.activeRemaining=0;c.activeNeedsEntry=false;c.activeEntryCharged=false;
                }
            }catch(RuntimeException ignored){}
            return c;
        }

        String json(){
            JsonObject j=new JsonObject();
            j.addProperty("version",3);j.addProperty("kind",kind);
            j.addProperty("mode",mode.name());j.addProperty("heavenTarget",heavenTarget);j.addProperty("heavenProgress",heavenProgress);
            j.addProperty("freeReplay",freeReplay);
            j.addProperty("activeBonus",activeBonus);j.addProperty("activeRemaining",activeRemaining);
            j.addProperty("activeInsideGod",activeInsideGod);j.addProperty("activeNeedsEntry",activeNeedsEntry);
            j.addProperty("activeEntryCharged",activeEntryCharged);j.addProperty("activeHistoryGames",activeHistoryGames);
            JsonArray q=new JsonArray();for(String type:stock)q.add(type);j.add("stock",q);
            j.addProperty("stockInsideGod",stockInsideGod);
            j.addProperty("postBonusPending",postBonusPending);j.addProperty("postBonusOrigin",postBonusOrigin);j.addProperty("postBonusType",postBonusType);
            j.addProperty("godChainActive",godChainActive);j.addProperty("godStartPending",godStartPending);
            j.addProperty("godGuaranteedRemaining",godGuaranteedRemaining);j.addProperty("continuationGamePending",continuationGamePending);
            j.addProperty("heavenAfterGod",heavenAfterGod);j.addProperty("compensationBig",compensationBig);
            return j.toString();
        }

        private static int value(JsonObject j,String k,int d){return j.has(k)?j.get(k).getAsInt():d;}
        private static boolean bool(JsonObject j,String k,boolean d){return j.has(k)?j.get(k).getAsBoolean():d;}
        private static String text(JsonObject j,String k,String d){return j.has(k)?j.get(k).getAsString():d;}
    }

    private static final class State {
        final Connection db;
        final RoleWeights weights;
        final RandomGenerator random;
        final int machineId,setting,bonusScalePpm,smallRoleScalePpm;
        final int godDenominator,godInGodBigStock,guaranteedBigs,continuationPercent,bigPayout,regPayout;
        final String period;
        final long normalBigToHeavenPpm,normalRegToHeavenPpm,heavenToHeavenPpm,targetSpins;
        long total,big,reg,current,difference,max,addedBig,addedReg,clock,simulatedSpins;
        String lastBonus;
        Long lastBonusAt;

        State(Connection db,RoleWeights weights,RandomGenerator random,int machineId,int setting,String period,
              int bonusScalePpm,int smallRoleScalePpm,int godDenominator,int godInGodBigStock,int guaranteedBigs,
              int continuationPercent,int bigPayout,int regPayout,
              long normalBigToHeavenPpm,long normalRegToHeavenPpm,long heavenToHeavenPpm,long targetSpins,
              long total,long big,long reg,long current,long difference,long max,long clock){
            this.db=db;this.weights=weights;this.random=random;this.machineId=machineId;this.setting=setting;this.period=period;
            this.bonusScalePpm=bonusScalePpm;this.smallRoleScalePpm=smallRoleScalePpm;
            this.godDenominator=godDenominator;this.godInGodBigStock=godInGodBigStock;this.guaranteedBigs=guaranteedBigs;
            this.continuationPercent=continuationPercent;this.bigPayout=bigPayout;this.regPayout=regPayout;
            this.normalBigToHeavenPpm=normalBigToHeavenPpm;this.normalRegToHeavenPpm=normalRegToHeavenPpm;this.heavenToHeavenPpm=heavenToHeavenPpm;
            this.targetSpins=targetSpins;this.total=total;this.big=big;this.reg=reg;this.current=current;this.difference=difference;this.max=max;this.clock=clock;
        }

        boolean hasBudget(){return simulatedSpins<targetSpins;}
        long at(){return clock++;}

        void graph(long at)throws Exception{
            execute(db,"INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(?,?,?,?,?)",
                    machineId,period,total,difference,at);
        }

        void advanceNormalGame(Cursor c,int payout)throws Exception{
            int bet=c.freeReplay?0:FixedGameRules.NORMAL_BET;
            c.freeReplay=false;
            simulatedSpins=Math.addExact(simulatedSpins,1);
            total=Math.addExact(total,1);current=Math.addExact(current,1);
            difference=Math.addExact(difference,(long)payout-bet);
            max=Math.max(max,difference);graph(at());
        }

        void advanceContinuationGame()throws Exception{
            simulatedSpins=Math.addExact(simulatedSpins,1);
            total=Math.addExact(total,1);current=Math.addExact(current,1);
            difference=Math.subtractExact(difference,FixedGameRules.NORMAL_BET);
            max=Math.max(max,difference);graph(at());
        }

        void consumeBonusRound()throws Exception{
            simulatedSpins=Math.addExact(simulatedSpins,1);
            difference=Math.addExact(difference,12);
            max=Math.max(max,difference);
        }

        void recordBonusHit(String type,int historyGames)throws Exception{
            if("BIG".equals(type)){big=Math.addExact(big,1);addedBig=Math.addExact(addedBig,1);}
            else if("REG".equals(type)){reg=Math.addExact(reg,1);addedReg=Math.addExact(addedReg,1);}
            else throw new IllegalArgumentException("bonus type");
            long eventAt=at();
            execute(db,"INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(?,?,?,?,?)",
                    machineId,period,type,historyGames,eventAt);
            lastBonus=type;lastBonusAt=eventAt;
        }

        void godHistory(int games)throws Exception{
            execute(db,"INSERT INTO juggler_god_history(machine_id,business_period_id,event_type,games,occurred_at) VALUES(?,?,'GOD',?,?)",
                    machineId,period,games,at());
        }

        void finishBonus()throws Exception{current=0;max=Math.max(max,difference);graph(at());}
        int bonusGames(String type){return ("BIG".equals(type)?bigPayout:regPayout)/14;}
    }

    public static MachineDataSimulator.Result run(
            Path databaseFile,RoleWeights weights,Map<String,Object> config,
            int machineId,int setting,long games,String period,RandomGenerator random,long now
    ) throws Exception {
        if(databaseFile==null||weights==null||config==null||random==null||period==null||period.isBlank())
            throw new IllegalArgumentException("simulation args");
        if(machineId<1||setting<1||setting>6||games<1||games>100_000L)throw new IllegalArgumentException("simulation bounds");

        Class.forName("org.sqlite.JDBC");
        try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+databaseFile.toAbsolutePath())){
            try(var statement=db.createStatement()){statement.execute("PRAGMA foreign_keys=ON");statement.execute("PRAGMA busy_timeout=5000");}
            db.setAutoCommit(false);
            try{
                var machineRows=query(db,"SELECT machine_type FROM machines WHERE machine_id=? AND deleted=0",machineId);
                if(machineRows.isEmpty())throw new IllegalArgumentException("Missing machine");
                String machineType=(String)machineRows.getFirst().get("machine_type");
                boolean extreme="JUGGLER_GOD_EXTREME".equals(machineType);
                if(!extreme&&!"JUGGLER_GOD".equals(machineType))throw new IllegalArgumentException("Wrong machine type");

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

                String key=cursorKey(period,machineId);
                Cursor c=Cursor.from(metadata(db,key),machineType);

                while(s.hasBudget()){
                    if((s.simulatedSpins&65535)==0&&Thread.currentThread().isInterrupted())
                        throw new java.util.concurrent.CancellationException("Simulator interrupted");

                    normalize(s,c);
                    if(!s.hasBudget())break;

                    if(c.continuationGamePending){
                        c.continuationGamePending=false;
                        s.advanceContinuationGame();
                        startBonus(s,c,"BIG",1,true,true);
                        continue;
                    }

                    if(c.activeRemaining>0){
                        playBonusRound(s,c);
                        continue;
                    }

                    boolean originHeaven=c.mode==Mode.HEAVEN;
                    InternalRole role;
                    if(random.nextInt(s.godDenominator)==0){
                        s.advanceNormalGame(c,GameRules.payout(InternalRole.GOD));
                        s.godHistory((int)s.current);
                        c.godStartPending=true;
                        c.heavenAfterGod=true;
                        c.postBonusPending=false;
                        continue;
                    }

                    if(originHeaven){
                        c.heavenProgress++;
                        role=c.heavenProgress>=c.heavenTarget
                                ?weights.drawBonusFamily(setting,random)
                                :weights.drawJugglerGodNonBonus(setting,random,bonusScale,smallRoleScale);
                    }else{
                        role=weights.drawJugglerGod(setting,random,bonusScale,smallRoleScale);
                    }

                    s.advanceNormalGame(c,GameRules.payout(role));
                    String bonus=GameRules.bonus(role);
                    if(bonus!=null){
                        c.postBonusPending=true;
                        c.postBonusOrigin=originHeaven?"HEAVEN":"NORMAL";
                        c.postBonusType=bonus;
                        startBonus(s,c,bonus,(int)s.current,false,true);
                    }else{
                        c.freeReplay=role==InternalRole.REPLAY;
                    }
                }

                execute(db,"UPDATE machine_period_stats SET total_games=?,big_count=?,reg_count=?,current_games=?,today_difference=?,today_max_difference=?,last_bonus_type=COALESCE(?,last_bonus_type),last_bonus_at=CASE WHEN ? IS NULL THEN last_bonus_at ELSE ? END WHERE machine_id=? AND business_period_id=?",
                        s.total,s.big,s.reg,s.current,s.difference,s.max,s.lastBonus,s.lastBonus,s.lastBonusAt,machineId,period);
                putMetadata(db,key,c.json());
                db.commit();
                return new MachineDataSimulator.Result(machineId,setting,s.simulatedSpins,s.big-beforeBig,s.reg-beforeReg,s.difference,s.max,s.current);
            }catch(Exception error){db.rollback();throw error;}
            finally{db.setAutoCommit(true);}
        }
    }

    private static void normalize(State s,Cursor c)throws Exception{
        if(c.activeRemaining>0||c.continuationGamePending)return;

        if(c.godStartPending){
            c.godStartPending=false;
            c.godChainActive=true;
            c.godGuaranteedRemaining=Math.max(0,s.guaranteedBigs-1);
            startBonus(s,c,"BIG",0,true,false);
            return;
        }

        if(c.godChainActive){
            if(!c.stock.isEmpty()){
                startBonus(s,c,c.stock.removeFirst(),0,true,true);
                return;
            }
            if(c.godGuaranteedRemaining>0){
                c.godGuaranteedRemaining--;
                s.difference=Math.subtractExact(s.difference,FixedGameRules.NORMAL_BET);
                startBonus(s,c,"BIG",0,true,true);
                return;
            }
            if(s.random.nextInt(100)<s.continuationPercent){
                c.continuationGamePending=true;
                return;
            }
            c.godChainActive=false;
        }

        if(c.compensationBig>0){
            c.compensationBig--;
            startBonus(s,c,"BIG",0,true,true);
            return;
        }

        if(!c.stock.isEmpty()){
            startBonus(s,c,c.stock.removeFirst(),0,c.stockInsideGod,true);
            return;
        }

        if(c.heavenAfterGod){
            c.heavenAfterGod=false;
            c.stockInsideGod=false;
            enterHeaven(s,c);
            return;
        }

        if(c.postBonusPending){
            c.postBonusPending=false;
            if("HEAVEN".equals(c.postBonusOrigin)){
                if(s.random.nextLong(1_000_000)<s.heavenToHeavenPpm)enterHeaven(s,c);
                else leaveHeaven(c);
            }else{
                long chance="BIG".equals(c.postBonusType)?s.normalBigToHeavenPpm:s.normalRegToHeavenPpm;
                if(s.random.nextLong(1_000_000)<chance)enterHeaven(s,c);
                else leaveHeaven(c);
            }
            c.stockInsideGod=false;
        }
    }

    private static void startBonus(State s,Cursor c,String type,int history,boolean insideGod,boolean needsEntry)throws Exception{
        if(!"BIG".equals(type)&&!"REG".equals(type))throw new IllegalArgumentException("bonus type");
        c.activeBonus=type;
        c.activeRemaining=s.bonusGames(type);
        c.activeInsideGod=insideGod;
        c.activeNeedsEntry=needsEntry;
        c.activeEntryCharged=false;
        c.activeHistoryGames=history;
        s.recordBonusHit(type,history);
    }

    private static void playBonusRound(State s,Cursor c)throws Exception{
        if(c.activeNeedsEntry&&!c.activeEntryCharged){
            s.difference=Math.subtractExact(s.difference,FixedGameRules.ENTRY_BET);
            c.activeEntryCharged=true;
        }

        s.consumeBonusRound();
        c.activeRemaining--;

        if(s.random.nextInt(s.godDenominator)==0){
            s.difference=Math.addExact(s.difference,GameRules.payout(InternalRole.GOD));
            s.max=Math.max(s.max,s.difference);
            s.godHistory(0);
            if(c.activeInsideGod){
                for(int i=0;i<s.godInGodBigStock;i++)c.stock.addLast("BIG");
                c.stockInsideGod=true;
            }else{
                // Production replacement semantics: the interrupted ordinary/HEAVEN bonus
                // ends here, then a full GOD chain runs, followed by one compensation BIG
                // and all stock already acquired before the GOD.
                finishActive(s,c);
                c.godStartPending=true;
                c.heavenAfterGod=true;
                c.compensationBig=Math.addExact(c.compensationBig,1);
                c.stockInsideGod=true;
                c.postBonusPending=false;
                return;
            }
        }else{
            InternalRole hit=s.weights.drawJugglerGod(s.setting,s.random,s.bonusScalePpm,s.smallRoleScalePpm);
            String next=GameRules.bonus(hit);
            if(next!=null)c.stock.addLast(next);
        }

        if(c.activeRemaining==0)finishActive(s,c);
    }

    private static void finishActive(State s,Cursor c)throws Exception{
        s.finishBonus();
        c.activeBonus="NONE";c.activeRemaining=0;c.activeInsideGod=false;
        c.activeNeedsEntry=false;c.activeEntryCharged=false;c.activeHistoryGames=0;
    }

    private static void enterHeaven(State s,Cursor c){
        c.mode=Mode.HEAVEN;c.heavenTarget=s.random.nextInt(32)+1;c.heavenProgress=0;
    }
    private static void leaveHeaven(Cursor c){c.mode=Mode.NORMAL;c.heavenTarget=0;c.heavenProgress=0;}

    private static String cursorKey(String period,int machineId){return "SIM_CURSOR:"+period+":"+machineId;}
    private static String metadata(Connection db,String key)throws Exception{
        var rows=query(db,"SELECT value FROM metadata WHERE key=?",key);
        return rows.isEmpty()?null:(String)rows.getFirst().get("value");
    }
    private static void putMetadata(Connection db,String key,String value)throws Exception{
        execute(db,"INSERT INTO metadata(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",key,value);
    }
    private static long number(Object value,long fallback){return value instanceof Number n?n.longValue():fallback;}
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
    private JugglerGodMachineDataSimulator(){}
}
