package jp.pirijuggler.paper.game.god;

/**
 * Published Kamigami no Kiseki reference values used as the Piri GOD gameplay baseline.
 * Piri deliberately overrides only GOD probability to 1/8192 at the baseline-design level.
 */
public final class GodKisekiBaseline {
    private static final double[] AT_INITIAL_ODDS = {533.0, 420.0, 496.0, 338.0, 455.0, 295.0};

    public static final int GG_GAMES = 50;
    public static final double GG_PURE_INCREASE_PER_GAME = 7.0;
    public static final int G_ZONE_MAX_GAMES = 5;

    public static final int KISEKI_GOD_DENOMINATOR = 16384;
    public static final int PIRI_GOD_DENOMINATOR = 8192;
    public static final int RED7_DENOMINATOR = 6900;

    public static final int SGG_MIN_GAMES = 10;
    public static final int SGG_MAX_GAMES = 100;
    public static final double SGG_MIN_CONTINUATION = 0.75;

    private GodKisekiBaseline() {}

    public static double atInitialOdds(int setting) {
        if (setting < 1 || setting > 6) throw new IllegalArgumentException("setting");
        return AT_INITIAL_ODDS[setting - 1];
    }

    public static double atInitialRate(int setting) {
        return 1.0 / atInitialOdds(setting);
    }
}
