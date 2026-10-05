package jp.pirijuggler.paper.game.pachinko;

import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.game.GameTransition;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.session.Session;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PachinkoGameEngineFireTest {
    @Test void fireIsDurableAndDoesNotUseSlotCredit() {
        Session before=PachinkoEngineTestSupport.sessionWith(PachinkoBallAccounting.lend(PachinkoRuntime.initial(),10),1);
        Machine machine=PachinkoEngineTestSupport.machine();
        var transition=new PachinkoGameEngine().plan(before,machine,PacketType.PACHINKO_FIRE,2,20,0,0,null);
        var runtime=PachinkoRuntime.fromJson(transition.after().text("machine_state_json"));
        assertEquals(249,runtime.ballsHeld());
        assertEquals(1,runtime.totalFired());
        assertEquals(1,runtime.ballSequenceId());
        assertEquals(before.number("credit"),transition.after().number("credit"));
        assertEquals(before.number("held_medals"),transition.after().number("held_medals"));
        assertEquals(runtime.toJsonString(),transition.machineRuntimeJson());
    }
}
