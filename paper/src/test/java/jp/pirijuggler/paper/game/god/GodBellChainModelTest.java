package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodBellChainModelTest {
    @Test void thresholdOneCountsEveryBellExactly() {
        var r=GodBellChainModel.evaluate(50,.6,1,.25,350);
        assertEquals(30.0,r.expectedQualifyingBells(),1e-12);
        assertEquals(7.5,r.expectedVStocks(),1e-12);
        assertEquals(2625.0,r.expectedNetMedals(),1e-9);
    }

    @Test void fiveBellChainMatchesClosedFormForSingleOpportunityWindow() {
        // With exactly 5 games, a threshold-5 opportunity exists iff all 5 are bells.
        double p=.7;
        var r=GodBellChainModel.evaluate(5,p,5,1.0,350);
        assertEquals(Math.pow(p,5),r.expectedQualifyingBells(),1e-12);
        assertEquals(Math.pow(p,5),r.expectedVStocks(),1e-12);
    }

    @Test void awardChanceCanBeSolvedFromEvBudget() {
        double target=35.0;
        double q=GodBellChainModel.solveAwardChance(50,.7,5,target,350);
        var r=GodBellChainModel.evaluate(50,.7,5,q,350);
        assertEquals(target,r.expectedNetMedals(),1e-10);
        assertTrue(q>=0&&q<=1);
    }

    @Test void impossibleBellBudgetIsRejected() {
        assertThrows(IllegalArgumentException.class,
                ()->GodBellChainModel.solveAwardChance(50,.1,8,10000,350));
    }
}
