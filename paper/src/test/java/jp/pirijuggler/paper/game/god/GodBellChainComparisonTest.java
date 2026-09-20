package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodBellChainComparisonTest {
    @Test void highBellRateMakesShortChainsExtremelyExpensive() {
        double p=1.0/1.7;
        var rows=GodBellChainComparison.compare(50,p,4,8,35.0,350.0);

        assertEquals(5,rows.size());
        assertEquals(4,rows.get(0).threshold());
        assertTrue(rows.get(0).expectedQualifyingBellsPerSet()
                > rows.get(1).expectedQualifyingBellsPerSet());
        assertTrue(rows.get(1).expectedQualifyingBellsPerSet()
                > rows.get(2).expectedQualifyingBellsPerSet());

        // A 35-medal/set budget must require a much smaller award chance at short thresholds.
        assertTrue(rows.get(0).awardChanceForBudget()
                < rows.get(4).awardChanceForBudget());
    }

    @Test void impossibleBudgetIsReportedWithoutInventingAChance() {
        var rows=GodBellChainComparison.compare(5,.1,5,5,350,350);
        assertTrue(Double.isNaN(rows.get(0).awardChanceForBudget()));
    }
}
