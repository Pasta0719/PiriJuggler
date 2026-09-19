package jp.pirijuggler.paper.network;

import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.paper.threading.MainThread;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ServerHandshakeTest {
    private final MainThread main = new MainThread() {
        private final Thread owner = Thread.currentThread();
        public boolean isMainThread() { return Thread.currentThread() == owner; }
        public void execute(Runnable command) { requireMainThread(); command.run(); }
    };

    @Test void requiresHandshakeAndResetsOnDisconnect() {
        ServerHandshake server = new ServerHandshake(main);
        UUID player = UUID.randomUUID();
        assertFalse(server.canUseSlot(player));
        byte[] ack = server.receive(player, EnvelopeCodec.encode(Handshake.hello())).orElseThrow();
        assertEquals(Handshake.acknowledgement(), EnvelopeCodec.decode(ack));
        assertTrue(server.canUseSlot(player));
        assertFalse(server.canUseSlot(UUID.randomUUID()));
        server.disconnect(player);
        assertFalse(server.canUseSlot(player));
    }

    @Test void eitherProtocolMismatchRevokesSlotAccess() {
        for (boolean envelopeMismatch : new boolean[]{true, false}) {
            ServerHandshake server = new ServerHandshake(main);
            UUID player = UUID.randomUUID();
            server.receive(player, EnvelopeCodec.encode(Handshake.hello()));
            var payload = Handshake.hello().payload();
            if (!envelopeMismatch) payload.addProperty("protocol", Protocol.VERSION + 1);
            Envelope wrong = new Envelope(envelopeMismatch ? Protocol.VERSION + 1 : Protocol.VERSION, PacketType.HELLO, payload);
            assertEquals(ErrorPackets.error(ErrorCode.PROTOCOL_MISMATCH), EnvelopeCodec.decode(server.receive(player, EnvelopeCodec.encode(wrong)).orElseThrow()));
            assertFalse(server.canUseSlot(player));
        }
    }

    @Test void spoofedDirectionReservedPacketAndMalformedJsonCannotAuthorizePlayer() {
        ServerHandshake server = new ServerHandshake(main);
        UUID player = UUID.randomUUID();
        for (PacketType type : new PacketType[]{PacketType.HELLO_ACK, PacketType.RESERVED_2, PacketType.RESERVED_11, PacketType.SPACE_ACTION}) {
            byte[] bytes = EnvelopeCodec.encode(Handshake.hello());
            bytes[6] = (byte) type.id();
            var response = server.receive(player, bytes);
            if (type == PacketType.SPACE_ACTION) assertEquals(ErrorPackets.error(ErrorCode.PROTOCOL_MISMATCH), EnvelopeCodec.decode(response.orElseThrow()));
            else assertTrue(response.isEmpty());
            assertFalse(server.canUseSlot(player));
        }
        assertTrue(server.receive(player, new byte[]{1, 2, 3}).isEmpty());
        assertFalse(server.canUseSlot(player));
        var payload = Handshake.hello().payload(); payload.addProperty("protocol", Integer.toString(Protocol.VERSION));
        assertEquals(ErrorPackets.error(ErrorCode.PROTOCOL_MISMATCH), EnvelopeCodec.decode(server.receive(player, EnvelopeCodec.encode(Envelope.current(PacketType.HELLO, payload))).orElseThrow()));
    }

    @Test void displayVersionDoesNotOverrideProtocolCompatibility() {
        var payload = Handshake.hello().payload(); payload.addProperty("modVersion", "1.0.1");
        ServerHandshake server = new ServerHandshake(main);
        UUID player = UUID.randomUUID();
        assertTrue(server.receive(player, EnvelopeCodec.encode(Envelope.current(PacketType.HELLO, payload))).isPresent());
        assertTrue(server.canUseSlot(player));
        server.clear();
        assertFalse(server.canUseSlot(player));
    }
}
