package jp.pirijuggler.paper.game.god;

/**
 * Locked Piri GOD behavior for the rare SP role.
 *
 * Baseline follows Kamigami no Kiseki:
 * - SP role odds: 1/65536
 * - in normal/GG contexts: 50% grants one GG stock
 * - that granted stock carries an 80% loop
 */
public final class GodSpRoleBehavior {
    public static final double STOCK_AWARD_CHANCE = 0.50;
    public static final double LOOP_CONTINUATION = 0.80;

    private GodSpRoleBehavior() {}

    public static double expectedDirectStocks() {
        return STOCK_AWARD_CHANCE;
    }

    public static double expectedLoopStocksConditionalOnAward() {
        return GodLoopStockModel.expectedAdditionalStocks(LOOP_CONTINUATION);
    }

    public static double expectedTotalStocksPerSp() {
        return STOCK_AWARD_CHANCE * (1.0 + expectedLoopStocksConditionalOnAward());
    }

    public static double expectedNetMedalsPerSp() {
        return expectedTotalStocksPerSp() * GodEconomyTargets.ggSetNetMedals();
    }

    public static double expectedNetContributionPerEligibleGame() {
        return expectedNetMedalsPerSp() * GodKisekiRoleTable.piriProbability(GodRole.SP);
    }
}
