package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodEconomyTargetsTest {
    @Test void fixedProductionInvariantsAreLockedButPayoutCurveIsExplicit() {
        assertEquals(350.0, GodEconomyTargets.ggSetNetMedals());
        assertEquals(1.0 / 8192.0, 1.0 / GodEconomyTargets.GOD_DENOMINATOR, 0.0);

        var benchmark=GodPayoutCurve.kisekiBenchmark();
        assertEquals(97.2,benchmark.payoutPercent(1));
        assertEquals(114.6,benchmark.payoutPercent(6));
    }

    @Test void godBudgetUsesCallerSuppliedTargetCurve() {
        var target=GodPayoutCurve.of(98,100,102,104,108,112);
        double expectedNetPerGod=2800;
        assertEquals(expectedNetPerGod/8192.0,
                GodEconomyTargets.godNetContributionPerGame(expectedNetPerGod),1e-12);
        assertEquals(target.targetNetPerGame(1)-expectedNetPerGod/8192.0,
                GodEconomyTargets.residualNetBudgetPerGame(target,1,expectedNetPerGod),1e-12);
    }

    @Test void evBudgetContainsNoRejectedBellChainSource() {
        var budget=new GodEvBudget(2800,700,900,
                1.0/8192.0,1.0/500.0,1.0/7000.0);
        assertEquals(2800.0/8192.0,budget.godPerGame(),1e-12);
        assertEquals(700.0/500.0,budget.normalGgPerGame(),1e-12);
        assertEquals(900.0/7000.0,budget.sggPerGame(),1e-12);
    }
}
