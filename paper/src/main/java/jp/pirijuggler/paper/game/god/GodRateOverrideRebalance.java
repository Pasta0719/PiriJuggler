package jp.pirijuggler.paper.game.god;

/**
 * Rebalances the deliberate GOD-rate override without inventing unpublished
 * Kiseki internals.
 *
 * If every non-GOD contribution is held constant except normal GG initial hits,
 * the exact reduction in GG-initial rate is:
 *
 *   extraGodNetPerGame / expectedNetPerNormalGgInitial
 *
 * This class intentionally leaves the average value of one normal GG initial as
 * an input until the full GG/loop model is solved.
 */
public final class GodRateOverrideRebalance {
    public record Result(
            double originalInitialRate,
            double adjustedInitialRate,
            double originalInitialOdds,
            double adjustedInitialOdds,
            double removedInitialRatePerGame,
            double extraGodNetPerGame
    ) {}

    private GodRateOverrideRebalance() {}

    public static Result absorbEntireGodDeltaInNormalGg(
            double originalInitialOdds,
            double expectedNetPerNormalGgInitial,
            double expectedNetPerGod
    ) {
        if (!Double.isFinite(originalInitialOdds) || originalInitialOdds <= 0)
            throw new IllegalArgumentException("originalInitialOdds");
        if (!Double.isFinite(expectedNetPerNormalGgInitial) || expectedNetPerNormalGgInitial <= 0)
            throw new IllegalArgumentException("expectedNetPerNormalGgInitial");
        if (!Double.isFinite(expectedNetPerGod) || expectedNetPerGod < 0)
            throw new IllegalArgumentException("expectedNetPerGod");

        double originalRate=1.0/originalInitialOdds;
        double deltaGodRate=1.0/GodKisekiBaseline.PIRI_GOD_DENOMINATOR
                - 1.0/GodKisekiBaseline.KISEKI_GOD_DENOMINATOR;
        double extraGodNetPerGame=expectedNetPerGod*deltaGodRate;
        double removedRate=extraGodNetPerGame/expectedNetPerNormalGgInitial;
        double adjustedRate=originalRate-removedRate;

        if (!(adjustedRate>0.0) || adjustedRate>=1.0)
            throw new IllegalArgumentException("Normal GG alone cannot absorb GOD EV delta");

        return new Result(
                originalRate,
                adjustedRate,
                originalInitialOdds,
                1.0/adjustedRate,
                removedRate,
                extraGodNetPerGame
        );
    }

    public static Result forSetting(
            int setting,
            double expectedNetPerNormalGgInitial,
            double expectedNetPerGod
    ) {
        return absorbEntireGodDeltaInNormalGg(
                GodKisekiBaseline.atInitialOdds(setting),
                expectedNetPerNormalGgInitial,
                expectedNetPerGod
        );
    }
}
