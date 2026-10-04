package jp.pirijuggler.paper.game.pachinko;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PachinkoSpecTest {
    @Test void lockedEconomicsStayConsistent() {
        assertEquals(1545.0, PachinkoSpec.averageRightPayout(), 1e-9);
        assertEquals(4.263157894736842, PachinkoSpec.expectedRightWinsAfterEntry(), 1e-12);
        assertEquals(5031.947368421053, PachinkoSpec.expectedGrossPayoutPerInitialJackpot(), 1e-9);
        assertEquals(14.903902899634103, PachinkoSpec.equivalentBorderSpinsPer1000Yen(), 1e-12);
    }

    @Test void payoutRateMovesWithRotationAndBorderIsBreakEven() {
        double border = PachinkoSpec.equivalentBorderSpinsPer1000Yen();
        assertEquals(100.0, PachinkoSpec.payoutRatePercent(border), 1e-9);
        assertEquals(115.09021656081629, PachinkoSpec.payoutRatePercent(17.0), 1e-9);
        assertTrue(PachinkoSpec.payoutRatePercent(16.0) < PachinkoSpec.payoutRatePercent(17.0));
        assertTrue(PachinkoSpec.payoutRatePercent(18.0) > PachinkoSpec.payoutRatePercent(17.0));
    }
}
