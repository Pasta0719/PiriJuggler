package jp.pirijuggler.paper.game.god;

/**
 * Converts an added premium trigger into long-run payout using both reward and
 * added play time. Unlike the earlier quick lower-bound arithmetic, this model
 * does not pretend the premium reward is instantaneous.
 *
 * Inputs are expressed as an average cycle:
 * - baseGames: games in the baseline machine cycle
 * - baseBet/basePayout: total medals wagered/paid in that cycle
 * - extraTriggerRatePerEligibleGame: added trigger probability caused by the override
 * - eligibleGames: games in the cycle on which the premium can occur
 * - premiumAddedGames: extra games caused by one premium
 * - premiumBetPerAddedGame/premiumPayoutPerAddedGame: gross accounting in those games
 *
 * This is still an expectation model, but it preserves the payout denominator.
 */
public final class GodRateOverrideCycleAccounting {
    public record Result(
            double baselinePayoutPercent,
            double adjustedPayoutPercent,
            double addedTriggersPerCycle,
            double addedBetPerCycle,
            double addedPayoutPerCycle,
            double addedGamesPerCycle
    ) {}

    private GodRateOverrideCycleAccounting() {}

    public static Result evaluate(
            double baseGames,
            double baseBet,
            double basePayout,
            double eligibleGames,
            double extraTriggerRatePerEligibleGame,
            double premiumAddedGames,
            double premiumBetPerAddedGame,
            double premiumPayoutPerAddedGame
    ) {
        positive(baseGames,"baseGames");
        positive(baseBet,"baseBet");
        nonNegative(basePayout,"basePayout");
        nonNegative(eligibleGames,"eligibleGames");
        probability(extraTriggerRatePerEligibleGame,"extraTriggerRatePerEligibleGame");
        nonNegative(premiumAddedGames,"premiumAddedGames");
        nonNegative(premiumBetPerAddedGame,"premiumBetPerAddedGame");
        nonNegative(premiumPayoutPerAddedGame,"premiumPayoutPerAddedGame");

        double triggers=eligibleGames*extraTriggerRatePerEligibleGame;
        double addedGames=triggers*premiumAddedGames;
        double addedBet=addedGames*premiumBetPerAddedGame;
        double addedPayout=addedGames*premiumPayoutPerAddedGame;

        return new Result(
                basePayout/baseBet*100.0,
                (basePayout+addedPayout)/(baseBet+addedBet)*100.0,
                triggers,
                addedBet,
                addedPayout,
                addedGames
        );
    }

    private static void positive(double value,String name){
        if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException(name);
    }
    private static void nonNegative(double value,String name){
        if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException(name);
    }
    private static void probability(double value,String name){
        if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException(name);
    }
}
