package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.session.Session;
import jp.pirijuggler.paper.machine.Machine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Compatibility adapter for the current Piri Juggler game.
 *
 * NormalGame remains unchanged internally. This adapter translates its legacy
 * transition shape to the machine-neutral GameTransition contract.
 */
public final class JugglerGameEngine implements GameEngine {
    private final NormalGame delegate;

    public JugglerGameEngine(NormalGame delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public GameTransition plan(Session before, Machine machine, PacketType action, long sequence,
                               long now, long receivedNanos, int ping, Integer clientPressedIndex) {
        return fromLegacy(delegate.plan(before, action, sequence, machine.setting(), now, receivedNanos, ping, clientPressedIndex));
    }

    @Override
    public List<Envelope> committed(GameTransition action, long sentNanos) {
        return delegate.committed(toLegacy(action), sentNanos);
    }

    @Override
    public List<GameTransition.Scheduled> scheduled(GameTransition action) {
        return delegate.scheduled(toLegacy(action)).stream()
                .map(event -> new GameTransition.Scheduled(event.delayMs(), event.packet()))
                .toList();
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

    private static GameTransition fromLegacy(NormalGame.Transition action) {
        return new GameTransition(
                action.transaction(), action.before(), action.after(), action.bet(), action.payout(),
                action.normalSpins(), action.finished(), action.lever(), action.bonusStarted(),
                action.bonusEnded(), action.publicDelayMs(), action.packets(), action.afterStart(),
                action.scheduled().stream()
                        .map(event -> new GameTransition.Scheduled(event.delayMs(), event.packet()))
                        .toList(),
                null
        );
    }

    private static NormalGame.Transition toLegacy(GameTransition action) {
        return new NormalGame.Transition(
                action.transaction(), action.before(), action.after(), action.bet(), action.payout(),
                action.normalSpins(), action.finished(), action.lever(), action.bonusStarted(),
                action.bonusEnded(), action.publicDelayMs(), action.packets(), action.afterStart(),
                action.scheduled().stream()
                        .map(event -> new NormalGame.Scheduled(event.delayMs(), event.packet()))
                        .toList()
        );
    }
}
