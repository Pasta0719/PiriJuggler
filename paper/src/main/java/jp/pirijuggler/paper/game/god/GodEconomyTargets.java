package jp.pirijuggler.paper.game.god;

/**
 * Fixed economic invariants already chosen for Piri GOD.
 *
 * The six-setting payout curve is intentionally NOT stored here. It must be
 * supplied explicitly through GodPayoutCurve so a reference benchmark cannot
 * accidentally become the production target.
 */
public final class GodEconomyTargets {
    public static final int GG_GAMES = 50;
    public static final double GG_PURE_INCREASE_PER_GAME = 7.0;
    public static final int GOD_DENOMINATOR = 8192;
    public static final double BET_PER_GAME = 3.0;

    private GodEconomyTargets() {}

    public static double ggSetNetMedals() {
        return GG_GAMES * GG_PURE_INCREASE_PER_GAME;
    }

    public static double godNetContributionPerGame(double expectedNetMedalsPerGod) {
        if (!Double.isFinite(expectedNetMedalsPerGod) || expectedNetMedalsPerGod < 0)
            throw new IllegalArgumentException("expectedNetMedalsPerGod");
        return expectedNetMedalsPerGod / GOD_DENOMINATOR;
    }

    public static double residualNetBudgetPerGame(
            GodPayoutCurve targetCurve,
            int setting,
            double expectedNetMedalsPerGod
    ) {
        if (targetCurve == null) throw new IllegalArgumentException("targetCurve");
        return targetCurve.targetNetPerGame(setting)
                - godNetContributionPerGame(expectedNetMedalsPerGod);
    }
}
