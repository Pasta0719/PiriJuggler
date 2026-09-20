package jp.pirijuggler.paper.game.god;

/**
 * Solves the setting-specific normal-GG initial hit rate from an explicit EV budget.
 *
 * The important constraint is that presentation probabilities are not chosen first:
 * once base-game loss and premium-path EV are fixed, the initial GG rate is the
 * remaining degree of freedom required to hit the target payout.
 */
public final class GodParameterFitter {
    public record Inputs(
            double baseNetPerGame,
            double expectedNetPerNormalGgInitial,
            double expectedNetPerGod,
            double sggNetPerGame,
            double bellVNetPerGame,
            double loopStockNetPerGame,
            double otherPremiumNetPerGame
    ) {
        public Inputs {
            finite(baseNetPerGame, "baseNetPerGame");
            positive(expectedNetPerNormalGgInitial, "expectedNetPerNormalGgInitial");
            nonNegative(expectedNetPerGod, "expectedNetPerGod");
            nonNegative(sggNetPerGame, "sggNetPerGame");
            nonNegative(bellVNetPerGame, "bellVNetPerGame");
            nonNegative(loopStockNetPerGame, "loopStockNetPerGame");
            nonNegative(otherPremiumNetPerGame, "otherPremiumNetPerGame");
        }
    }

    public record Result(
            int setting,
            double targetPayoutPercent,
            double targetNetPerGame,
            double fixedNetPerGame,
            double normalGgInitialRatePerGame,
            double normalGgInitialOdds
    ) {}

    private GodParameterFitter() {}

    public static Result fit(int setting, Inputs in) {
        double target=GodEconomyTargets.targetNetPerGame(setting);
        double god=GodEconomyTargets.godNetContributionPerGame(in.expectedNetPerGod());
        double fixed=in.baseNetPerGame()+god+in.sggNetPerGame()+in.bellVNetPerGame()
                +in.loopStockNetPerGame()+in.otherPremiumNetPerGame();
        double needed=target-fixed;
        double rate=needed/in.expectedNetPerNormalGgInitial();
        if(!Double.isFinite(rate)||rate<=0||rate>1)
            throw new IllegalArgumentException("No feasible normal GG rate for setting "+setting);
        return new Result(setting,GodEconomyTargets.payoutPercent(setting),target,fixed,rate,1.0/rate);
    }

    private static void finite(double value,String name){
        if(!Double.isFinite(value))throw new IllegalArgumentException(name);
    }
    private static void positive(double value,String name){
        if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException(name);
    }
    private static void nonNegative(double value,String name){
        if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException(name);
    }
}
