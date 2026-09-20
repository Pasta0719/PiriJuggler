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
    void oneSpaceActionResolvesOneDurableGame() {
        GodGameEngine engine=new GodGameEngine(RandomStreams.production());
        Session before=session(50);
        Machine machine=machine();
        var t=engine.plan(before,machine,PacketType.SPACE_ACTION,1,1_700_000_000_000L,0,0,null);
        assertEquals(3,t.bet());
        assertEquals(1,t.normalSpins());
        assertEquals(Session.GameState.SEATED_READY,t.after().state());
        assertNotNull(t.machineRuntimeJson());
        assertNotNull(t.after().machineState());
        assertTrue(t.after().publicState().has("godPhase"));
        assertEquals(1,t.after().sequence());
    }

    @Test
    void zeroCreditIsRejectedWithoutAdvancingCabinetRuntime() {
        GodGameEngine engine=new GodGameEngine(RandomStreams.production());
        var t=engine.plan(session(0),machine(),PacketType.SPACE_ACTION,1,1_700_000_000_000L,0,0,null);
        assertEquals(0,t.bet());
        assertEquals(0,t.normalSpins());
        assertNull(t.machineRuntimeJson());
        assertEquals("NOT_ENOUGH_CREDIT",t.packets().getFirst().payload().get("errorCode").getAsString());
    }

    private static Machine machine(){
        return new Machine(1,new Machine.Location(UUID.randomUUID(),"world",0,64,0,"NORTH"),
                MachineType.GOD,1,true,false,false,0,0,0,null,1,1);
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
