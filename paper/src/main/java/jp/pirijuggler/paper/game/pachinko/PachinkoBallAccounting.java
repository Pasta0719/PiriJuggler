package jp.pirijuggler.paper.game.pachinko;

/** Immutable ball accounting. Server-owned; no visual/client balance authority. */
public final class PachinkoBallAccounting {
    private PachinkoBallAccounting() {}

    public static PachinkoRuntime lend(PachinkoRuntime r, long now) {
        return copy(r, Math.addExact(r.ballsHeld(), PachinkoSpec.BALLS_PER_1000_YEN),
                Math.addExact(r.ballsLoaned(), PachinkoSpec.BALLS_PER_1000_YEN),
                r.totalFired(), r.totalStarts(), r.ballSequenceId(), now);
    }

    public static PachinkoRuntime fire(PachinkoRuntime r, long now) {
        if (r.ballsHeld() < 1 || r.mode() != PachinkoRuntime.Mode.NORMAL ||
                r.presentation() != PachinkoRuntime.Presentation.IDLE)
            throw new IllegalStateException("Cannot fire in current pachinko state");
        return copy(r, r.ballsHeld() - 1, r.ballsLoaned(),
                Math.addExact(r.totalFired(), 1), r.totalStarts(),
                Math.addExact(r.ballSequenceId(), 1), now);
    }

    /** Invoke only for a server-validated physical start entry, never from an untrusted client claim. */
    public static PachinkoRuntime validStart(PachinkoRuntime r, long now) {
        if (r.totalStarts() >= r.totalFired())
            throw new IllegalStateException("Start count cannot exceed fired balls");
        return copy(r, Math.addExact(r.ballsHeld(), PachinkoSpec.START_PRIZE_BALLS),
                r.ballsLoaned(), r.totalFired(), Math.addExact(r.totalStarts(), 1),
                r.ballSequenceId(), now);
    }

    private static PachinkoRuntime copy(PachinkoRuntime r, long held, long loaned,
                                        long fired, long starts, long sequence, long now) {
        return new PachinkoRuntime(r.mode(), held, loaned, fired, starts, sequence,
                r.presentation(), r.initialHitCommitted(), r.initialOutcome(),
                r.rushActive(), r.rushWins(), r.rightOutcome(), r.currentPayout(), r.cumulativePayout(), r.statistics(), now);
    }
}
