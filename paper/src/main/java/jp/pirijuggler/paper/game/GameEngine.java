package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.session.Session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side machine game behavior seam.
 *
 * The first implementation is the existing Juggler behavior. New machine families
 * (Okidoki/GOD/Disc) can implement this contract without putting their state machine
 * inside MachineService.
 *
 * Transition/Scheduled remain NormalGame types for this first compatibility step so
 * the existing durable GameStore transaction format is unchanged.
 */
public interface GameEngine {
    NormalGame.Transition plan(Session before, PacketType action, long sequence, int setting,
                               long now, long receivedNanos, int ping, Integer clientPressedIndex);

    List<Envelope> committed(NormalGame.Transition action, long sentNanos);

    List<NormalGame.Scheduled> scheduled(NormalGame.Transition action);

    Optional<Envelope> resume(Session saved, long sentNanos);

    Session capture(Session saved, long now);

    void forget(UUID session);
}
