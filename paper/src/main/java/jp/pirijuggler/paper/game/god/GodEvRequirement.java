package jp.pirijuggler.paper.game.god;

/**
 * Converts the locked payout curve and normal-play coin base into the amount of
 * positive EV that must be supplied by GG / GOD / SGG / loop / Z routes.
 *
 * This is a normal-spin budget anchor, not the final whole-machine accounting.
 * Final payout verification still uses the full state/cycle simulator because
 * AT adds both games and wager/payout volume.
 */
public final class GodEvRequirement {
    public record Row(
            int setting,
            double targetPayoutPercent,
            double targetNetPerBaseGame,
            double normalBaseNetPerGame,
            double requiredPositiveEvPerBaseGame,
            double godEvPerEligibleGame,
            double remainingNonGodEvPerBaseGame
    ) {}

    private GodEvRequirement() {}

    public static Row forSetting(int setting, double expectedNetPerGod) {
        double targetNet=GodProductionTarget.curve().targetNetPerGame(setting);
        double normalBaseNet=-GodNormalBaseEconomy.netLossPerNormalGameSetting1();
        double required=targetNet-normalBaseNet;
        double god=GodEconomyTargets.godNetContributionPerGame(expectedNetPerGod);
        return new Row(
                setting,
                GodProductionTarget.curve().payoutPercent(setting),
                targetNet,
                normalBaseNet,
                required,
                god,
                required-god
        );
    }
}
