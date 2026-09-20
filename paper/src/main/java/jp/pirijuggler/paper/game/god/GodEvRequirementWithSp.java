package jp.pirijuggler.paper.game.god;

/**
 * Adds the locked SP-role contribution to the per-setting positive-EV budget.
 *
 * This keeps SP explicit instead of burying it inside "other premium".
 */
public final class GodEvRequirementWithSp {
    public record Row(
            int setting,
            double requiredPositiveEvPerBaseGame,
            double godEvPerEligibleGame,
            double spEvPerEligibleGame,
            double remainingEvForNormalGgLoopSggZ
    ) {}

    private GodEvRequirementWithSp() {}

    public static Row forSetting(int setting, double expectedNetPerGod) {
        var base=GodEvRequirement.forSetting(setting,expectedNetPerGod);
        double sp=GodSpRoleBehavior.expectedNetContributionPerEligibleGame();
        return new Row(
                setting,
                base.requiredPositiveEvPerBaseGame(),
                base.godEvPerEligibleGame(),
                sp,
                base.remainingNonGodEvPerBaseGame()-sp
        );
    }
}
