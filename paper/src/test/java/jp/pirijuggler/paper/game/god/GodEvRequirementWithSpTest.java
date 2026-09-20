package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodEvRequirementWithSpTest {
    @Test void spIsReservedExplicitlyFromRemainingPremiumBudget() {
        var r=GodEvRequirementWithSp.forSetting(1,3000.0);
        double sp=875.0/65536.0;
        assertEquals(sp,r.spEvPerEligibleGame(),1e-12);

        var base=GodEvRequirement.forSetting(1,3000.0);
        assertEquals(base.remainingNonGodEvPerBaseGame()-sp,
                r.remainingEvForNormalGgLoopSggZ(),1e-12);
    }

    @Test void higherSettingStillNeedsMoreResidualEv() {
        var s1=GodEvRequirementWithSp.forSetting(1,3000.0);
        var s6=GodEvRequirementWithSp.forSetting(6,3000.0);
        assertTrue(s6.remainingEvForNormalGgLoopSggZ()
                > s1.remainingEvForNormalGgLoopSggZ());
    }
}
