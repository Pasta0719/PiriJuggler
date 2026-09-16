package jp.pirijuggler.paper.database;

import jp.pirijuggler.common.reel.Reel;
import jp.pirijuggler.paper.game.GameRules;
import jp.pirijuggler.paper.game.PremiumPolicy;
import jp.pirijuggler.paper.game.RoleWeights;
import jp.pirijuggler.paper.reel.DisplayRole;
import jp.pirijuggler.paper.reel.InternalRole;
import jp.pirijuggler.paper.reel.StopSolver;
import jp.pirijuggler.paper.reel.StopTriplet;
import jp.pirijuggler.paper.session.Session;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Phase11 machine-lock force settlement. All calls run inside the caller's DB transaction.
 * It consumes a private recovery RNG stream; no draw, role or premium is exposed publicly.
 */
public final class RecoveryStore {
    private final PiriDatabase db;
    private final RoleWeights weights;
    private final PremiumPolicy premiums;
    private final StopSolver solver;
    private final long seed;
    private final Map<Integer, SplittableRandom> random = new HashMap<>();

    public RecoveryStore(PiriDatabase db, Map<String,Object> config, StopSolver solver) {
        this(db,config,solver,new SecureRandom().nextLong());
    }

    RecoveryStore(PiriDatabase db, Map<String,Object> config, StopSolver solver, long seed) {
        this.db=db;
        this.weights=new RoleWeights(config);
        this.premiums=new PremiumPolicy(config);
        this.solver=solver;
        this.seed=seed;
    }

    /** Settle one unresolved session to SEATED_READY while preserving source_business_period_id. */
    public Session settle(Session before,long now) throws Exception {
        if(before.ready())return before;
        int machine=before.machine();
        String period=before.text("source_business_period_id");
        Map<String,Object> statRow=row("SELECT * FROM machine_period_stats WHERE machine_id=? AND business_period_id=?",machine,period);
        Stats stats=new Stats(statRow);
        Map<String,Object> values=new LinkedHashMap<>(before.snapshot());
        int setting=((Number)row("SELECT setting FROM machines WHERE machine_id=?",machine).get("setting")).intValue();

        switch(before.state()) {
            case NORMAL_BETTED -> settleFreshNormal(values,stats,setting,now);
            case NORMAL_SPINNING -> settleStoredNormal(values,stats,now);
            case REPLAY_READY -> settleReplayChain(values,stats,setting,now);
            case BONUS_PENDING_BIG -> settleUnstartedBonus(values,stats,"BIG",false,now);
            case BONUS_PENDING_REG -> settleUnstartedBonus(values,stats,"REG",false,now);
            case BONUS_ENTRY_BETTED_BIG -> settleUnstartedBonus(values,stats,"BIG",true,now);
            case BONUS_ENTRY_BETTED_REG -> settleUnstartedBonus(values,stats,"REG",true,now);
            case BONUS_ENTRY_SPINNING_BIG -> {
                completeSpecialSpin(values,DisplayRole.BIG_ENTRY);
                settleUnstartedBonus(values,stats,"BIG",true,now);
            }
            case BONUS_ENTRY_SPINNING_REG -> {
                completeSpecialSpin(values,DisplayRole.REG_ENTRY);
                settleUnstartedBonus(values,stats,"REG",true,now);
            }
            case BIG_READY -> settleRunningBonus(values,stats,"BIG",false,now);
            case REG_READY -> settleRunningBonus(values,stats,"REG",false,now);
            case BIG_BETTED -> settleRunningBonus(values,stats,"BIG",true,now);
            case REG_BETTED -> settleRunningBonus(values,stats,"REG",true,now);
            case BIG_SPINNING -> {
                completeBonusSpin(values);
                settleRunningBonus(values,stats,"BIG",true,now);
            }
            case REG_SPINNING -> {
                completeBonusSpin(values);
                settleRunningBonus(values,stats,"REG",true,now);
            }
            case SEATED_READY -> { }
        }

        finish(values,now);
        db.sql("UPDATE player_sessions SET game_state=?,credit=?,held_medals=?,spin_id=?,internal_role=?,premium_type=?,notice_state=?,lamp_on=?,bonus_type=?,bonus_payout_count=?,current_bet=?,pay_display=?,display_left_stop=?,display_center_stop=?,display_right_stop=?,stopped_mask=?,phase_left=?,phase_center=?,phase_right=?,motion_profile=?,last_activity=? WHERE session_id=?",
                values.get("game_state"),values.get("credit"),values.get("held_medals"),values.get("spin_id"),values.get("internal_role"),values.get("premium_type"),values.get("notice_state"),values.get("lamp_on"),values.get("bonus_type"),values.get("bonus_payout_count"),values.get("current_bet"),values.get("pay_display"),values.get("display_left_stop"),values.get("display_center_stop"),values.get("display_right_stop"),values.get("stopped_mask"),values.get("phase_left"),values.get("phase_center"),values.get("phase_right"),values.get("motion_profile"),values.get("last_activity"),before.id().toString());
        db.sql("UPDATE machine_period_stats SET total_games=?,big_count=?,reg_count=?,current_games=?,today_difference=?,today_max_difference=?,last_bonus_type=?,last_bonus_at=? WHERE machine_id=? AND business_period_id=?",
                stats.total,stats.big,stats.reg,stats.current,stats.difference,stats.max,stats.lastBonus,stats.lastBonusAt,machine,period);
        db.sql("UPDATE machines SET last_left_stop=?,last_center_stop=?,last_right_stop=?,updated_at=? WHERE machine_id=?",
                values.get("display_left_stop"),values.get("display_center_stop"),values.get("display_right_stop"),now,machine);
        return new Session(values);
    }

    private void settleFreshNormal(Map<String,Object> values,Stats stats,int setting,long now) throws Exception {
        InternalRole role=draw(values,setting);
        stats.total=Math.addExact(stats.total,1);stats.current=Math.addExact(stats.current,1);
        settleNormalRole(values,stats,role,premium(values),0,now);
    }

    private void settleStoredNormal(Map<String,Object> values,Stats stats,long now) throws Exception {
        String raw=(String)values.get("internal_role");
        if(raw==null)throw new IllegalStateException("NORMAL_SPINNING missing internal_role");
        settleNormalRole(values,stats,InternalRole.valueOf(raw),premium(values),((Number)values.get("stopped_mask")).intValue(),now);
    }

    private void settleReplayChain(Map<String,Object> values,Stats stats,int setting,long now) throws Exception {
        while(true){
            InternalRole role=draw(values,setting);
            stats.total=Math.addExact(stats.total,1);stats.current=Math.addExact(stats.current,1);
            boolean replay=settleNormalRole(values,stats,role,premium(values),0,now);
            if(!replay)return;
        }
    }

    /** @return true only when force settlement must auto-play another free replay. */
    private boolean settleNormalRole(Map<String,Object> values,Stats stats,InternalRole role,PremiumPolicy.Type premium,int mask,long now) throws Exception {
        StopTriplet finalStops=completeNormal(values,role,premium,mask);
        values.put("display_left_stop",finalStops.left());values.put("display_center_stop",finalStops.center());values.put("display_right_stop",finalStops.right());
        var evaluation=solver.catalogue().evaluation(finalStops);
        DisplayRole directRole=(premium==PremiumPolicy.Type.B||premium==PremiumPolicy.Type.F)?null:role.directEntryDisplay();
        boolean direct=directRole!=null&&evaluation.valid(directRole);
        int payout=direct?0:GameRules.payout(role);
        addAssets(values,payout);stats.addDifference(payout);
        graph(values,stats,now);

        String bonus=GameRules.bonus(role);
        if(bonus!=null){
            if(direct)settleUnstartedBonus(values,stats,bonus,true,now);
            else settleUnstartedBonus(values,stats,bonus,false,now);
            return false;
        }
        return role==InternalRole.REPLAY;
    }

    private InternalRole draw(Map<String,Object> values,int setting) {
        SplittableRandom rng=rng(((Number)values.get("machine_id")).intValue());
        InternalRole role=weights.draw(setting,rng);
        PremiumPolicy.Type p=premiums.draw(role,rng).orElse(null);
        values.put("internal_role",role.name());values.put("premium_type",p==null?null:p.name());
        return role;
    }

    private PremiumPolicy.Type premium(Map<String,Object> values){
        Object raw=values.get("premium_type");return raw==null?null:PremiumPolicy.Type.valueOf((String)raw);
    }

    private StopTriplet completeNormal(Map<String,Object> values,InternalRole role,PremiumPolicy.Type p,int mask){
        StopTriplet stops=currentStops(values);
        DisplayRole display=role.display(p==PremiumPolicy.Type.B);
        DisplayRole alternate=(p==PremiumPolicy.Type.B||p==PremiumPolicy.Type.F)?null:role.directEntryDisplay();
        boolean premiumF=p==PremiumPolicy.Type.F;
        boolean allowBar=p!=null;
        return complete(values,stops,mask,display,alternate,premiumF,allowBar);
    }

    private void completeSpecialSpin(Map<String,Object> values,DisplayRole role){
        StopTriplet stops=complete(values,currentStops(values),((Number)values.get("stopped_mask")).intValue(),role,null,false,false);
        putStops(values,stops);
    }

    private void completeBonusSpin(Map<String,Object> values){
        String raw=(String)values.get("internal_role");if(raw==null)throw new IllegalStateException("Bonus spin missing display role");
        completeSpecialSpin(values,DisplayRole.valueOf(raw));
    }

    private StopTriplet complete(Map<String,Object> values,StopTriplet starts,int mask,DisplayRole role,DisplayRole alternate,boolean premiumF,boolean allowBar){
        StopTriplet stops=starts;int fixed=mask;
        for(Reel reel:Reel.values())if((fixed&reel.bit())==0){
            String name=reel.name().toLowerCase();
            double phase=((Number)values.get("phase_"+name)).doubleValue();
            int pressed=Math.floorMod((int)Math.floor(phase),21);
            var choice=solver.choose(role,alternate,fixed,stops,reel,pressed,premiumF,allowBar,false);
            stops=stops.with(reel,choice.stopIndex());fixed|=reel.bit();
        }
        return stops;
    }

    private void settleUnstartedBonus(Map<String,Object> values,Stats stats,String type,boolean entryBetAlreadyPaid,long now) throws Exception {
        long gross=GameRules.bonusGross(type);
        long bonusBet=2L*GameRules.bonusGames(type);
        long net=gross-bonusBet-(entryBetAlreadyPaid?0:1);
        addAssets(values,net);stats.addDifference(net);
        addBonus(stats,values,type,now);
        stats.current=0;graph(values,stats,now);
    }

    private void settleRunningBonus(Map<String,Object> values,Stats stats,String type,boolean currentBetAlreadyPaid,long now) throws Exception {
        long gross=GameRules.bonusGross(type),paid=((Number)values.get("bonus_payout_count")).longValue();
        long remainingGross=gross-paid;
        if(remainingGross<0||remainingGross%14!=0)throw new IllegalStateException("Invalid bonus payout count");
        long games=remainingGross/14;
        long net=currentBetAlreadyPaid?(games==0?0:Math.subtractExact(Math.multiplyExact(games,14),Math.multiplyExact(games-1,2))):Math.multiplyExact(games,12);
        addAssets(values,net);stats.addDifference(net);stats.current=0;graph(values,stats,now);
    }

    private void addBonus(Stats stats,Map<String,Object> values,String type,long now) throws Exception {
        if(type.equals("BIG"))stats.big=Math.addExact(stats.big,1);else if(type.equals("REG"))stats.reg=Math.addExact(stats.reg,1);else throw new IllegalArgumentException("Bonus type");
        stats.lastBonus=type;stats.lastBonusAt=now;
        db.sql("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(?,?,?,?,?)",
                values.get("machine_id"),values.get("source_business_period_id"),type,stats.current,now);
    }

    private void graph(Map<String,Object> values,Stats stats,long now) throws Exception {
        db.sql("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(?,?,?,?,?)",
                values.get("machine_id"),values.get("source_business_period_id"),stats.total,stats.difference,now);
    }

    private static void addAssets(Map<String,Object> values,long amount){
        if(amount==0)return;
        var balance=new GameRules.Balance(((Number)values.get("credit")).intValue(),((Number)values.get("held_medals")).longValue()).payout(amount);
        values.put("credit",balance.credit());values.put("held_medals",balance.held());
    }

    private static StopTriplet currentStops(Map<String,Object> values){return new StopTriplet(((Number)values.get("display_left_stop")).intValue(),((Number)values.get("display_center_stop")).intValue(),((Number)values.get("display_right_stop")).intValue());}
    private static void putStops(Map<String,Object> values,StopTriplet stops){values.put("display_left_stop",stops.left());values.put("display_center_stop",stops.center());values.put("display_right_stop",stops.right());}

    private static void finish(Map<String,Object> values,long now){
        values.put("game_state","SEATED_READY");values.put("spin_id",null);values.put("internal_role",null);values.put("premium_type",null);
        values.put("notice_state","NONE");values.put("lamp_on",0);values.put("bonus_type",null);values.put("bonus_payout_count",0L);
        values.put("current_bet",0);values.put("pay_display",0);values.put("stopped_mask",0);values.put("motion_profile",null);values.put("last_activity",now);
        values.put("phase_left",((Number)values.get("display_left_stop")).doubleValue());values.put("phase_center",((Number)values.get("display_center_stop")).doubleValue());values.put("phase_right",((Number)values.get("display_right_stop")).doubleValue());
    }

    private Map<String,Object> row(String sql,Object...args) throws Exception {var rows=db.rows(sql,args);if(rows.isEmpty())throw new IllegalStateException("Missing recovery row");return rows.getFirst();}
    private SplittableRandom rng(int machine){return random.computeIfAbsent(machine,id->new SplittableRandom(mix(seed^id)));}
    private static long mix(long value){value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;return value^(value>>>31);}

    private static final class Stats {
        long total,big,reg,current,difference,max;String lastBonus;Long lastBonusAt;
        Stats(Map<String,Object> row){total=n(row,"total_games");big=n(row,"big_count");reg=n(row,"reg_count");current=n(row,"current_games");difference=n(row,"today_difference");max=n(row,"today_max_difference");lastBonus=(String)row.get("last_bonus_type");lastBonusAt=row.get("last_bonus_at")==null?null:((Number)row.get("last_bonus_at")).longValue();}
        void addDifference(long amount){difference=Math.addExact(difference,amount);max=Math.max(max,difference);}
        static long n(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    }
}
