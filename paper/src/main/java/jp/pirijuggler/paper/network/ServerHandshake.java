package jp.pirijuggler.paper.network;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.EnvelopeCodec;
import jp.pirijuggler.common.protocol.Handshake;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.protocol.Protocol;
import jp.pirijuggler.common.protocol.ProtocolException;
import jp.pirijuggler.common.protocol.ErrorCode;
import jp.pirijuggler.common.protocol.ErrorPackets;
import jp.pirijuggler.paper.threading.MainThread;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ServerHandshake {
    private final MainThread mainThread;
    private final Set<UUID> compatiblePlayers = new HashSet<>();

    public ServerHandshake(MainThread mainThread) { this.mainThread = mainThread; }

    public Optional<byte[]> receive(UUID player, byte[] message) {
        mainThread.requireMainThread();
        Envelope packet;
        try { packet = EnvelopeCodec.decode(message); }
        catch (ProtocolException exception) { compatiblePlayers.remove(player); return Optional.empty(); }
        if (packet.protocol() != Protocol.VERSION) {
            compatiblePlayers.remove(player);
            return mismatch();
        }
        if (!packet.packetType().isClientToServer() || packet.packetType().isReserved()) {
            compatiblePlayers.remove(player);
            return Optional.empty();
        }
        if (packet.packetType() != PacketType.HELLO)
            return compatiblePlayers.contains(player) ? Optional.empty() : mismatch();
        if (!Handshake.validHello(packet)) { compatiblePlayers.remove(player); return mismatch(); }
        compatiblePlayers.add(player);
        return Optional.of(EnvelopeCodec.encode(Handshake.acknowledgement()));
    }

    private static Optional<byte[]> mismatch() {
        return Optional.of(EnvelopeCodec.encode(ErrorPackets.error(ErrorCode.PROTOCOL_MISMATCH)));
    }

    public boolean canUseSlot(UUID player) { mainThread.requireMainThread(); return compatiblePlayers.contains(player); }
    public void disconnect(UUID player) { mainThread.requireMainThread(); compatiblePlayers.remove(player); }
    public void clear() { mainThread.requireMainThread(); compatiblePlayers.clear(); }
}
