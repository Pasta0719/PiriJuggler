package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodMachineRuntimeTest {
    @Test
    void initialRuntimeRoundTrips() {
        GodMachineRuntime initial=GodMachineRuntime.initial();
        assertEquals(initial,GodMachineRuntime.fromJson(initial.toJsonString()));
        assertEquals(GodFrontMode.LOW_A,initial.frontMode());
        assertEquals(0,initial.normalGamesSinceGg());
    }

    @Test
    void rejectsNegativeMachineCounters() {
        assertThrows(IllegalArgumentException.class,()->new GodMachineRuntime(
                GodFrontMode.NORMAL,-1,0,0,0,0));
    }
}
