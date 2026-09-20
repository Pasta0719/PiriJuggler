package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodRateOverrideRebalanceTest {
    @Test void exactRateReductionEqualsExtraGodEvDividedByInitialValue() {
        var r=GodRateOverrideRebalance.absorbEntireGodDeltaInNormalGg(
                500.0, 700.0, 3000.0);

        double expectedDelta=(3000.0/16384.0)/700.0;
        assertEquals(expectedDelta,r.removedInitialRatePerGame(),1e-15);
        assertEquals(1.0/500.0-expectedDelta,r.adjustedInitialRate(),1e-15);
        assertEquals(3000.0/16384.0,r.extraGodNetPerGame(),1e-15);
    }

    @Test void settingBaselineIsUsedWithoutChangingPublishedReference() {
        var r=GodRateOverrideRebalance.forSetting(1,3000.0,3000.0);
        assertEquals(533.0,r.originalInitialOdds(),0.0);
        assertTrue(r.adjustedInitialOdds()>r.originalInitialOdds());
    }

    @Test void rejectsCaseWhereNormalGgCannotCarryWholeAdjustment() {
        assertThrows(IllegalArgumentException.class,
                ()->GodRateOverrideRebalance.absorbEntireGodDeltaInNormalGg(
                        533.0, 90.0, 3000.0));
    }
}
