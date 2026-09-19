package jp.pirijuggler.common.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V4ProtocolTest {
    @Test void handshakeHasNoDuplicatePacketType() {
        assertEquals("{\"protocol\":2,\"modVersion\":\"1.0.0\"}", Handshake.hello().payload().toString());
        assertEquals("{\"protocol\":2,\"serverVersion\":\"1.0.0\"}", Handshake.acknowledgement().payload().toString());
        var payload = Handshake.hello().payload(); payload.addProperty("type", "HELLO");
        assertFalse(Handshake.validHello(Envelope.current(PacketType.HELLO, payload)));
        assertThrows(ProtocolException.class, () -> EnvelopeCodec.encode(Envelope.current(PacketType.HELLO, payload)));
    }

    @Test void errorPayloadsUseTheSpecifiedFields() {
        assertEquals("{\"errorCode\":\"PROTOCOL_MISMATCH\"}", ErrorPackets.error(ErrorCode.PROTOCOL_MISMATCH).payload().toString());
        assertEquals(PacketType.ERROR, ErrorPackets.error(ErrorCode.PROTOCOL_MISMATCH).packetType());
        assertEquals("{\"clientSequence\":123,\"errorCode\":\"SEQUENCE_OLD\"}", ErrorPackets.rejected(123, ErrorCode.SEQUENCE_OLD).payload().toString());
        assertEquals(PacketType.ACTION_REJECTED, ErrorPackets.rejected(123, ErrorCode.SEQUENCE_OLD).packetType());
    }
}
