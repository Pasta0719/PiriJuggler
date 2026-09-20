package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodEconomyTargetsTest {
    @Test void startingTargetsAreLocked() {
        assertEquals(97.2, GodEconomyTargets.payoutPercent(1));
        assertEquals(114.6, GodEconomyTargets.payoutPercent(6));
        assertEquals(350.0, GodEconomyTargets.ggSetNetMedals());
        assertEquals(1.0 / 8192.0, 1.0 / GodEconomyTargets.GOD_DENOMINATOR, 0.0);
    }

    @Test void godBudgetIsIndependentAndExactlyOneOver8192() {
        double expectedNetPerGod=2800;
        assertEquals(expectedNetPerGod/8192.0,
                GodEconomyTargets.godNetContributionPerGame(expectedNetPerGod),1e-12);
        assertEquals(GodEconomyTargets.targetNetPerGame(1)-expectedNetPerGod/8192.0,
                GodEconomyTargets.residualNetBudgetPerGame(1,expectedNetPerGod),1e-12);
    }

    @Test void evBudgetAddsSourceContributions() {
        var budget=new GodEvBudget(2800,700,900,350,
                1.0/8192.0,1.0/500.0,1.0/7000.0,1.0/1000.0);
        assertEquals(2800.0/8192.0,budget.godPerGame(),1e-12);
        assertEquals(700.0/500.0,budget.normalGgPerGame(),1e-12);
        assertEquals(350.0/1000.0,budget.bellVPerGame(),1e-12);
    }
}
