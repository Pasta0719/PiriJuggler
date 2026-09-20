package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.session.Session;
import jp.pirijuggler.paper.machine.Machine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side machine game behavior seam.
 *
 * Engines now exchange machine-neutral GameTransition values. The Juggler adapter
 * maps its legacy NormalGame transition into this contract while GOD can implement
 * its own state machine without depending on Juggler bonus semantics.
 */
public interface GameEngine {
    GameTransition plan(Session before, Machine machine, PacketType action, long sequence,
                        long now, long receivedNanos, int ping, Integer clientPressedIndex);

    List<Envelope> committed(GameTransition action, long sentNanos);

    List<GameTransition.Scheduled> scheduled(GameTransition action);

    Optional<Envelope> resume(Session saved, long sentNanos);

    Session capture(Session saved, long now);

    void forget(UUID session);
}
