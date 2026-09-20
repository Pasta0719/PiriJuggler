package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class GodMachineRuntimeTest {
    @Test void initialRuntimeRoundTrips(){
        GodMachineRuntime initial=GodMachineRuntime.initial();
        assertEquals(initial,GodMachineRuntime.fromJson(initial.toJsonString()));
        assertEquals(GodFrontMode.LOW_A,initial.frontMode());
        assertEquals(GodGaiaMode.LOW,initial.gaiaMode());
        assertEquals(GodPhase.NORMAL,initial.gameplay().phase());
        assertEquals(0,initial.ceilingTarget());
    }
    @Test void rejectsNegativeMachineCounters(){
        assertThrows(IllegalArgumentException.class,()->new GodMachineRuntime(
                GodFrontMode.NORMAL,-1,0,0,GodGaiaMode.LOW,0,0,false,0,1480,0,GodSessionState.initial()));
    }
    @Test void heavenTargetAlwaysWithinThree(){
        var rng=new SplittableRandom(7);
        for(int i=0;i<1000;i++)assertTrue(GodGaiaRules.chooseTarget(GodGaiaMode.HEAVEN,rng)<=3);
    }
}
