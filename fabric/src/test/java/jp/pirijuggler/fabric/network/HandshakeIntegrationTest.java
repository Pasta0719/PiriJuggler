package jp.pirijuggler.fabric.network;

import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.fabric.ErrorMessages;
import jp.pirijuggler.paper.network.ServerHandshake;
import jp.pirijuggler.paper.threading.MainThread;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeIntegrationTest {
    @Test void actualClientAndServerHandlersExchangeVersionOnePackets() {
        MainThread main = new MainThread() {
            private final Thread owner = Thread.currentThread();
            public boolean isMainThread() { return Thread.currentThread() == owner; }
            public void execute(Runnable task) { requireMainThread(); task.run(); }
        };
        ServerHandshake server = new ServerHandshake(main);
        ClientHandshake client = new ClientHandshake();
        UUID player = UUID.randomUUID();
        assertFalse(client.canUseSlot()); assertFalse(server.canUseSlot(player));
        byte[] hello = EnvelopeCodec.encode(client.begin());
        assertFalse(client.canUseSlot());
        byte[] ack = server.receive(player, hello).orElseThrow();
        client.receive(EnvelopeCodec.decode(ack));
        assertTrue(client.canUseSlot()); assertTrue(server.canUseSlot(player));
        server.disconnect(player); client.reset();
        assertFalse(client.canUseSlot()); assertFalse(client.helloSent()); assertFalse(server.canUseSlot(player));
    }

    @Test void clientRejectsUnsolicitedAckEnvelopeMismatchAndPayloadMismatch() {
        ClientHandshake client = new ClientHandshake();
        client.receive(Handshake.acknowledgement());
        assertFalse(client.canUseSlot());
        client.begin();
        client.receive(new Envelope(2, PacketType.HELLO_ACK, Handshake.acknowledgement().payload()));
        assertFalse(client.canUseSlot());
        var payload = Handshake.acknowledgement().payload(); payload.addProperty("protocol", 2);
        client.receive(Envelope.current(PacketType.HELLO_ACK, payload));
        assertFalse(client.canUseSlot());
        client.receive(Handshake.acknowledgement()); assertTrue(client.canUseSlot());
        client.reject(); assertFalse(client.canUseSlot());
    }

    @Test void allFixedErrorCodesHaveJapaneseMessages() {
        for (ErrorCode code : ErrorCode.values()) {
            String message = ErrorMessages.japanese(code);
            assertFalse(message.isBlank());
            assertTrue(message.codePoints().anyMatch(cp -> cp > 127), code.name());
        }
    }
}
