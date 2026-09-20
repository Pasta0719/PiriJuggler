package jp.pirijuggler.paper.game.god;

/**
 * Calibrates the unknown "strong loop stock" by published aggregate expectation
 * without inventing an unpublished loop-rate distribution.
 */
public final class GodPremiumCalibration {
    public static final double PUBLISHED_EXPECTED_MEDALS_FLOOR = 3000.0;

    public record Result(
            double guaranteedNetMedals,
            double targetExpectedNetMedals,
            double requiredAdditionalNetMedals,
            double impliedAdditionalGgEquivalents
    ) {}

    private GodPremiumCalibration() {}

    public static Result toExpectedNetMedals(double targetExpectedNetMedals) {
        if (!Double.isFinite(targetExpectedNetMedals)
                || targetExpectedNetMedals < GodPremiumImpact.guaranteedNetPerGodBeforeLoop()) {
            throw new IllegalArgumentException("targetExpectedNetMedals");
        }
        double guaranteed=GodPremiumImpact.guaranteedNetPerGodBeforeLoop();
        double additional=targetExpectedNetMedals-guaranteed;
        return new Result(
                guaranteed,
                targetExpectedNetMedals,
                additional,
                additional/GodEconomyTargets.ggSetNetMedals());
    }

    public static Result publishedFloor() {
        return toExpectedNetMedals(PUBLISHED_EXPECTED_MEDALS_FLOOR);
    }

    /**
     * Extra payout percentage points caused by Piri's doubled GOD frequency,
     * calibrated to a chosen average GOD value.
     */
    public static double extraPayoutPointsFromRateOverride(double expectedNetMedalsPerGod) {
        if (!Double.isFinite(expectedNetMedalsPerGod) || expectedNetMedalsPerGod < 0)
            throw new IllegalArgumentException("expectedNetMedalsPerGod");
        double deltaRate=1.0/GodKisekiBaseline.PIRI_GOD_DENOMINATOR
                - 1.0/GodKisekiBaseline.KISEKI_GOD_DENOMINATOR;
        return expectedNetMedalsPerGod*deltaRate/GodEconomyTargets.BET_PER_GAME*100.0;
    }
}
