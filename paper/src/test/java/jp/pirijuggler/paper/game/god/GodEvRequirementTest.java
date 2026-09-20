package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodEvRequirementTest {
    @Test void settingOneBudgetIsDerivedFromTargetAndPublishedCoinBase() {
        var r=GodEvRequirement.forSetting(1,3000.0);

        double target=3.0*(0.972-1.0);
        double base=-50.0/30.8;
        double required=target-base;
        double god=3000.0/8192.0;

        assertEquals(target,r.targetNetPerBaseGame(),1e-12);
        assertEquals(base,r.normalBaseNetPerGame(),1e-12);
        assertEquals(required,r.requiredPositiveEvPerBaseGame(),1e-12);
        assertEquals(god,r.godEvPerEligibleGame(),1e-12);
        assertEquals(required-god,r.remainingNonGodEvPerBaseGame(),1e-12);
    }

    @Test void higherSettingsNeedMorePositiveEvAtSameNormalBase() {
        var s1=GodEvRequirement.forSetting(1,3000.0);
        var s6=GodEvRequirement.forSetting(6,3000.0);
        assertTrue(s6.requiredPositiveEvPerBaseGame()>s1.requiredPositiveEvPerBaseGame());
        assertTrue(s6.remainingNonGodEvPerBaseGame()>s1.remainingNonGodEvPerBaseGame());
    }
}
