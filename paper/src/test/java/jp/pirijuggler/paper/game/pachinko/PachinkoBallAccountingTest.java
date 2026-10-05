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
    @Test void measuredRotationUsesActualDurableFiredAndStartCounters() {
        var r=PachinkoRuntime.initial();
        r=PachinkoBallAccounting.lend(r,1);
        for(int i=0;i<250;i++){r=PachinkoBallAccounting.fire(r,2+i);if(i<17)r=PachinkoBallAccounting.validStart(r,300+i);}
        assertEquals(17.0,r.measuredSpinsPer1000Yen(),1e-12);
        assertEquals(PachinkoSpec.payoutRatePercent(17.0),PachinkoSpec.payoutRatePercent(r.measuredSpinsPer1000Yen()),1e-12);
    }

}
