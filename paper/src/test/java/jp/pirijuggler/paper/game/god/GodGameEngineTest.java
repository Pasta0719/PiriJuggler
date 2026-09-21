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

    @Test
    void gaiaBellRejectsLeftFirstAndAcceptsRightFirstNavigation(){
        GodGameEngine engine=new GodGameEngine(RandomStreams.production());
        Machine machine=machine("GAIA_BELL");
        var lever=engine.plan(session(50),machine,PacketType.SPACE_ACTION,1,1,0,0,null);
        var rejected=engine.plan(lever.after(),machine,PacketType.STOP_LEFT,2,2,0,0,0);
        assertEquals(0,rejected.after().number("stopped_mask"));
        assertEquals("INVALID_STATE",rejected.packets().getFirst().payload().get("errorCode").getAsString());
        var right=engine.plan(lever.after(),machine,PacketType.STOP_RIGHT,3,3,0,0,0);
        assertEquals(4,right.after().number("stopped_mask"));
    }


    @Test
    void pendingDisplayRoleAndPayoutComeFromSameResolvedOutcome(){
        GodGameEngine engine=new GodGameEngine(RandomStreams.production());

        var lower=engine.plan(session(50),machine("LOWER_YELLOW7"),PacketType.SPACE_ACTION,1,1,0,0,null);
        assertEquals("LOWER_YELLOW7",lower.after().machineState().get("_pendingDisplayRole").getAsString());
        assertEquals(3,lower.after().machineState().get("_pendingPayout").getAsInt());

        var common=engine.plan(session(50),machine("COMMON_YELLOW7"),PacketType.SPACE_ACTION,1,1,0,0,null);
        assertEquals("COMMON_YELLOW7",common.after().machineState().get("_pendingDisplayRole").getAsString());
        assertEquals(15,common.after().machineState().get("_pendingPayout").getAsInt());

        var ordered=engine.plan(session(50),machine("ORDERED_YELLOW7"),PacketType.SPACE_ACTION,1,1,0,0,null);
        assertEquals("MISS",ordered.after().machineState().get("_pendingDisplayRole").getAsString());
        assertEquals(1,ordered.after().machineState().get("_pendingPayout").getAsInt());
        assertFalse(ordered.after().machineState().has("_pendingStopOrder"));
    }

    private static jp.pirijuggler.paper.game.GameTransition finishForced(String role){
        GodGameEngine engine=new GodGameEngine(RandomStreams.production());
        Machine machine=machine(role);
        var lever=engine.plan(session(50),machine,PacketType.SPACE_ACTION,1,1,0,0,null);
        if("GAIA_BELL".equals(role)){
            var right=engine.plan(lever.after(),machine,PacketType.STOP_RIGHT,2,2,0,0,0);
            var left=engine.plan(right.after(),machine,PacketType.STOP_LEFT,3,3,0,0,0);
            return engine.plan(left.after(),machine,PacketType.STOP_CENTER,4,4,0,0,0);
        }
        var left=engine.plan(lever.after(),machine,PacketType.STOP_LEFT,2,2,0,0,0);
        var center=engine.plan(left.after(),machine,PacketType.STOP_CENTER,3,3,0,0,0);
        return engine.plan(center.after(),machine,PacketType.STOP_RIGHT,4,4,0,0,0);
    }

    @Test
    void orderedYellowInGgPublishesAndEnforcesPushOrder(){
        GodGameEngine engine=new GodGameEngine(RandomStreams.production());
        GodSessionState gg=new GodSessionState(GodPhase.GG,50,0,GodLoopType.A,0,0,0,0,0,0,0,0,"GG_TEST","TEST");
        GodMachineRuntime runtime=GodMachineRuntime.initial().withGameplay(gg).withForcedRole("ORDERED_YELLOW7");
        Machine machine=machineWithRuntime(runtime.toJsonString());
        var lever=engine.plan(session(50),machine,PacketType.SPACE_ACTION,1,1,0,0,null);

        var state=lever.after().machineState();
        assertTrue(state.has("_pendingStopOrder"));
        assertEquals(15,state.get("_pendingPayout").getAsInt());
        int expected=state.getAsJsonArray("_pendingStopOrder").get(0).getAsInt();

        var startPacket=engine.committed(lever,0).stream().filter(p->p.packetType()==PacketType.SPIN_START).findFirst().orElseThrow();
        assertTrue(startPacket.payload().has("godNav"));
        assertEquals(1,startPacket.payload().getAsJsonObject("stopHints").entrySet().size());

        int wrong=(expected+1)%3;
        var rejected=engine.plan(lever.after(),machine,actionFor(wrong),2,2,0,0,0);
        assertEquals(0,rejected.after().number("stopped_mask"));
        assertEquals("INVALID_STATE",rejected.packets().getFirst().payload().get("errorCode").getAsString());
    }

    private static PacketType actionFor(int reel){
        return switch(reel){case 0->PacketType.STOP_LEFT;case 1->PacketType.STOP_CENTER;case 2->PacketType.STOP_RIGHT;default->throw new IllegalArgumentException();};
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
    @Test
    void everyForcedRoleCompletesWithMatchingPayoutReplayAndVisibleFormation(){
        record Expect(String role,int payout,boolean replay,String display){}
        Expect[] expects={
                new Expect("MISS",0,false,"MISS"),
                new Expect("UPPER_BLUE7",0,true,"UPPER_BLUE7"),
                new Expect("MIDDLE_BLUE7",0,true,"MIDDLE_BLUE7"),
                new Expect("ORDERED_YELLOW7",1,false,"MISS"),
                new Expect("LOWER_YELLOW7",3,false,"LOWER_YELLOW7"),
                new Expect("RISING_YELLOW7",15,false,"RISING_YELLOW7"),
                new Expect("MIDDLE_YELLOW7",15,false,"MIDDLE_YELLOW7"),
                new Expect("COMMON_YELLOW7",15,false,"COMMON_YELLOW7"),
                new Expect("GAIA_BELL",1,false,"GAIA_BELL"),
                new Expect("RED7_FAKE",0,true,"RED7_FAKE"),
                new Expect("RED7",15,false,"RED7"),
                new Expect("GOD",15,false,"GOD"),
                new Expect("SP",15,false,"SP")
        };

        for(Expect e:expects){
            GodGameEngine engine=new GodGameEngine(RandomStreams.production());
            Machine machine=machine(e.role());
            var t=engine.plan(session(50),machine,PacketType.SPACE_ACTION,1,1,0,0,null);
            var pending=t.after().machineState();
            assertEquals(e.role(),pending.get("_pendingRole").getAsString(),e.role());
            assertEquals(e.display(),pending.get("_pendingDisplayRole").getAsString(),e.role());
            assertEquals(e.payout(),pending.get("_pendingPayout").getAsInt(),e.role());
            assertEquals(e.replay(),pending.get("_pendingReplay").getAsBoolean(),e.role());

            long seq=2;
            while(t.after().state()==Session.GameState.NORMAL_SPINNING){
                var state=t.after().machineState();
                int mask=(int)t.after().number("stopped_mask");
                int reel;
                if(state.has("_pendingStopOrder")){
                    int step=Integer.bitCount(mask);
                    reel=state.getAsJsonArray("_pendingStopOrder").get(step).getAsInt();
                }else if(mask==0&&state.has("_pendingFirstReel")){
                    reel=state.get("_pendingFirstReel").getAsInt();
                }else{
                    reel=-1;
                    for(int i=0;i<3;i++)if((mask&(1<<i))==0){reel=i;break;}
                }
                t=engine.plan(t.after(),machine,actionFor(reel),seq,seq,0,0,0);
                seq++;
            }

            assertEquals(e.payout(),t.payout(),e.role());
            assertEquals(e.replay()?Session.GameState.REPLAY_READY:Session.GameState.SEATED_READY,t.after().state(),e.role());

            int l=(int)t.after().number("display_left_stop");
            int m=(int)t.after().number("display_center_stop");
            int r=(int)t.after().number("display_right_stop");
            if("MISS".equals(e.display()))
                assertTrue(jp.pirijuggler.common.reel.GodStopControl.isSafeMiss(l,m,r),e.role());
            else if("RED7_FAKE".equals(e.display()))
                assertTrue(jp.pirijuggler.common.reel.GodStopControl.isSafeFakeRed(l,m,r),e.role());
            else
                assertTrue(jp.pirijuggler.common.reel.GodStopControl.matchesPublishedForm(e.display(),l,m,r),e.role());
        }
    }

}
