package jp.pirijuggler.paper.game.pachinko;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PachinkoGameEngineFireTest {
    @Test void accountingUsedByEnginePreservesSlotIndependentBalance() {
        var loaned=PachinkoBallAccounting.lend(PachinkoRuntime.initial(),10);
        var fired=PachinkoBallAccounting.fire(loaned,20);
        assertEquals(249,fired.ballsHeld());
        assertEquals(1,fired.totalFired());
        assertEquals(1,fired.ballSequenceId());
        assertEquals(250,fired.ballsLoaned());
    }
}
