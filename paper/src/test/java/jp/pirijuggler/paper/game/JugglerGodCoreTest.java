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

    @Test void runtimeRoundTripPreservesHiddenSuccessorState() {
        var state=new JugglerGodRuntime(JugglerGodRuntime.Mode.HEAVEN,32,17,4,true,true,"GOD_CHAIN",9,true,"TEST");
        assertEquals(state,JugglerGodRuntime.fromJson(state.toJsonString()));
    }
}
