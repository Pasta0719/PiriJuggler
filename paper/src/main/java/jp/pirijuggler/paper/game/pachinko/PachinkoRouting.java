package jp.pirijuggler.paper.game.pachinko;

import java.util.random.RandomGenerator;

/**
 * Server-side physical routing model. Each fired ball is evaluated once.
 * The target describes observed starts per 250 fired balls, not jackpot odds.
 */
public final class PachinkoRouting {
    private final double startProbability;

    public PachinkoRouting(double targetSpinsPer1000Yen) {
        if (!Double.isFinite(targetSpinsPer1000Yen) ||
                targetSpinsPer1000Yen < 0 ||
                targetSpinsPer1000Yen > PachinkoSpec.BALLS_PER_1000_YEN) {
            throw new IllegalArgumentException("Invalid start routing target");
        }
        startProbability = targetSpinsPer1000Yen / PachinkoSpec.BALLS_PER_1000_YEN;
    }

    public static PachinkoRouting reference() {
        return new PachinkoRouting(PachinkoSpec.REFERENCE_SPINS_PER_1000_YEN);
    }

    public boolean entersStart(RandomGenerator random) {
        return random.nextDouble() < startProbability;
    }

    public double expectedSpinsPer1000Yen() {
        return startProbability * PachinkoSpec.BALLS_PER_1000_YEN;
    }
}
