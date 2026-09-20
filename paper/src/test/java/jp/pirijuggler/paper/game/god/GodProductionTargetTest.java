package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodProductionTargetTest {
    @Test void productionCurveIsLocked() {
        var c=GodProductionTarget.curve();
        assertEquals(97.2,c.payoutPercent(1));
        assertEquals(99.1,c.payoutPercent(2));
        assertEquals(102.1,c.payoutPercent(3));
        assertEquals(106.9,c.payoutPercent(4));
        assertEquals(111.7,c.payoutPercent(5));
        assertEquals(114.6,c.payoutPercent(6));
    }
}
