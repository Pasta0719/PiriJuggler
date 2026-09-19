package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.session.Session;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Compatibility adapter for the current Piri Juggler game.
 *
 * Deliberately contains no new game rules: it delegates 1:1 to NormalGame so the
 * multi-machine refactor can be introduced without changing current runtime behavior.
 */
public final class JugglerGameEngine implements GameEngine {
    private final NormalGame delegate;

    public JugglerGameEngine(NormalGame delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public NormalGame.Transition plan(Session before, PacketType action, long sequence, int setting,
                                      long now, long receivedNanos, int ping, Integer clientPressedIndex) {
        return delegate.plan(before, action, sequence, setting, now, receivedNanos, ping, clientPressedIndex);
    }

    @Override
    public List<Envelope> committed(NormalGame.Transition action, long sentNanos) {
        return delegate.committed(action, sentNanos);
    }

    @Override
    public List<NormalGame.Scheduled> scheduled(NormalGame.Transition action) {
        return delegate.scheduled(action);
    }

    @Override
    public Optional<Envelope> resume(Session saved, long sentNanos) {
        return delegate.resume(saved, sentNanos);
    }

    @Override
    public Session capture(Session saved, long now) {
        return delegate.capture(saved, now);
    }

    @Override
    public void forget(UUID session) {
        delegate.forget(session);
    }
}
