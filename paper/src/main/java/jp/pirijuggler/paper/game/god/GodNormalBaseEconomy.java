package jp.pirijuggler.paper.game.god;

/**
 * Published normal-play coin-base reference from Kamigami no Kiseki.
 *
 * 50 medals ~= 30.8 normal games (setting 1 reference).
 * This is converted into net medal loss per normal game without pretending
 * that it is the complete machine payout.
 */
public final class GodNormalBaseEconomy {
    public static final double REFERENCE_MEDALS = 50.0;
    public static final double REFERENCE_GAMES_SETTING1 = 30.8;

    private GodNormalBaseEconomy() {}

    /** Average net medals consumed by one normal game at the published base. */
    public static double netLossPerNormalGameSetting1() {
        return REFERENCE_MEDALS / REFERENCE_GAMES_SETTING1;
    }

    /** Average gross payout/replay value returned inside normal play itself. */
    public static double grossReturnPerNormalGameSetting1() {
        return GodEconomyTargets.BET_PER_GAME - netLossPerNormalGameSetting1();
    }

    /** Normal-play gross return ratio only; this is NOT whole-machine payout. */
    public static double normalPlayReturnPercentSetting1() {
        return grossReturnPerNormalGameSetting1() / GodEconomyTargets.BET_PER_GAME * 100.0;
    }
}
