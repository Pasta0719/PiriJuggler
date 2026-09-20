package jp.pirijuggler.paper.game.god;

/**
 * Locked gameplay invariants for Piri GOD v1.
 *
 * These are production rules, not provisional economy-calibration knobs.
 * Payout fitting must not weaken these values.
 */
public final class GodProductionSpec {
    public static final int GOD_DENOMINATOR = 8192;
    public static final int RED7_DENOMINATOR = 6900;
    public static final int SP_DENOMINATOR = 65536;

    public static final int GG_GAMES = 50;
    public static final double GG_PURE_INCREASE_PER_GAME = 7.0;
    public static final int G_ZONE_MAX_GAMES = 5;

    public static final int NORMAL_CEILING_GAMES = 1480;
    public static final int RESET_CEILING_510_GAMES = 510;
    public static final int RESET_CEILING_1000_GAMES = 1000;
    public static final int RESET_CEILING_1480_GAMES = 1480;
    public static final double RESET_CEILING_510_RATE = 0.152;
    public static final double RESET_CEILING_1000_RATE = 0.203;
    public static final double RESET_CEILING_1480_RATE = 0.645;

    public static final int GOD_GUARANTEED_GG_SETS = 4;
    public static final GodLoopType GOD_LOOP = GodLoopType.D;

    public static final double SP_STOCK_HIT_RATE_NORMAL_OR_GG = 0.50;
    public static final GodLoopType SP_LOOP_ON_HIT = GodLoopType.D;

    public static final int Z_ZONE_BASE_GAMES = 5;
    public static final double Z_ZONE_YELLOW7_DENOMINATOR = 1.4;
    public static final int Z_ZONE_REQUIRED_YELLOW7_STREAK = 5;
    public static final double Z_ZONE_ZERO_YELLOW_SPECIAL_D_RATE = 0.50;

    public static final boolean GG_BELL_STREAK_V_STOCK_ENABLED = false;

    private GodProductionSpec() {}

    public static double ggSetNetMedals() {
        return GG_GAMES * GG_PURE_INCREASE_PER_GAME;
    }

    public static int chooseResetCeiling(double unit) {
        if (!(unit >= 0.0 && unit < 1.0)) throw new IllegalArgumentException("unit");
        if (unit < RESET_CEILING_510_RATE) return RESET_CEILING_510_GAMES;
        if (unit < RESET_CEILING_510_RATE + RESET_CEILING_1000_RATE) return RESET_CEILING_1000_GAMES;
        return RESET_CEILING_1480_GAMES;
    }
}
