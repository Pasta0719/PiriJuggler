package jp.pirijuggler.paper.game.god;

/**
 * First locked economic targets for the GOD-family design.
 *
 * The payout targets follow Million God: Kamigami no Kiseki as the starting
 * benchmark. Presentation and exact gameplay can diverge, but all later
 * parameters must reconcile back to these long-run targets.
 */
public final class GodEconomyTargets {
    private static final double[] PAYOUT_PERCENT = {97.2, 99.1, 102.1, 106.9, 111.7, 114.6};

    public static final int GG_GAMES = 50;
    public static final double GG_PURE_INCREASE_PER_GAME = 7.0;
    public static final int GOD_DENOMINATOR = 8192;
    public static final double BET_PER_GAME = 3.0;

    private GodEconomyTargets() {}

    public static double payoutPercent(int setting) {
        if (setting < 1 || setting > 6) throw new IllegalArgumentException("setting");
        return PAYOUT_PERCENT[setting - 1];
    }

    public static double ggSetNetMedals() {
        return GG_GAMES * GG_PURE_INCREASE_PER_GAME;
    }

    public static double targetNetPerGame(int setting) {
        return BET_PER_GAME * (payoutPercent(setting) / 100.0 - 1.0);
    }

    public static double godNetContributionPerGame(double expectedNetMedalsPerGod) {
        if (!Double.isFinite(expectedNetMedalsPerGod) || expectedNetMedalsPerGod < 0)
            throw new IllegalArgumentException("expectedNetMedalsPerGod");
        return expectedNetMedalsPerGod / GOD_DENOMINATOR;
    }

    public static double residualNetBudgetPerGame(int setting, double expectedNetMedalsPerGod) {
        return targetNetPerGame(setting) - godNetContributionPerGame(expectedNetMedalsPerGod);
    }
}
