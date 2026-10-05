package jp.pirijuggler.paper.game.pachinko;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Presentation-only RUSH policy. Locked payout economics remain in {@link PachinkoSpec}.
 *
 * Selected machine mode is a probability loop:
 * P(continue) = loopContinuation = 0.81 exactly.
 * Other models exist only as presentation/state-machine strategies and must expose
 * their effective continuation explicitly before they can be selected.
 */
public sealed interface PachinkoRushPresentation permits PachinkoRushPresentation.FiniteSt, PachinkoRushPresentation.ProbabilityLoop, PachinkoRushPresentation.LtChallenge {
    double effectiveContinuation();
    boolean continues(RandomGenerator random);

    record FiniteSt(int attempts,double hitPerAttempt) implements PachinkoRushPresentation {
        public FiniteSt { if(attempts<1||hitPerAttempt<0||hitPerAttempt>1)throw new IllegalArgumentException(); }
        @Override public double effectiveContinuation(){return 1.0-Math.pow(1.0-hitPerAttempt,attempts);}
        @Override public boolean continues(RandomGenerator random){for(int i=0;i<attempts;i++)if(random.nextDouble()<hitPerAttempt)return true;return false;}
    }

    record ProbabilityLoop(double continuation) implements PachinkoRushPresentation {
        public ProbabilityLoop { if(continuation<0||continuation>1)throw new IllegalArgumentException(); }
        @Override public double effectiveContinuation(){return continuation;}
        @Override public boolean continues(RandomGenerator random){return random.nextDouble()<continuation;}
    }

    /**
     * Base challenge followed, on failure, by an LT-style promotion challenge.
     * Effective continuation = base + (1-base) * promotion * promotedContinuation.
     */
    record LtChallenge(double base,double promotion,double promotedContinuation) implements PachinkoRushPresentation {
        public LtChallenge { if(base<0||base>1||promotion<0||promotion>1||promotedContinuation<0||promotedContinuation>1)throw new IllegalArgumentException(); }
        @Override public double effectiveContinuation(){return base+(1.0-base)*promotion*promotedContinuation;}
        @Override public boolean continues(RandomGenerator random){
            if(random.nextDouble()<base)return true;
            return random.nextDouble()<promotion && random.nextDouble()<promotedContinuation;
        }
    }

    PachinkoRushPresentation SELECTED = new ProbabilityLoop(PachinkoSpec.RUSH_CONTINUATION_RATE);

    static PachinkoRushPresentation selected(){return SELECTED;}
    static boolean selectedContinues(RandomGenerator random){return Objects.requireNonNull(SELECTED).continues(random);}
}
