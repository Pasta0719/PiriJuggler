package jp.pirijuggler.paper.game.god;

import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.game.RandomStreams;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import jp.pirijuggler.paper.session.Session;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GodGameEngineTest {
    @Test
    void leverThenThreeStopsCompleteOneGame() {
        GodGameEngine engine=new GodGameEngine(RandomStreams.production());
        Machine machine=machine("MISS");

        var lever=engine.plan(session(50),machine,PacketType.SPACE_ACTION,1,1_700_000_000_000L,0,0,null);
        assertEquals(3,lever.bet());
        assertEquals(0,lever.normalSpins());
        assertTrue(lever.lever());
        assertEquals(Session.GameState.NORMAL_SPINNING,lever.after().state());
        assertNull(lever.machineRuntimeJson());
        assertTrue(engine.committed(lever,0).stream().anyMatch(p->p.packetType()==PacketType.SPIN_START));

        var left=engine.plan(lever.after(),machine,PacketType.STOP_LEFT,2,1_700_000_000_100L,0,0,3);
        assertEquals(1,left.after().number("stopped_mask"));
        assertFalse(left.finished());

        var center=engine.plan(left.after(),machine,PacketType.STOP_CENTER,3,1_700_000_000_200L,0,0,8);
        assertEquals(3,center.after().number("stopped_mask"));
        assertFalse(center.finished());

        var right=engine.plan(center.after(),machine,PacketType.STOP_RIGHT,4,1_700_000_000_300L,0,0,12);
        assertEquals(7,right.after().number("stopped_mask"));
        assertTrue(right.finished());
        assertEquals(1,right.normalSpins());
        assertEquals(Session.GameState.SEATED_READY,right.after().state());
        assertNotNull(right.machineRuntimeJson());
        assertTrue(right.publicDelayMs()>0);
        assertTrue(right.after().publicState().has("godPhase"));
    }

    @Test
    void spaceStopsNextPendingReelWhileSpinning() {
        GodGameEngine engine=new GodGameEngine(RandomStreams.production());
        Machine machine=machine("MISS");
        var lever=engine.plan(session(50),machine,PacketType.SPACE_ACTION,1,1,0,0,null);
        var stop=engine.plan(lever.after(),machine,PacketType.SPACE_ACTION,2,2,0,0,5);
        assertEquals(1,stop.after().number("stopped_mask"));
    }

    @Test
    void zeroCreditIsRejectedWithoutStartingSpin() {
        GodGameEngine engine=new GodGameEngine(RandomStreams.production());
        var t=engine.plan(session(0),machine("MISS"),PacketType.SPACE_ACTION,1,1_700_000_000_000L,0,0,null);
        assertEquals(0,t.bet());
        assertEquals(Session.GameState.SEATED_READY,t.after().state());
        assertNull(t.machineRuntimeJson());
        assertEquals("NOT_ENOUGH_CREDIT",t.packets().getFirst().payload().get("errorCode").getAsString());
    }

    @Test
    void forcedPublishedPayoutsMatchSettlement(){
        assertEquals(3,finishForced("LOWER_YELLOW7").payout());
        assertEquals(15,finishForced("COMMON_YELLOW7").payout());
        assertEquals(1,finishForced("GAIA_BELL").payout());
        assertEquals(15,finishForced("SP").payout());
        assertEquals(15,finishForced("RED7").payout());
        assertEquals(15,finishForced("GOD").payout());
    }

    @Test
    void replayRoleEndsReplayReadyAndNextLeverIsFree(){
        GodGameEngine engine=new GodGameEngine(RandomStreams.production());
        Machine machine=machine("UPPER_BLUE7");
        Session start=session(50);
        var lever=engine.plan(start,machine,PacketType.SPACE_ACTION,1,1,0,0,null);
        var left=engine.plan(lever.after(),machine,PacketType.STOP_LEFT,2,2,0,0,1);
        var center=engine.plan(left.after(),machine,PacketType.STOP_CENTER,3,3,0,0,2);
        var right=engine.plan(center.after(),machine,PacketType.STOP_RIGHT,4,4,0,0,3);
        assertEquals(Session.GameState.REPLAY_READY,right.after().state());
        assertEquals(0,right.payout());

        Machine afterMachine=machineWithRuntime(right.machineRuntimeJson());
        var replayLever=engine.plan(right.after(),afterMachine,PacketType.SPACE_ACTION,5,5,0,0,null);
        assertEquals(0,replayLever.bet());
        assertEquals(47,replayLever.after().number("credit"));
    }

    private static jp.pirijuggler.paper.game.GameTransition finishForced(String role){
        GodGameEngine engine=new GodGameEngine(RandomStreams.production());
        Machine machine=machine(role);
        var lever=engine.plan(session(50),machine,PacketType.SPACE_ACTION,1,1,0,0,null);
        var left=engine.plan(lever.after(),machine,PacketType.STOP_LEFT,2,2,0,0,0);
        var center=engine.plan(left.after(),machine,PacketType.STOP_CENTER,3,3,0,0,0);
        return engine.plan(center.after(),machine,PacketType.STOP_RIGHT,4,4,0,0,0);
    }

    private static Machine machine(String forcedRole){
        String runtime=GodMachineRuntime.initial().withForcedRole(forcedRole).toJsonString();
        return new Machine(1,new Machine.Location(UUID.randomUUID(),"world",0,64,0,"NORTH"),
                MachineType.GOD,1,true,false,false,0,0,0,runtime,1,1);
    }

    private static Machine machineWithRuntime(String runtime){
        return new Machine(1,new Machine.Location(UUID.randomUUID(),"world",0,64,0,"NORTH"),
                MachineType.GOD,1,true,false,false,0,0,0,runtime,1,1);
    }

    private static Session session(int credit){
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("session_id",UUID.randomUUID().toString());
        v.put("player_uuid",UUID.randomUUID().toString());
        v.put("machine_id",1);
        v.put("source_business_period_id","p");
        v.put("game_state","SEATED_READY");
        v.put("lifecycle","ACTIVE");
        v.put("credit",credit);v.put("held_medals",0L);
        v.put("spin_id",null);v.put("internal_role",null);v.put("premium_type",null);
        v.put("notice_state","NONE");v.put("lamp_on",0);
        v.put("bonus_type",null);v.put("bonus_payout_count",0L);
        v.put("current_bet",0);v.put("pay_display",0);
        v.put("display_left_stop",0);v.put("display_center_stop",0);v.put("display_right_stop",0);
        v.put("stopped_mask",0);v.put("phase_left",0.0);v.put("phase_center",0.0);v.put("phase_right",0.0);
        v.put("motion_profile",null);v.put("machine_state_json",null);
        v.put("last_client_sequence",0L);v.put("last_activity",0L);v.put("lock_expires_at",null);
        return new Session(v);
    }
}
