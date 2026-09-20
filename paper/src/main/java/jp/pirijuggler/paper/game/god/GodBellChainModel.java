package jp.pirijuggler.paper.game.god;

/**
 * Exact finite-horizon bell-chain EV model for one GG set.
 *
 * A qualifying bell extends the visible consecutive-bell streak. Every bell that
 * lands with streak >= threshold is one V-stock opportunity with awardChance.
 * A non-bell resets the streak. No simulation is used here.
 */
public final class GodBellChainModel {
    public record Result(double expectedQualifyingBells, double expectedVStocks, double expectedNetMedals) {}

    private GodBellChainModel() {}

    public static Result evaluate(int games, double bellProbability, int threshold,
                                  double awardChance, double netMedalsPerStock) {
        if (games < 0) throw new IllegalArgumentException("games");
        probability(bellProbability, "bellProbability");
        if (threshold < 1) throw new IllegalArgumentException("threshold");
        probability(awardChance, "awardChance");
        if (!Double.isFinite(netMedalsPerStock) || netMedalsPerStock < 0)
            throw new IllegalArgumentException("netMedalsPerStock");

        // State is the current streak, capped at threshold. Once threshold is reached,
        // every further consecutive bell is another qualifying opportunity.
        double[] state = new double[threshold + 1];
        state[0] = 1.0;
        double qualifying = 0.0;

        for (int game = 0; game < games; game++) {
            double[] next = new double[threshold + 1];
            for (int streak = 0; streak <= threshold; streak++) {
                double mass = state[streak];
                if (mass == 0) continue;

                next[0] += mass * (1.0 - bellProbability);

                int bellStreak = Math.min(threshold, streak + 1);
                double bellMass = mass * bellProbability;
                next[bellStreak] += bellMass;
                if (streak + 1 >= threshold) qualifying += bellMass;
            }
            state = next;
        }

        double stocks = qualifying * awardChance;
        return new Result(qualifying, stocks, stocks * netMedalsPerStock);
    }

    /**
     * Solves the stock-award chance required to consume a requested EV budget
     * inside one GG set, for a fixed bell probability and streak threshold.
     */
    public static double solveAwardChance(int games, double bellProbability, int threshold,
                                          double targetNetMedalsPerSet, double netMedalsPerStock) {
        if (!Double.isFinite(targetNetMedalsPerSet) || targetNetMedalsPerSet < 0)
            throw new IllegalArgumentException("targetNetMedalsPerSet");
        if (!Double.isFinite(netMedalsPerStock) || netMedalsPerStock <= 0)
            throw new IllegalArgumentException("netMedalsPerStock");

        Result all = evaluate(games, bellProbability, threshold, 1.0, netMedalsPerStock);
        if (targetNetMedalsPerSet == 0) return 0.0;
        if (all.expectedNetMedals() <= 0 || targetNetMedalsPerSet > all.expectedNetMedals() + 1e-12)
            throw new IllegalArgumentException("Bell-chain EV budget is infeasible");

        return targetNetMedalsPerSet / all.expectedNetMedals();
    }

    private static void probability(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException(name);
    }
}
