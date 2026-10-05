package jp.pirijuggler.paper.game.pachinko;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PachinkoBallAccountingTest {
    @Test void lendingFiringAndValidStartKeepExactCounters() {
        var initial=PachinkoRuntime.initial();
        var loan=PachinkoBallAccounting.lend(initial,100);
        assertEquals(250,loan.ballsHeld());
        assertEquals(250,loan.ballsLoaned());
        var fired=PachinkoBallAccounting.fire(loan,101);
        assertEquals(249,fired.ballsHeld());
        assertEquals(1,fired.totalFired());
        assertEquals(1,fired.ballSequenceId());
        var started=PachinkoBallAccounting.validStart(fired,102);
        assertEquals(250,started.ballsHeld());
        assertEquals(1,started.totalStarts());
        assertEquals(250.0,started.measuredSpinsPer1000Yen(),0.00001);
        assertEquals(started,PachinkoRuntime.fromJson(started.toJsonString()));
    }

    @Test void cannotFireWithoutBallsOrInventUnfiredStart() {
        var initial=PachinkoRuntime.initial();
        assertThrows(IllegalStateException.class,()->PachinkoBallAccounting.fire(initial,1));
        assertThrows(IllegalStateException.class,()->PachinkoBallAccounting.validStart(initial,1));
    }
}
