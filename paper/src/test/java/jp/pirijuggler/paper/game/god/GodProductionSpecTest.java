package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodProductionSpecTest {
    @Test
    void locksPiriGodV1PremiumAndGgRules() {
        assertEquals(8192, GodProductionSpec.GOD_DENOMINATOR);
        assertEquals(6900, GodProductionSpec.RED7_DENOMINATOR);
        assertEquals(65536, GodProductionSpec.SP_DENOMINATOR);
        assertEquals(50, GodProductionSpec.GG_GAMES);
        assertEquals(7.0, GodProductionSpec.GG_PURE_INCREASE_PER_GAME);
        assertEquals(350.0, GodProductionSpec.ggSetNetMedals());
        assertEquals(4, GodProductionSpec.GOD_GUARANTEED_GG_SETS);
        assertEquals(GodLoopType.D, GodProductionSpec.GOD_LOOP);
        assertFalse(GodProductionSpec.GG_BELL_STREAK_V_STOCK_ENABLED);
    }

    @Test
    void locksCeilingRules() {
        assertEquals(1480, GodProductionSpec.NORMAL_CEILING_GAMES);
        assertEquals(510, GodProductionSpec.chooseResetCeiling(0.0));
        assertEquals(510, GodProductionSpec.chooseResetCeiling(0.151999));
        assertEquals(1000, GodProductionSpec.chooseResetCeiling(0.152));
        assertEquals(1000, GodProductionSpec.chooseResetCeiling(0.354999));
        assertEquals(1480, GodProductionSpec.chooseResetCeiling(0.355));
        assertEquals(1480, GodProductionSpec.chooseResetCeiling(0.999999));
        assertThrows(IllegalArgumentException.class, () -> GodProductionSpec.chooseResetCeiling(-0.1));
        assertThrows(IllegalArgumentException.class, () -> GodProductionSpec.chooseResetCeiling(1.0));
    }

    @Test
    void loopTypesExposeExactContinuationRates() {
        assertEquals(0.01, GodLoopType.A.continuationRate());
        assertEquals(0.25, GodLoopType.B.continuationRate());
        assertEquals(0.50, GodLoopType.C.continuationRate());
        assertEquals(0.80, GodLoopType.D.continuationRate());
        assertEquals(4.0, GodLoopType.D.expectedExtraStocks(), 1e-12);
    }
}
