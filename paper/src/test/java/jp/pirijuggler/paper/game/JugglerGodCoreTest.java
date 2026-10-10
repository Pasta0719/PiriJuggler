package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import jp.pirijuggler.paper.reel.InternalRole;
import jp.pirijuggler.paper.session.Session;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class JugglerGodCoreTest extends GameFixture {
    private record Rig(Session session,Machine machine,JugglerGodGameEngine engine) {}

    private Rig rig(JugglerGodRuntime runtime,int setting) throws Exception {
        var location=new Machine.Location(UUID.randomUUID(),"world",0,64,0,"NORTH");
        int id=db.create(location,MachineType.JUGGLER_GOD,NOW);
        db.sql("UPDATE machines SET setting=?,machine_runtime_json=? WHERE machine_id=?",setting,runtime.toJsonString(),id);
        UUID player=UUID.randomUUID();
        Session s=db.seat(player,id,NOW);
        db.sql("UPDATE player_sessions SET credit=50,held_medals=500 WHERE player_uuid=?",player.toString());
        s=db.state().session(player);
        var weights=new RoleWeights(config);
        var random=new RandomStreams(123456789L);
        var normal=new NormalGame(weights,random,SOLVER,main,config,false);
        var engine=new JugglerGodGameEngine(normal,random,weights,config);
        return new Rig(s,db.state().machine(id),engine);
    }

    private Rig rig(JugglerGodRuntime runtime,int setting,MachineType type) throws Exception {
        if(type==MachineType.JUGGLER_GOD)return rig(runtime,setting);
        var location=new Machine.Location(UUID.randomUUID(),"world",0,64,0,"NORTH");
        int id=db.create(location,type,NOW);
        db.sql("UPDATE machines SET setting=?,machine_runtime_json=? WHERE machine_id=?",setting,runtime.toJsonString(),id);
        UUID player=UUID.randomUUID();
        Session s=db.seat(player,id,NOW);
        db.sql("UPDATE player_sessions SET credit=50,held_medals=500 WHERE player_uuid=?",player.toString());
        s=db.state().session(player);
        var weights=new RoleWeights(config);
        var random=new RandomStreams(123456789L);
        var tune=jp.pirijuggler.paper.database.StartupProfile.map(config.get("juggler_god_extreme"));
        var normal=new NormalGame(weights,random,SOLVER,main,config,false,
                ((Number)tune.get("big_payout")).intValue(),((Number)tune.get("reg_payout")).intValue());
        var engine=new JugglerGodGameEngine(normal,random,weights,config,"juggler_god_extreme");
        return new Rig(s,db.state().machine(id),engine);
    }

    private GameTransition plan(Rig rig,Session s,PacketType type,long nano) throws Exception {
        return rig.engine().plan(s,db.state().machine(rig.machine().id()),type,s.sequence()+1,NOW+s.sequence()+1,nano,0,null);
    }

    private Session action(Rig rig,Session s,PacketType type,long nano) throws Exception {
        GameTransition t=plan(rig,s,type,nano);
        Session after=store.commit(t);
        rig.engine().committed(t,nano);
        return after;
    }

    @Test void guaranteedGodStockForcesBigWithoutAdvancingGameCounter() throws Exception {
        var runtime=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,3,true,false,"NONE",2,false,"GOD_GUARANTEED_NEXT");
        Rig rig=rig(runtime,1);
        Session s=action(rig,rig.session(),PacketType.SPACE_ACTION,0);
        GameTransition lever=plan(rig,s,PacketType.SPACE_ACTION,1_000_000_000L);
        assertEquals("BIG",lever.after().text("internal_role"));
        assertEquals(0,lever.normalSpins());
        s=store.commit(lever);
        assertEquals(0,scalar("SELECT total_games FROM machine_period_stats WHERE machine_id=?",s.machine()));
        JugglerGodRuntime persisted=JugglerGodRuntime.fromJson(db.state().machine(s.machine()).runtimeJson());
        assertEquals(JugglerGodRuntime.Mode.GOD_CHAIN,persisted.mode());
        assertEquals("GOD_CHAIN",persisted.bonusOrigin());
        assertFalse(persisted.forceChainBig(),"guaranteed BIG reservation must be consumed at lever-on");
        assertFalse(persisted.countNextChainGame());
    }

    @Test void postGuaranteeGodContinuationCountsAsOneGame() throws Exception {
        var runtime=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,0,true,true,"NONE",5,false,"GOD_CONTINUE");
        Rig rig=rig(runtime,6);
        Session s=action(rig,rig.session(),PacketType.SPACE_ACTION,0);
        GameTransition lever=plan(rig,s,PacketType.SPACE_ACTION,1_000_000_000L);
        assertEquals("BIG",lever.after().text("internal_role"));
        assertEquals(1,lever.normalSpins());
        s=store.commit(lever);
        assertEquals(1,scalar("SELECT total_games FROM machine_period_stats WHERE machine_id=?",s.machine()));
        JugglerGodRuntime persisted=JugglerGodRuntime.fromJson(db.state().machine(s.machine()).runtimeJson());
        assertFalse(persisted.forceChainBig(),"1G continuation reservation must be one-shot");
        assertFalse(persisted.countNextChainGame(),"1G marker must be consumed together with the reservation");
        assertEquals("GOD_CHAIN",persisted.bonusOrigin());
    }

    @Test void heavenBeforeTargetCannotNaturallyStartBonus() throws Exception {
        var runtime=new JugglerGodRuntime(JugglerGodRuntime.Mode.HEAVEN,2,0,0,false,false,"NONE",0,false,"GOD_END_HEAVEN");
        Rig rig=rig(runtime,3);
        Session s=action(rig,rig.session(),PacketType.SPACE_ACTION,0);
        GameTransition lever=plan(rig,s,PacketType.SPACE_ACTION,1_000_000_000L);
        InternalRole role=InternalRole.valueOf(lever.after().text("internal_role"));
        assertNull(GameRules.bonus(role));
        JugglerGodRuntime state=JugglerGodRuntime.fromJson(lever.machineRuntimeJson());
        assertEquals(1,state.heavenProgress());
        assertEquals(JugglerGodRuntime.Mode.HEAVEN,state.mode());
        store.commit(lever);
        JugglerGodRuntime persisted=JugglerGodRuntime.fromJson(db.state().machine(rig.machine().id()).runtimeJson());
        assertEquals(2,persisted.heavenTarget());
        assertEquals(1,persisted.heavenProgress());
        assertEquals(JugglerGodRuntime.Mode.HEAVEN,persisted.mode());
    }

    @Test void heavenTargetGameForcesSettingBasedBigOrRegFamily() throws Exception {
        var runtime=new JugglerGodRuntime(JugglerGodRuntime.Mode.HEAVEN,1,0,0,false,false,"NONE",0,false,"GOD_END_HEAVEN");
        Rig rig=rig(runtime,3);
        Session s=action(rig,rig.session(),PacketType.SPACE_ACTION,0);
        GameTransition lever=plan(rig,s,PacketType.SPACE_ACTION,1_000_000_000L);
        InternalRole role=InternalRole.valueOf(lever.after().text("internal_role"));
        assertTrue(GameRules.bonus(role)!=null);
        assertTrue(role==InternalRole.BIG||role==InternalRole.REG);
        JugglerGodRuntime state=JugglerGodRuntime.fromJson(lever.machineRuntimeJson());
        assertEquals(1,state.heavenProgress());
        assertEquals("HEAVEN",state.bonusOrigin());
    }

    @Test void godBarIsExactlyCenterLineAndPaysFifteen() {
        var candidates=SOLVER.catalogue().candidates(jp.pirijuggler.paper.reel.DisplayRole.GOD_BAR);
        assertFalse(candidates.isEmpty());
        for(var candidate:candidates)
            assertEquals(1<<jp.pirijuggler.paper.reel.Payline.L1_CENTER.ordinal(),candidate.winningBarConfirmationLines());
        assertEquals(15,GameRules.payout(InternalRole.GOD));
        assertEquals("BIG",GameRules.bonus(InternalRole.GOD));
    }

    @Test void firstGodBigIsStoredAsZeroGameBigHistory() throws Exception {
        Rig rig=rig(JugglerGodRuntime.initial(),1);
        Session before=rig.session();
        var values=new LinkedHashMap<>(before.snapshot());
        values.put("last_client_sequence",before.sequence()+1);
        values.put("game_state","BIG_READY");
        values.put("bonus_type","BIG");
        values.put("last_activity",before.number("last_activity")+1);
        values.put("machine_state_json",new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,4,false,false,
                "GOD_CHAIN",1,false,"GOD_STARTED").toJsonString());
        Session after=new Session(values);
        var tx=new GameTransition(UUID.randomUUID(),before,after,0,15,1,true,false,"BIG",false,0,
                List.of(),List.of(),List.of(),after.text("machine_state_json"));
        store.commit(tx);
        assertEquals(1,scalar("SELECT count(*) FROM juggler_god_history WHERE machine_id=?",before.machine()));
        assertEquals(1,scalar("SELECT count(*) FROM bonus_history WHERE machine_id=? AND bonus_type='BIG' AND games=0",before.machine()));
    }

    @Test void firstGodBigEntryKeepsCounterOneForDedicatedAudioLifecycle() {
        var before=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,4,false,false,
                "GOD_CHAIN",1,false,"GOD_STARTED");
        var after=JugglerGodGameEngine.recordGodChainBonusStart(before);
        assertEquals(1,after.godBigCount());
        assertEquals("GOD_BIG_STARTED",after.lastEvent());
        assertEquals(4,after.guaranteedRemaining());
    }

    @Test void firstGodBigEndIsAuthoritativelyIdentifiedFromTheFinalBonusSpin() throws Exception {
        Rig rig=rig(new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,4,false,false,
                "GOD_CHAIN",1,false,"GOD_BIG_STARTED"),1);
        var values=new LinkedHashMap<>(rig.session().snapshot());
        values.put("game_state","BIG_SPINNING");
        values.put("bonus_type","BIG");
        values.put("machine_state_json",new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,4,false,false,
                "GOD_CHAIN",1,false,"GOD_BIG_STARTED").toJsonString());
        Session first=new Session(values);
        assertTrue(JugglerGodGameEngine.firstGodBigEnd(first,true));
        values.put("machine_state_json",new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,3,false,false,
                "GOD_CHAIN",2,false,"GOD_BIG_STARTED").toJsonString());
        assertFalse(JugglerGodGameEngine.firstGodBigEnd(new Session(values),true));
        assertFalse(JugglerGodGameEngine.firstGodBigEnd(first,false));
    }

    @Test void directEntryGodChainBigIncrementsCounter() {
        var before=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,2,false,false,
                "GOD_CHAIN",2,false,"BONUS_DRAWN");
        var after=JugglerGodGameEngine.recordGodChainBonusStart(before);
        assertEquals(3,after.godBigCount());
        assertEquals("GOD_BIG_STARTED",after.lastEvent());
        assertEquals(2,after.guaranteedRemaining());
    }

    @Test void godChainFailureAlwaysEntersFreshHeaven() throws Exception {
        var runtime=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,0,false,false,
                "GOD_CHAIN",5,false,"GOD_BIG_STARTED");
        Rig rig=rig(runtime,1);
        var method=JugglerGodGameEngine.class.getDeclaredMethod("afterBonus",Machine.class,JugglerGodRuntime.class);
        method.setAccessible(true);
        boolean sawHeaven=false;
        for(int i=0;i<64;i++){
            var after=(JugglerGodRuntime)method.invoke(rig.engine(),rig.machine(),runtime);
            if(after.mode()==JugglerGodRuntime.Mode.HEAVEN){
                assertTrue(after.heavenTarget()>=1&&after.heavenTarget()<=32);
                assertEquals(0,after.heavenProgress());
                sawHeaven=true;break;
            }
        }
        assertTrue(sawHeaven);
    }


    @Test void stockLampIsBooleanOnlyAndRuntimeRoundTripKeepsHiddenCounts() {
        var state=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,2,false,false,
                "GOD_CHAIN",3,false,"BONUS_STOCK_CONFIRMED",
                7,2,"NONE","NONE",0,false,false);
        assertTrue(state.stockLampOn());
        var roundTrip=JugglerGodRuntime.fromJson(state.toJsonString());
        assertEquals(7,roundTrip.additionalBigStock());
        assertEquals(2,roundTrip.additionalRegStock());
        assertTrue(roundTrip.stockLampOn());
        assertFalse(JugglerGodRuntime.initial().stockLampOn());
    }

    @Test void interruptPreservesExistingStockAndReleaseContext() {
        var state=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,0,false,false,
                "GOD_CHAIN",5,false,"STOCK_RELEASE_BIG",
                3,1,"NONE","NONE",0,false,true);
        var interrupted=state.interrupt("REG","BIG",126,false,"BONUS_STOCK_DRAWN");
        assertEquals(3,interrupted.additionalBigStock());
        assertEquals(1,interrupted.additionalRegStock());
        assertEquals("REG",interrupted.pendingBonusHit());
        assertEquals("BIG",interrupted.suspendedBonusType());
        assertEquals(126,interrupted.suspendedBonusPayoutCount());
        assertTrue(interrupted.releasingStock());
    }

    @Test void stockReleaseRegAcquisitionIsStillConfirmedAndCannotLeavePendingRegLatched() {
        var releasing=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,0,false,false,
                "GOD_CHAIN",5,false,"BONUS_STOCK_CONFIRM_READY",
                0,0,"REG","REG",14,false,true);
        assertTrue(JugglerGodGameEngine.isAcquisitionEntryFinish(
                true,Session.GameState.BONUS_ENTRY_SPINNING_REG,releasing));
        var confirmed=JugglerGodGameEngine.confirmAcquiredStock(releasing);
        assertEquals(1,confirmed.additionalRegStock());
        assertEquals(0,confirmed.additionalBigStock());
        assertEquals("NONE",confirmed.pendingBonusHit());
        assertEquals("NONE",confirmed.suspendedBonusType());
        assertTrue(confirmed.releasingStock());
    }

    @Test void stockReleaseBigAcquisitionAlsoConsumesPendingHitExactlyOnce() {
        var releasing=new JugglerGodRuntime(JugglerGodRuntime.Mode.HEAVEN,7,7,0,false,false,
                "HEAVEN",0,false,"BONUS_STOCK_CONFIRM_READY",
                2,1,"BIG","REG",42,false,true);
        var confirmed=JugglerGodGameEngine.confirmAcquiredStock(releasing);
        assertEquals(3,confirmed.additionalBigStock());
        assertEquals(1,confirmed.additionalRegStock());
        assertEquals("NONE",confirmed.pendingBonusHit());
        assertTrue(confirmed.releasingStock());
    }

    @Test void consumingPendingGodCanPreserveInterruptedBonusUntilReplacementDecision() {
        var state=JugglerGodRuntime.initial().interrupt("GOD","BIG",210,false,"BONUS_GOD_CONFIRM_READY");
        var consumed=state.consumePendingHit("BONUS_GOD_CONFIRMED");
        assertEquals("NONE",consumed.pendingBonusHit());
        assertEquals("BIG",consumed.suspendedBonusType());
        assertEquals(210,consumed.suspendedBonusPayoutCount());
    }

    @Test void ordinaryBonusGodReplacementStateUsesFullGodChainAndOneBigCompensation() {
        var interrupted=JugglerGodRuntime.initial().interrupt("GOD","REG",84,false,"BONUS_GOD_CONFIRM_READY");
        var replacement=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,4,false,false,
                "GOD_CHAIN",1,false,"GOD_STARTED",
                interrupted.additionalBigStock()+1,interrupted.additionalRegStock(),
                "NONE","NONE",0,false,interrupted.releasingStock(),
                interrupted.forcedRole(),interrupted.godPresentationStartMs());
        assertEquals(JugglerGodRuntime.Mode.GOD_CHAIN,replacement.mode());
        assertEquals(4,replacement.guaranteedRemaining());
        assertEquals(1,replacement.godBigCount());
        assertEquals(1,replacement.additionalBigStock());
        assertEquals(0,replacement.additionalRegStock());
        assertEquals("NONE",replacement.pendingBonusHit());
        assertEquals("NONE",replacement.suspendedBonusType());
    }

    @Test void finishingGodChainStockReleaseReturnsToGuaranteedChainBeforeHeaven() {
        var releasing=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,3,false,false,
                "GOD_CHAIN",2,false,"STOCK_RELEASE_REG",
                0,0,"NONE","NONE",0,false,true);
        var stopped=releasing.stopReleasing("STOCK_RELEASES_DONE");
        var rng=new java.util.Random(0L);
        var after=JugglerGodTransitions.afterBonus(stopped,1,rng,125000,62500,500000);
        assertEquals(JugglerGodRuntime.Mode.GOD_CHAIN,after.mode());
        assertEquals(2,after.guaranteedRemaining());
        assertTrue(after.forceChainBig());
        assertFalse(after.countNextChainGame());
    }

    @Test void finishingFinalGodChainStockReleaseCanOnlyContinueOrEnterHeaven() {
        var releasing=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,0,false,false,
                "GOD_CHAIN",9,false,"STOCK_RELEASE_BIG",
                0,0,"NONE","NONE",0,false,true);
        var failRng=new java.util.Random(0L){@Override public int nextInt(int bound){return bound-1;}};
        var after=JugglerGodTransitions.afterBonus(releasing.stopReleasing("DONE"),1,failRng,125000,62500,500000);
        assertEquals(JugglerGodRuntime.Mode.HEAVEN,after.mode());
        assertTrue(after.heavenTarget()>=1&&after.heavenTarget()<=32);
        assertFalse(after.forceChainBig());
    }

    @Test void heavenStockReleasePreservesHeavenOriginUntilFinalStockFinishes() {
        var state=new JugglerGodRuntime(JugglerGodRuntime.Mode.HEAVEN,12,12,0,false,false,
                "HEAVEN",0,false,"BONUS_STOCK_CONFIRMED",
                1,1,"NONE","NONE",0,false,true);
        assertEquals(JugglerGodRuntime.Mode.HEAVEN,state.mode());
        assertEquals("HEAVEN",state.bonusOrigin());
        assertTrue(state.stockLampOn());
        var roundTrip=JugglerGodRuntime.fromJson(state.toJsonString());
        assertEquals(state,roundTrip);
    }

    @Test void runtimeRoundTripPreservesHiddenSuccessorState() {
        var state=new JugglerGodRuntime(JugglerGodRuntime.Mode.HEAVEN,32,17,4,true,true,"GOD_CHAIN",9,true,"TEST");
        assertEquals(state,JugglerGodRuntime.fromJson(state.toJsonString()));
    }
    @Test void godPresentationRejectsInputForFifteenSecondsThenNormalizesReelsToCenterSevens() throws Exception {
        var runtime=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,4,false,false,
                "GOD_CHAIN",1,false,"GOD_STARTED").startPresentation(NOW,"GOD_STARTED");
        Rig rig=rig(runtime,1);
        var values=new LinkedHashMap<>(rig.session().snapshot());
        values.put("game_state","BIG_READY");
        values.put("bonus_type","BIG");
        values.put("lamp_on",1);
        values.put("display_left_stop",9);
        values.put("display_center_stop",11);
        values.put("display_right_stop",4);
        values.put("machine_state_json",runtime.toJsonString());
        Session before=new Session(values);

        GameTransition locked=rig.engine().plan(before,rig.machine(),PacketType.SPACE_ACTION,
                before.sequence()+1,NOW+14_999,0,0,null);
        assertEquals(Session.GameState.BIG_READY,locked.after().state());
        assertEquals(PacketType.ACTION_REJECTED,locked.packets().getFirst().packetType());
        assertEquals(NOW,JugglerGodRuntime.fromJson(locked.machineRuntimeJson()).godPresentationStartMs());

        GameTransition unlocked=rig.engine().plan(before,rig.machine(),PacketType.SPACE_ACTION,
                before.sequence()+1,NOW+15_000,0,0,null);
        assertEquals(Session.GameState.BIG_BETTED,unlocked.after().state());
        assertEquals(3,unlocked.after().number("display_left_stop"));
        assertEquals(3,unlocked.after().number("display_center_stop"));
        assertEquals(3,unlocked.after().number("display_right_stop"));
        assertEquals(0,JugglerGodRuntime.fromJson(unlocked.machineRuntimeJson()).godPresentationStartMs());
    }

    @Test void consumedContinuationCannotSelfRearmBeforeBonusEnd() throws Exception {
        var runtime=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,0,true,true,
                "NONE",20,false,"GOD_CONTINUE");
        Rig rig=rig(runtime,6);
        Session s=action(rig,rig.session(),PacketType.SPACE_ACTION,0);
        GameTransition lever=plan(rig,s,PacketType.SPACE_ACTION,1_000_000_000L);
        assertEquals("BIG",lever.after().text("internal_role"));
        JugglerGodRuntime afterLever=JugglerGodRuntime.fromJson(lever.machineRuntimeJson());
        assertFalse(afterLever.forceChainBig());
        assertFalse(afterLever.countNextChainGame());
        assertEquals("GOD_CHAIN",afterLever.bonusOrigin());
        assertEquals("BONUS_DRAWN",afterLever.lastEvent());
    }

    @Test void godContinuationSuccessArmsExactlyOneCountedBig() {
        var before=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,0,false,false,
                "GOD_CHAIN",9,false,"GOD_BIG_STARTED");
        var rng=new java.util.Random(0L){
            @Override public int nextInt(int bound){return 0;}
        };
        var after=JugglerGodTransitions.afterBonus(before,6,rng,125000,62500,500000);
        assertEquals(JugglerGodRuntime.Mode.GOD_CHAIN,after.mode());
        assertTrue(after.forceChainBig());
        assertTrue(after.countNextChainGame());
        assertEquals(0,after.guaranteedRemaining());
        assertEquals("GOD_CONTINUE",after.lastEvent());
    }

    @Test void godContinuationFailureAlwaysLeavesChainForFreshHeaven() {
        var before=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,0,false,false,
                "GOD_CHAIN",9,false,"GOD_BIG_STARTED");
        var rng=new java.util.Random(0L){
            @Override public int nextInt(int bound){return bound-1;}
        };
        var after=JugglerGodTransitions.afterBonus(before,6,rng,125000,62500,500000);
        assertEquals(JugglerGodRuntime.Mode.HEAVEN,after.mode());
        assertFalse(after.forceChainBig());
        assertFalse(after.countNextChainGame());
        assertEquals(32,after.heavenTarget());
        assertEquals(0,after.heavenProgress());
        assertEquals("GOD_END_HEAVEN",after.lastEvent());
    }

    private Session playForcedRole(Rig rig,Session s,InternalRole role,long baseNanos) throws Exception {
        db.forceJugglerGodRoleForSeatedOwner(s.machine(),s.player(),role.name(),NOW+s.sequence()+1);
        s=db.state().session(s.player());
        if(s.state()==Session.GameState.SEATED_READY)s=action(rig,s,PacketType.SPACE_ACTION,baseNanos);
        assertTrue(s.state()==Session.GameState.NORMAL_BETTED||s.state()==Session.GameState.REPLAY_READY);
        s=action(rig,s,PacketType.SPACE_ACTION,baseNanos);
        assertEquals(role.name(),s.text("internal_role"));
        for(var stop:List.of(PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT))
            s=action(rig,s,stop,baseNanos+1_000_000_000L);
        return s;
    }

    @Test void threeForcedCherriesGuaranteeBonusOnNextLiveLeverBothProfiles() throws Exception {
        for(var type:List.of(MachineType.JUGGLER_GOD,MachineType.JUGGLER_GOD_EXTREME)){
            Rig rig=rig(JugglerGodRuntime.initial(),1,type);
            Session s=rig.session();
            for(int i=1;i<=3;i++){
                s=playForcedRole(rig,s,InternalRole.CHERRY,3_000_000_000L*i);
                assertEquals(Session.GameState.SEATED_READY,s.state());
                var runtime=JugglerGodRuntime.fromJson(db.state().machine(s.machine()).runtimeJson());
                assertEquals(10+i,runtime.roleStreak(),type+" consecutive cherry count "+i);
                assertEquals("NONE",runtime.forcedRole());
            }
            s=action(rig,s,PacketType.SPACE_ACTION,15_000_000_000L);
            GameTransition next=plan(rig,s,PacketType.SPACE_ACTION,16_000_000_000L);
            InternalRole role=InternalRole.valueOf(next.after().text("internal_role"));
            assertNotNull(GameRules.bonus(role),type+" cherry x3 should guarantee BIG/REG or GOD on next lever");
            assertEquals(0,JugglerGodRuntime.fromJson(next.machineRuntimeJson()).roleStreak());
        }
    }

    @Test void cherryStreakPersistsAcrossNormalExitAndReSeat() throws Exception {
        Rig rig=rig(JugglerGodRuntime.initial(),1);
        Session s=rig.session();
        for(int i=1;i<=3;i++){
            s=playForcedRole(rig,s,InternalRole.CHERRY,3_000_000_000L*i);
            String prior=JugglerGodRuntime.fromJson(db.state().machine(s.machine()).runtimeJson()).toJsonString();
            assertEquals("USER_CLOSE_SAFE",db.closeSession(s.player(),s.id(),s.machine(),s.sequence()+1,NOW+i*1000,60_000));
            assertFalse(db.state().busy(s.machine()));
            s=db.seat(s.player(),s.machine(),NOW+i*1000+1);
            assertEquals(prior,s.machineState().toString());
            assertEquals(10+i,JugglerGodRuntime.fromJson(s.machineState().toString()).roleStreak());
        }
        s=action(rig,s,PacketType.SPACE_ACTION,20_000_000_000L);
        GameTransition next=plan(rig,s,PacketType.SPACE_ACTION,21_000_000_000L);
        assertNotNull(GameRules.bonus(InternalRole.valueOf(next.after().text("internal_role"))));
    }

    @Test void fiveForcedReplaysWorkOnOccupiedOwnSeatAndGuaranteeNextBonus() throws Exception {
        Rig rig=rig(JugglerGodRuntime.initial(),2);
        Session s=rig.session();
        for(int i=1;i<=5;i++){
            s=playForcedRole(rig,s,InternalRole.REPLAY,3_000_000_000L*i);
            assertEquals(Session.GameState.REPLAY_READY,s.state());
            var runtime=JugglerGodRuntime.fromJson(db.state().machine(s.machine()).runtimeJson());
            assertEquals(5+i,runtime.roleStreak(),"consecutive replay "+i);
        }
        GameTransition next=plan(rig,s,PacketType.SPACE_ACTION,21_000_000_000L);
        assertNotNull(GameRules.bonus(InternalRole.valueOf(next.after().text("internal_role"))));
        assertEquals(0,JugglerGodRuntime.fromJson(next.machineRuntimeJson()).roleStreak());
    }

    @Test void everyPrecursorPathIsReachableFromRealRoleStreaks() {
        for(var role:List.of(InternalRole.GRAPE,InternalRole.REPLAY,InternalRole.CHERRY)){
            int state=0;
            int limit=role==InternalRole.CHERRY?3:5;
            for(int i=1;i<=limit;i++){
                state=JugglerGodGameEngine.followingStreak(state,role);
                var trigger=JugglerGodGameEngine.trigger(state,100_000);
                if(i==2&&role==InternalRole.CHERRY)assertEquals(.40,trigger.bonus(),1e-12);
                if(i==3&&role==InternalRole.CHERRY)assertEquals(1,trigger.bonus(),1e-12);
                if(i==4&&role!=InternalRole.CHERRY)assertEquals(.20,trigger.bonus(),1e-12);
                if(i==5&&role!=InternalRole.CHERRY)assertEquals(1,trigger.bonus(),1e-12);
            }
        }
        assertEquals(.35,JugglerGodGameEngine.trigger(JugglerGodGameEngine.followingStreak(0,InternalRole.BELL),100_000).bonus(),1e-12);
        assertEquals(.15,JugglerGodGameEngine.trigger(JugglerGodGameEngine.followingStreak(0,InternalRole.PIERO),100_000).bonus(),1e-12);
    }

    @Test void highUltraRuntimeSurvivesJsonAndPreservesBonusOnlyLamp() throws Exception {
        var state=JugglerGodRuntime.initial().withHot(JugglerGodRuntime.Mode.HIGH,20,3,"GRAPE_THREE");
        var parsed=JugglerGodRuntime.fromJson(state.toJsonString());
        assertEquals(state,parsed);
        assertEquals(20,parsed.hotRemaining());
        assertEquals(3,parsed.roleStreak());
        var ultra=parsed.withHot(JugglerGodRuntime.Mode.ULTRA,15,9,"REPLAY_FOUR");
        assertEquals(ultra,JugglerGodRuntime.fromJson(ultra.toJsonString()));
        assertEquals(JugglerGodRuntime.Mode.NORMAL,ultra.clearHot().mode());
        assertEquals(0,ultra.clearHot().hotRemaining());
        assertEquals(0,ultra.clearHot().roleStreak());
    }

    @Test void highAndUltraBoostRealBonusRoleDrawWithoutAddingNotice() throws Exception {
        Rig rig=rig(JugglerGodRuntime.initial(),1);
        var field=JugglerGodGameEngine.class.getDeclaredField("highModeScalePpm");
        field.setAccessible(true);
        var ultraField=JugglerGodGameEngine.class.getDeclaredField("ultraModeScalePpm");
        ultraField.setAccessible(true);
        int high=((int[])field.get(rig.engine()))[1];
        int ultra=((int[])ultraField.get(rig.engine()))[1];
        var weights=new RoleWeights(config);
        int total=300000;
        var rng=new Random(950871L);
        int highHits=0,ultraHits=0;
        for(int i=0;i<total;i++){
            if(GameRules.bonus(weights.drawJugglerGod(1,rng,high,813500))!=null)highHits++;
            if(GameRules.bonus(weights.drawJugglerGod(1,rng,ultra,813500))!=null)ultraHits++;
        }
        assertEquals(1-Math.pow(.60,1.0/20.0),highHits/(double)total,.0015);
        assertEquals(1-Math.pow(.30,1.0/15.0),ultraHits/(double)total,.0015);
    }

    @Test void stoppedRoleAdvancesExistingStreakAndSpendsExactlyOneHighGame() throws Exception {
        var runtime=JugglerGodRuntime.initial()
                .withHot(JugglerGodRuntime.Mode.HIGH,20,2,"TEST")
                .forceRole("GRAPE");
        Rig rig=rig(runtime,1);
        Session s=action(rig,rig.session(),PacketType.SPACE_ACTION,0);
        GameTransition lever=plan(rig,s,PacketType.SPACE_ACTION,1_000_000_000L);
        assertEquals("GRAPE",lever.after().text("internal_role"));
        var next=JugglerGodRuntime.fromJson(lever.machineRuntimeJson());
        assertEquals(JugglerGodRuntime.Mode.HIGH,next.mode());
        assertEquals(19,next.hotRemaining());
        assertEquals(3,next.roleStreak());
        assertEquals(0,lever.after().number("lamp_on"));
    }

    @Test void twoStreakIsExplicitHighPromotionOpportunityWithNoPrematureBonusNotice() throws Exception {
        var weights=new RoleWeights(config);
        var normal=new NormalGame(weights,new RandomStreams(333L),SOLVER,main,config,false);
        var engine=new JugglerGodGameEngine(normal,new RandomStreams(444L),weights,config);
        var array=JugglerGodGameEngine.class.getDeclaredField("precursorTwoHighPpm");
        array.setAccessible(true);
        assertEquals(94884,((int[])array.get(engine))[1]);
        var method=JugglerGodGameEngine.class.getDeclaredMethod("trigger",int.class,int.class);
        method.setAccessible(true);
        Object grapeTwo=method.invoke(null,2,100000);
        Object replayTwo=method.invoke(null,7,100000);
        Object cherryOne=method.invoke(null,11,100000);
        var bonus=grapeTwo.getClass().getDeclaredMethod("bonus");
        var high=grapeTwo.getClass().getDeclaredMethod("high");
        bonus.setAccessible(true);high.setAccessible(true);
        assertEquals(0.0,((Double)bonus.invoke(grapeTwo)),1e-12);
        assertEquals(0.1,((Double)high.invoke(grapeTwo)),1e-12);
        assertEquals(0.1,((Double)high.invoke(replayTwo)),1e-12);
        assertEquals(0.08,((Double)high.invoke(cherryOne)),1e-12);
    }

}
