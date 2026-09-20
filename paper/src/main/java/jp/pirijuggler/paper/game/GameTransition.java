package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.paper.session.Session;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable machine-neutral gameplay transition.
 *
 * The statistics fields retain the existing database accounting contract while
 * removing NormalGame types from GameEngine/GameStore. Machine-specific engines
 * may leave bonusStarted null/bonusEnded false when those Juggler counters do not
 * apply.
 */
public record GameTransition(
        UUID transaction,
        Session before,
        Session after,
        int bet,
        int payout,
        int normalSpins,
        boolean finished,
        boolean lever,
        String bonusStarted,
        boolean bonusEnded,
        long publicDelayMs,
        List<Envelope> packets,
        List<Envelope> afterStart,
        List<Scheduled> scheduled
) {
    public record Scheduled(long delayMs, Envelope packet) {
        public Scheduled {
            if (delayMs < 0) throw new IllegalArgumentException("delay");
            Objects.requireNonNull(packet);
        }
    }

    public GameTransition {
        Objects.requireNonNull(transaction);
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        if (publicDelayMs < 0) throw new IllegalArgumentException("public delay");
        packets = List.copyOf(packets);
        afterStart = List.copyOf(afterStart);
        scheduled = List.copyOf(scheduled);
    }
}
