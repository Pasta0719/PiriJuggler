package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodPremiumImpactTest {
    @Test void kisekiGodHasFourGuaranteedGgEquivalentsBeforeLoop() {
        assertEquals(1400.0,GodPremiumImpact.guaranteedNetPerGodBeforeLoop(),1e-12);
    }

    @Test void doublingGodFrequencyHasAConcreteMinimumReturnImpact() {
        assertEquals(1400.0/16384.0,
                GodPremiumImpact.minimumExtraNetPerGameFromPiriGodRate(),1e-12);
        assertEquals((1400.0/16384.0)/3.0*100.0,
                GodPremiumImpact.minimumExtraPayoutPoints(),1e-12);
        assertTrue(GodPremiumImpact.minimumExtraPayoutPoints()>2.84);
    }
}
