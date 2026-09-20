package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodPremiumCalibrationTest {
    @Test void publishedThreeThousandFloorDoesNotInventLoopDistribution() {
        var r=GodPremiumCalibration.publishedFloor();
        assertEquals(1400.0,r.guaranteedNetMedals(),1e-12);
        assertEquals(3000.0,r.targetExpectedNetMedals(),1e-12);
        assertEquals(1600.0,r.requiredAdditionalNetMedals(),1e-12);
        assertEquals(1600.0/350.0,r.impliedAdditionalGgEquivalents(),1e-12);
    }

    @Test void oneOver8192AtThreeThousandAddsOverSixPayoutPointsVsKisekiRate() {
        double points=GodPremiumCalibration.extraPayoutPointsFromRateOverride(3000.0);
        assertEquals((3000.0/16384.0)/3.0*100.0,points,1e-12);
        assertTrue(points>6.10);
    }
}
