package jp.pirijuggler.paper.game.pachinko;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PachinkoRuntimeTest {
    @Test void initialRuntimeRoundTrips() {
        var initial=PachinkoRuntime.initial();
        assertEquals(initial,PachinkoRuntime.fromJson(initial.toJsonString()));
        assertEquals(0.0,initial.measuredSpinsPer1000Yen(),1e-12);
    }

    @Test void measuredRotationUsesActualFiredBallsAndStarts() {
        var runtime=new PachinkoRuntime(
                PachinkoRuntime.Mode.NORMAL,
                0,2500,2500,170,12,
                PachinkoRuntime.Presentation.IDLE,
                false,PachinkoRuntime.InitialOutcome.NONE,
                false,0,PachinkoRuntime.RightOutcome.NONE,0,0,1234
        );
        assertEquals(17.0,runtime.measuredSpinsPer1000Yen(),1e-12);
        assertEquals(runtime,PachinkoRuntime.fromJson(runtime.toJsonString()));
    }

    @Test void invalidRushStateIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new PachinkoRuntime(
                PachinkoRuntime.Mode.NORMAL,
                0,0,0,0,0,
                PachinkoRuntime.Presentation.IDLE,
                true,PachinkoRuntime.InitialOutcome.RUSH_1500,
                true,0,PachinkoRuntime.RightOutcome.NONE,0,0,0
        ));
    }
}
