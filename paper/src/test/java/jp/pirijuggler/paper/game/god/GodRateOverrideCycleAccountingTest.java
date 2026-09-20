package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodRateOverrideCycleAccountingTest {
    @Test void premiumAddedTimeChangesBothNumeratorAndDenominator() {
        var r=GodRateOverrideCycleAccounting.evaluate(
                10000,
                30000,
                29160,
                10000,
                1.0/16384.0,
                200,
                3,
                10
        );
        assertEquals(97.2,r.baselinePayoutPercent(),1e-12);
        assertTrue(r.addedBetPerCycle()>0);
        assertTrue(r.addedPayoutPerCycle()>r.addedBetPerCycle());
        assertTrue(r.adjustedPayoutPercent()>r.baselinePayoutPercent());
    }

    @Test void zeroDurationPremiumHasNoAccountingEffect() {
        var r=GodRateOverrideCycleAccounting.evaluate(
                1000,3000,2916,1000,1.0/16384.0,0,3,10);
        assertEquals(97.2,r.adjustedPayoutPercent(),1e-12);
    }
}
