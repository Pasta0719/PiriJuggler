package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodKisekiBaselineTest {
    @Test void publishedInitialHitBenchmarksAreLocked() {
        assertEquals(533.0, GodKisekiBaseline.atInitialOdds(1));
        assertEquals(420.0, GodKisekiBaseline.atInitialOdds(2));
        assertEquals(496.0, GodKisekiBaseline.atInitialOdds(3));
        assertEquals(338.0, GodKisekiBaseline.atInitialOdds(4));
        assertEquals(455.0, GodKisekiBaseline.atInitialOdds(5));
        assertEquals(295.0, GodKisekiBaseline.atInitialOdds(6));
    }

    @Test void piriGodIsExactlyTwiceTheKisekiReferenceFrequency() {
        double kiseki=1.0/GodKisekiBaseline.KISEKI_GOD_DENOMINATOR;
        double piri=1.0/GodKisekiBaseline.PIRI_GOD_DENOMINATOR;
        assertEquals(kiseki*2.0,piri,0.0);
        assertEquals(8192,GodEconomyTargets.GOD_DENOMINATOR);
    }

    @Test void coreAtBaselineMatchesCurrentEconomyTargetConstants() {
        assertEquals(GodKisekiBaseline.GG_GAMES,GodEconomyTargets.GG_GAMES);
        assertEquals(GodKisekiBaseline.GG_PURE_INCREASE_PER_GAME,
                GodEconomyTargets.GG_PURE_INCREASE_PER_GAME);
    }
}
