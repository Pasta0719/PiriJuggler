package jp.pirijuggler.paper.game.god;

/**
 * Quantifies the payout impact of Piri's deliberate GOD-rate override.
 *
 * Kiseki PGG guarantees GOD stage 50G + three GG stocks = four GG-equivalent
 * 50G blocks before the additional strong loop stock is valued.
 */
public final class GodPremiumImpact {
    public static final int KISEKI_GUARANTEED_GG_EQUIVALENTS = 4;

    private GodPremiumImpact() {}

    public static double guaranteedNetPerGodBeforeLoop() {
        return KISEKI_GUARANTEED_GG_EQUIVALENTS * GodEconomyTargets.ggSetNetMedals();
    }

    /**
     * Extra net medals/game caused only by changing GOD from 1/16384 to 1/8192,
     * before assigning any value to the strong loop stock.
     */
    public static double minimumExtraNetPerGameFromPiriGodRate() {
        double reward=guaranteedNetPerGodBeforeLoop();
        return reward * (1.0 / GodKisekiBaseline.PIRI_GOD_DENOMINATOR
                - 1.0 / GodKisekiBaseline.KISEKI_GOD_DENOMINATOR);
    }

    /** Minimum payout-percentage-point increase at a 3-medal wager. */
    public static double minimumExtraPayoutPoints() {
        return minimumExtraNetPerGameFromPiriGodRate()
                / GodEconomyTargets.BET_PER_GAME * 100.0;
    }
}
