package jp.pirijuggler.paper.database;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.reel.GodReelStrip;
import jp.pirijuggler.common.reel.GodStopControl;
import jp.pirijuggler.common.reel.Reel;
import jp.pirijuggler.paper.game.GameRules;
import jp.pirijuggler.paper.game.JugglerGodRuntime;
import jp.pirijuggler.paper.game.JugglerGodTransitions;
import jp.pirijuggler.paper.game.god.GodMachineRuntime;
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
    private static final int JG_GOD_DENOMINATOR=8192;
    private static final int JG_GOD_IN_GOD_BIG_STOCK=7;
    private final PiriDatabase db;
    private final RoleWeights weights;
    private final PremiumPolicy premiums;
    private final StopSolver solver;
    private final long seed;
    private final long normalBigToHeavenPpm,normalRegToHeavenPpm,heavenToHeavenPpm;
    private final int[] jgBonusScalePpm=new int[7];
    private final int[] jgSmallRoleScalePpm=new int[7];
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
        Map<String,Object> tuning=StartupProfile.map(config.get("juggler_god"));
        this.normalBigToHeavenPpm=number(tuning.get("normal_big_to_heaven_ppm"),0);
        this.normalRegToHeavenPpm=number(tuning.get("normal_reg_to_heaven_ppm"),0);
        this.heavenToHeavenPpm=number(tuning.get("heaven_to_heaven_ppm"),0);
        Map<String,Object> settings=StartupProfile.map(tuning.get("settings"));
        for(int setting=1;setting<=6;setting++){
            Map<String,Object> row=StartupProfile.map(settings.get(Integer.toString(setting)));
            jgBonusScalePpm[setting]=(int)number(row.get("bonus_scale_ppm"),1_000_000);
            jgSmallRoleScalePpm[setting]=(int)number(row.get("small_role_scale_ppm"),1_000_000);
        }
    }

    /**
     * Settle one unresolved session to SEATED_READY while preserving source_business_period_id.
     * The persisted last_client_sequence is the snapshot version for the §133 idempotency key.
     */
    public Session settle(Session before,long now) throws Exception {
        if(before.ready())return before;
        String settlementId=settlementTransactionId(before);
        var prior=db.rows("SELECT transaction_id FROM economy_transactions WHERE transaction_id=?",settlementId);
        if(!prior.isEmpty()){
            var current=db.rows("SELECT * FROM player_sessions WHERE session_id=?",before.id().toString());
            if(current.size()!=1)throw new IllegalStateException("Settled session missing: "+before.id());
            Session settled=new Session(current.getFirst());
            if(!settled.ready())throw new IllegalStateException("Settlement marker exists for unresolved session: "+before.id());
            return settled;
        }

        int machine=before.machine();
        String period=before.text("source_business_period_id");
        Map<String,Object> statRow=row("SELECT * FROM machine_period_stats WHERE machine_id=? AND business_period_id=?",machine,period);
        Stats stats=new Stats(statRow);
        Map<String,Object> values=new LinkedHashMap<>(before.snapshot());
        Map<String,Object> machineRow=row("SELECT setting,machine_type FROM machines WHERE machine_id=?",machine);
        int setting=((Number)machineRow.get("setting")).intValue();
        boolean god="GOD".equals(machineRow.get("machine_type"));
        boolean jugglerGod="JUGGLER_GOD".equals(machineRow.get("machine_type"));
        String settledGodRuntime=null;
        String settledJugglerGodRuntime=null;

        if(god){
            settledGodRuntime=settleGod(before,values,stats,now);
        } else if(jugglerGod){
            settledJugglerGodRuntime=settleJugglerGod(before,values,stats,setting,now);
        } else switch(before.state()) {
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
        db.sql("UPDATE player_sessions SET game_state=?,credit=?,held_medals=?,spin_id=?,internal_role=?,premium_type=?,notice_state=?,lamp_on=?,bonus_type=?,bonus_payout_count=?,current_bet=?,pay_display=?,display_left_stop=?,display_center_stop=?,display_right_stop=?,stopped_mask=?,phase_left=?,phase_center=?,phase_right=?,motion_profile=?,machine_state_json=?,last_activity=? WHERE session_id=?",
                values.get("game_state"),values.get("credit"),values.get("held_medals"),values.get("spin_id"),values.get("internal_role"),values.get("premium_type"),values.get("notice_state"),values.get("lamp_on"),values.get("bonus_type"),values.get("bonus_payout_count"),values.get("current_bet"),values.get("pay_display"),values.get("display_left_stop"),values.get("display_center_stop"),values.get("display_right_stop"),values.get("stopped_mask"),values.get("phase_left"),values.get("phase_center"),values.get("phase_right"),values.get("motion_profile"),values.get("machine_state_json"),values.get("last_activity"),before.id().toString());
        db.sql("UPDATE machine_period_stats SET total_games=?,big_count=?,reg_count=?,current_games=?,today_difference=?,today_max_difference=?,last_bonus_type=?,last_bonus_at=? WHERE machine_id=? AND business_period_id=?",
                stats.total,stats.big,stats.reg,stats.current,stats.difference,stats.max,stats.lastBonus,stats.lastBonusAt,machine,period);
        String settledMachineRuntime=settledGodRuntime!=null?settledGodRuntime:settledJugglerGodRuntime;
        if(settledMachineRuntime!=null)
            db.sql("UPDATE machines SET last_left_stop=?,last_center_stop=?,last_right_stop=?,machine_runtime_json=?,updated_at=? WHERE machine_id=?",
                    values.get("display_left_stop"),values.get("display_center_stop"),values.get("display_right_stop"),settledMachineRuntime,now,machine);
        else
            db.sql("UPDATE machines SET last_left_stop=?,last_center_stop=?,last_right_stop=?,updated_at=? WHERE machine_id=?",
                    values.get("display_left_stop"),values.get("display_center_stop"),values.get("display_right_stop"),now,machine);
        db.sql("INSERT INTO economy_transactions(transaction_id,player_uuid,operation,vault_amount,item_snapshot_json,balance_before,status,created_at,updated_at) VALUES(?,?,'FORCE_SETTLEMENT',0,?,NULL,'APPLIED',?,?)",
                settlementId,before.player().toString(),before.state().name(),now,now);
        return new Session(values);
    }

    static String settlementTransactionId(Session session){
        return "SETTLE:"+session.id()+":"+session.sequence();
    }

    private record JgDraw(InternalRole role,JugglerGodRuntime runtime,int normalSpins){}
    private record JgRound(int payoutCount,boolean ended){}

    private String settleJugglerGod(Session before,Map<String,Object> values,Stats stats,int setting,long now) throws Exception {
        JugglerGodRuntime runtime=loadJugglerGodRuntime(before);

        // BONUS-in-BONUS confirmation is a successor-specific suspended state. Never
        // reinterpret it as a fresh ordinary JUGGLER game during recovery.
        if(!"NONE".equals(runtime.pendingBonusHit()))
            return settlePendingJugglerGodOverlay(before,values,stats,runtime,setting,now);

        switch(before.state()){
            case NORMAL_BETTED,REPLAY_READY -> {
                JgDraw draw=drawJugglerGodRecovery(runtime,setting,before.machine());
                runtime=draw.runtime();
                stats.total=Math.addExact(stats.total,draw.normalSpins());
                stats.current=Math.addExact(stats.current,draw.normalSpins());
                putRecoveryRole(values,draw.role(),before.machine());
                runtime=settleJugglerGodNormalRole(values,stats,draw.role(),runtime,setting,before.machine(),now);
            }
            case NORMAL_SPINNING -> {
                String raw=(String)values.get("internal_role");
                if(raw==null)throw new IllegalStateException("JUGGLER_GOD NORMAL_SPINNING missing internal_role");
                runtime=settleJugglerGodNormalRole(values,stats,InternalRole.valueOf(raw),runtime,setting,before.machine(),now);
            }
            case BONUS_PENDING_BIG -> runtime=settleJugglerGodUnstartedBonus(values,stats,"BIG",false,runtime,setting,before.machine(),now);
            case BONUS_PENDING_REG -> runtime=settleJugglerGodUnstartedBonus(values,stats,"REG",false,runtime,setting,before.machine(),now);
            case BONUS_ENTRY_BETTED_BIG -> runtime=settleJugglerGodUnstartedBonus(values,stats,"BIG",true,runtime,setting,before.machine(),now);
            case BONUS_ENTRY_BETTED_REG -> runtime=settleJugglerGodUnstartedBonus(values,stats,"REG",true,runtime,setting,before.machine(),now);
            case BONUS_ENTRY_SPINNING_BIG -> {
                completeSpecialSpin(values,DisplayRole.BIG_ENTRY);
                runtime=settleJugglerGodUnstartedBonus(values,stats,"BIG",true,runtime,setting,before.machine(),now);
            }
            case BONUS_ENTRY_SPINNING_REG -> {
                completeSpecialSpin(values,DisplayRole.REG_ENTRY);
                runtime=settleJugglerGodUnstartedBonus(values,stats,"REG",true,runtime,setting,before.machine(),now);
            }
            case BIG_READY -> runtime=settleJugglerGodRunningBonus(values,stats,"BIG",false,false,runtime,setting,before.machine(),now);
            case REG_READY -> runtime=settleJugglerGodRunningBonus(values,stats,"REG",false,false,runtime,setting,before.machine(),now);
            case BIG_BETTED -> runtime=settleJugglerGodRunningBonus(values,stats,"BIG",true,false,runtime,setting,before.machine(),now);
            case REG_BETTED -> runtime=settleJugglerGodRunningBonus(values,stats,"REG",true,false,runtime,setting,before.machine(),now);
            case BIG_SPINNING -> {
                completeBonusSpin(values);
                runtime=settleJugglerGodRunningBonus(values,stats,"BIG",true,true,runtime,setting,before.machine(),now);
            }
            case REG_SPINNING -> {
                completeBonusSpin(values);
                runtime=settleJugglerGodRunningBonus(values,stats,"REG",true,true,runtime,setting,before.machine(),now);
            }
            case SEATED_READY -> {}
        }
        return runtime.clearPresentation("RECOVERY_SETTLED").toJsonString();
    }

    private JugglerGodRuntime loadJugglerGodRuntime(Session before) throws Exception {
        JsonObject sessionState=before.machineState();
        if(sessionState!=null&&sessionState.has("jgMode"))return JugglerGodRuntime.fromJson(sessionState.toString());
        Object raw=row("SELECT machine_runtime_json FROM machines WHERE machine_id=?",before.machine()).get("machine_runtime_json");
        return JugglerGodRuntime.fromJson(raw instanceof String s?s:null);
    }

    private JgDraw drawJugglerGodRecovery(JugglerGodRuntime runtime,int setting,int machine){
        SplittableRandom rng=rng(machine);
        if(!"NONE".equals(runtime.forcedRole())){
            InternalRole role;
            try{role=InternalRole.valueOf(runtime.forcedRole());}
            catch(IllegalArgumentException invalid){role=InternalRole.MISS;}
            return new JgDraw(role,runtime.forceRole("NONE"),1);
        }
        if(runtime.mode()==JugglerGodRuntime.Mode.GOD_CHAIN&&runtime.forceChainBig()){
            int spins=runtime.countNextChainGame()?1:0;
            JugglerGodRuntime consumed=runtime.core(runtime.mode(),runtime.heavenTarget(),runtime.heavenProgress(),
                    runtime.guaranteedRemaining(),false,false,runtime.bonusOrigin(),runtime.godBigCount(),
                    runtime.godFreeze(),"RECOVERY_GOD_CHAIN_BIG_CONSUMED");
            return new JgDraw(InternalRole.BIG,consumed,spins);
        }
        if(runtime.mode()!=JugglerGodRuntime.Mode.GOD_CHAIN&&rng.nextInt(JG_GOD_DENOMINATOR)==0)
            return new JgDraw(InternalRole.GOD,runtime,1);
        if(runtime.mode()==JugglerGodRuntime.Mode.HEAVEN){
            int progress=Math.min(32,runtime.heavenProgress()+1);
            JugglerGodRuntime progressed=runtime.core(runtime.mode(),runtime.heavenTarget(),progress,
                    runtime.guaranteedRemaining(),runtime.forceChainBig(),runtime.countNextChainGame(),
                    runtime.bonusOrigin(),runtime.godBigCount(),runtime.godFreeze(),runtime.lastEvent());
            InternalRole role=progress>=runtime.heavenTarget()
                    ?weights.drawBonusFamily(setting,rng)
                    :weights.drawJugglerGodNonBonus(setting,rng,jgBonusScalePpm[setting],jgSmallRoleScalePpm[setting]);
            return new JgDraw(role,progressed,1);
        }
        return new JgDraw(weights.drawJugglerGod(setting,rng,jgBonusScalePpm[setting],jgSmallRoleScalePpm[setting]),runtime,1);
    }

    private void putRecoveryRole(Map<String,Object> values,InternalRole role,int machine){
        PremiumPolicy.Type p=role==InternalRole.GOD?null:premiums.draw(role,rng(machine)).orElse(null);
        values.put("internal_role",role.name());
        values.put("premium_type",p==null?null:p.name());
    }

    private JugglerGodRuntime settleJugglerGodNormalRole(
            Map<String,Object> values,Stats stats,InternalRole role,JugglerGodRuntime runtime,
            int setting,int machine,long now
    ) throws Exception {
        StopTriplet finalStops=completeNormal(values,role,premium(values),((Number)values.get("stopped_mask")).intValue());
        putStops(values,finalStops);
        PremiumPolicy.Type p=premium(values);
        var evaluation=solver.catalogue().evaluation(finalStops);
        DisplayRole directRole=(p==PremiumPolicy.Type.B||p==PremiumPolicy.Type.F)?null:role.directEntryDisplay();
        boolean direct=directRole!=null&&evaluation.valid(directRole);
        int payout=direct?0:GameRules.payout(role);
        addAssets(values,payout);stats.addDifference(payout);
        graph(values,stats,now);

        if(role==InternalRole.GOD)
            return settleRecoveredTopLevelGod(values,stats,runtime,setting,machine,now);

        String bonus=GameRules.bonus(role);
        if(bonus==null){
            // Force settlement cannot leave a free replay attached to a released machine.
            // Preserve its exact three-medal value rather than silently dropping it.
            if(role==InternalRole.REPLAY){addAssets(values,3);stats.addDifference(3);}
            return runtime;
        }

        String origin=runtime.mode()==JugglerGodRuntime.Mode.HEAVEN?"HEAVEN":
                runtime.mode()==JugglerGodRuntime.Mode.GOD_CHAIN?"GOD_CHAIN":bonus;
        JugglerGodRuntime started=runtime.core(runtime.mode(),runtime.heavenTarget(),runtime.heavenProgress(),
                runtime.guaranteedRemaining(),false,false,origin,runtime.godBigCount(),false,"RECOVERY_BONUS_DRAWN");
        if("GOD_CHAIN".equals(origin))
            started=started.core(started.mode(),started.heavenTarget(),started.heavenProgress(),started.guaranteedRemaining(),
                    false,false,started.bonusOrigin(),started.godBigCount()+1,false,"RECOVERY_GOD_BIG_STARTED");
        return settleJugglerGodUnstartedBonus(values,stats,bonus,direct,started,setting,machine,now);
    }

    private JugglerGodRuntime settleRecoveredTopLevelGod(
            Map<String,Object> values,Stats stats,JugglerGodRuntime runtime,int setting,int machine,long now
    ) throws Exception {
        db.sql("INSERT INTO juggler_god_history(machine_id,business_period_id,event_type,games,occurred_at) VALUES(?,?,'GOD',?,?)",
                values.get("machine_id"),values.get("source_business_period_id"),stats.current,now);

        JugglerGodRuntime god=new JugglerGodRuntime(
                JugglerGodRuntime.Mode.GOD_CHAIN,0,0,4,false,false,
                "GOD_CHAIN",1,false,"RECOVERY_GOD_STARTED",
                runtime.additionalBigStock(),runtime.additionalRegStock(),"NONE","NONE",0,false,false,
                runtime.forcedRole(),0);

        // First GOD BIG starts directly: no one-medal entry cost.
        god=simulateJugglerGodBonusOverlays(god,"BIG",GameRules.bonusGames("BIG"),setting,machine,values,stats,now);
        long net=(long)GameRules.bonusGross("BIG")-2L*GameRules.bonusGames("BIG");
        addAssets(values,net);stats.addDifference(net);
        stats.big=Math.addExact(stats.big,1);stats.lastBonus="BIG";stats.lastBonusAt=now;
        db.sql("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(?,?,'BIG',0,?)",
                values.get("machine_id"),values.get("source_business_period_id"),now);
        stats.current=0;graph(values,stats,now);
        return postJugglerGodBonus(god,setting,machine);
    }

    private JugglerGodRuntime simulateJugglerGodBonusOverlays(
            JugglerGodRuntime initial,String type,long rounds,int setting,int machine,
            Map<String,Object> values,Stats stats,long now
    ) throws Exception {
        JugglerGodRuntime runtime=initial;
        SplittableRandom rng=rng(machine);
        for(long i=0;i<rounds;i++){
            if(rng.nextInt(JG_GOD_DENOMINATOR)==0){
                addAssets(values,15);stats.addDifference(15);
                runtime=runtime.stock(Math.addExact(runtime.additionalBigStock(),JG_GOD_IN_GOD_BIG_STOCK),
                        runtime.additionalRegStock(),"RECOVERY_GOD_IN_GOD");
                continue;
            }
            InternalRole hit=weights.drawJugglerGod(setting,rng,jgBonusScalePpm[setting],jgSmallRoleScalePpm[setting]);
            String bonus=GameRules.bonus(hit);
            if(bonus==null)continue;
            runtime=runtime.stock(
                    runtime.additionalBigStock()+("BIG".equals(bonus)?1:0),
                    runtime.additionalRegStock()+("REG".equals(bonus)?1:0),
                    "RECOVERY_BONUS_STOCK");
        }
        return runtime;
    }

    private JugglerGodRuntime settleJugglerGodUnstartedBonus(
            Map<String,Object> values,Stats stats,String type,boolean entryBetAlreadyPaid,JugglerGodRuntime runtime,
            int setting,int machine,long now
    ) throws Exception {
        runtime=simulateJugglerGodBonusOverlays(runtime,type,GameRules.bonusGames(type),setting,machine,values,stats,now);
        settleUnstartedBonus(values,stats,type,entryBetAlreadyPaid,now);
        return postJugglerGodBonus(runtime,setting,machine);
    }

    private JugglerGodRuntime settleJugglerGodRunningBonus(
            Map<String,Object> values,Stats stats,String type,boolean currentBetAlreadyPaid,boolean currentSpinOverlayAlreadyDrawn,
            JugglerGodRuntime runtime,int setting,int machine,long now
    ) throws Exception {
        long gross=GameRules.bonusGross(type);
        long paid=((Number)values.get("bonus_payout_count")).longValue();
        long remainingGross=gross-paid;
        if(remainingGross<0||remainingGross%14!=0)throw new IllegalStateException("Invalid JUGGLER_GOD bonus payout count");
        long rounds=remainingGross/14;
        long overlayRounds=Math.max(0,rounds-(currentSpinOverlayAlreadyDrawn?1:0));
        runtime=simulateJugglerGodBonusOverlays(runtime,type,overlayRounds,setting,machine,values,stats,now);
        settleRunningBonus(values,stats,type,currentBetAlreadyPaid,now);
        return postJugglerGodBonus(runtime,setting,machine);
    }

    private JugglerGodRuntime postJugglerGodBonus(JugglerGodRuntime runtime,int setting,int machine){
        if(runtime.stockLampOn())return runtime;
        if(runtime.releasingStock())runtime=runtime.stopReleasing("RECOVERY_STOCK_RELEASES_DONE");
        return JugglerGodTransitions.afterBonus(runtime,setting,rng(machine),
                normalBigToHeavenPpm,normalRegToHeavenPpm,heavenToHeavenPpm);
    }

    private String settlePendingJugglerGodOverlay(
            Session before,Map<String,Object> values,Stats stats,JugglerGodRuntime runtime,int setting,long now
    ) throws Exception {
        String hit=runtime.pendingBonusHit();
        int machine=before.machine();

        // If disconnect happened while the bonus round itself was still spinning, finish
        // exactly that already-paid round first. The overlay draw has already happened.
        if(before.state()==Session.GameState.BIG_SPINNING||before.state()==Session.GameState.REG_SPINNING){
            String type=before.state()==Session.GameState.BIG_SPINNING?"BIG":"REG";
            JgRound round=settleOneJugglerGodBonusRound(values,stats,type,now);
            runtime=runtime.interrupt(hit,type,round.payoutCount(),round.ended(),
                    "GOD".equals(hit)?"RECOVERY_GOD_CONFIRM_READY":"RECOVERY_STOCK_CONFIRM_READY");
        }

        int big=runtime.additionalBigStock();
        int reg=runtime.additionalRegStock();
        if("GOD".equals(hit)){
            addAssets(values,15);stats.addDifference(15);graph(values,stats,now);
            big=Math.addExact(big,JG_GOD_IN_GOD_BIG_STOCK);
        }else if("BIG".equals(hit)){
            big=Math.addExact(big,1);
        }else if("REG".equals(hit)){
            reg=Math.addExact(reg,1);
        }else{
            throw new IllegalStateException("Unknown JUGGLER_GOD pending overlay "+hit);
        }

        String suspendedType=runtime.suspendedBonusType();
        int suspendedCount=runtime.suspendedBonusPayoutCount();
        boolean suspendedEnded=runtime.suspendedBonusEnded();
        JugglerGodRuntime confirmed=new JugglerGodRuntime(
                runtime.mode(),runtime.heavenTarget(),runtime.heavenProgress(),runtime.guaranteedRemaining(),
                runtime.forceChainBig(),runtime.countNextChainGame(),runtime.bonusOrigin(),runtime.godBigCount(),
                false,"RECOVERY_OVERLAY_CONFIRMED",big,reg,"NONE","NONE",0,false,runtime.releasingStock(),
                runtime.forcedRole(),0);

        if(suspendedEnded)return confirmed.toJsonString();
        if(!Set.of("BIG","REG").contains(suspendedType))
            throw new IllegalStateException("JUGGLER_GOD overlay missing suspended bonus");

        values.put("bonus_payout_count",suspendedCount);
        values.put("bonus_type",suspendedType);
        JugglerGodRuntime completed=settleJugglerGodRunningBonus(
                values,stats,suspendedType,false,false,confirmed,setting,machine,now);
        return completed.toJsonString();
    }

    private JgRound settleOneJugglerGodBonusRound(Map<String,Object> values,Stats stats,String type,long now) throws Exception {
        completeBonusSpin(values);
        addAssets(values,14);stats.addDifference(14);
        int count=Math.addExact(((Number)values.get("bonus_payout_count")).intValue(),14);
        boolean ended="BIG".equals(type)?count>266:count>98;
        values.put("bonus_payout_count",ended?0:count);
        values.put("current_bet",0);
        if(ended){stats.current=0;graph(values,stats,now);}
        return new JgRound(count,ended);
    }

    /**
     * GOD recovery never redraws a role. Lever-on already persisted the exact
     * pending runtime/result in machine_state_json, so recovery completes that
     * authoritative result and then returns the session to a safe ready state.
     */
    private String settleGod(Session before,Map<String,Object> values,Stats stats,long now) throws Exception {
        if(before.state()==Session.GameState.REPLAY_READY){
            // Preserve the value of the free replay when force-settling to SEATED_READY.
            addAssets(values,3);
            stats.addDifference(3);
            graph(values,stats,now);
            JsonObject state=before.machineState();
            finish(values,now);
            if(state==null)return null;
            Object raw=row("SELECT machine_runtime_json FROM machines WHERE machine_id=?",before.machine()).get("machine_runtime_json");
            return raw instanceof String text?text:null;
        }
        if(before.state()!=Session.GameState.NORMAL_SPINNING)
            throw new IllegalStateException("Unexpected GOD recovery state: "+before.state());

        JsonObject state=before.machineState();
        if(state==null||!state.has("_pendingRuntime")||!state.has("_pendingPayout")||!state.has("_pendingRole"))
            throw new IllegalStateException("GOD spin missing pending recovery state");

        String role=state.get("_pendingRole").getAsString();
        String displayRole=state.has("_pendingDisplayRole")?state.get("_pendingDisplayRole").getAsString():role;
        int mask=((Number)values.get("stopped_mask")).intValue();
        for(int reel=0;reel<3;reel++){
            int bit=1<<reel;
            if((mask&bit)!=0)continue;
            String name=new String[]{"left","center","right"}[reel];
            int pressed=Math.floorMod((int)Math.floor(((Number)values.get("phase_"+name)).doubleValue()),GodReelStrip.STOPS);
            long presentationSeed=state.has("_presentationControlSeed")
                    ?state.get("_presentationControlSeed").getAsLong()
                    :state.has("_missControlSeed")?state.get("_missControlSeed").getAsLong():0L;
            int target=GodStopControl.targetFor(
                    displayRole,reel,pressed,mask,
                    ((Number)values.get("display_left_stop")).intValue(),
                    ((Number)values.get("display_center_stop")).intValue(),
                    ((Number)values.get("display_right_stop")).intValue(),
                    presentationSeed
            );
            values.put("display_"+name+"_stop",target);
            mask|=bit;
        }

        int payout=state.get("_pendingPayout").getAsInt();
        boolean replay=state.has("_pendingReplay")&&state.get("_pendingReplay").getAsBoolean();
        addAssets(values,payout);
        stats.addDifference(payout);
        stats.total=Math.addExact(stats.total,1);
        stats.current=Math.addExact(stats.current,1);
        if(replay){
            // Convert the pending free game into equivalent medals only for forced recovery.
            addAssets(values,3);
            stats.addDifference(3);
        }
        graph(values,stats,now);

        GodMachineRuntime runtime=GodMachineRuntime.fromJson(state.getAsJsonObject("_pendingRuntime").toString());
        values.put("machine_state_json",runtime.gameplay().toJsonString());
        finish(values,now);
        return runtime.toJsonString();
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
    private static long number(Object value,long fallback){return value instanceof Number n?n.longValue():fallback;}
    private static long mix(long value){value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;return value^(value>>>31);}

    private static final class Stats {
        long total,big,reg,current,difference,max;String lastBonus;Long lastBonusAt;
        Stats(Map<String,Object> row){total=n(row,"total_games");big=n(row,"big_count");reg=n(row,"reg_count");current=n(row,"current_games");difference=n(row,"today_difference");max=n(row,"today_max_difference");lastBonus=(String)row.get("last_bonus_type");lastBonusAt=row.get("last_bonus_at")==null?null:((Number)row.get("last_bonus_at")).longValue();}
        void addDifference(long amount){difference=Math.addExact(difference,amount);max=Math.max(max,difference);}
        static long n(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    }
}
