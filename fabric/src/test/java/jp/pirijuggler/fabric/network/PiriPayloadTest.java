package jp.pirijuggler.fabric.network;

import io.netty.buffer.Unpooled;
import jp.pirijuggler.common.protocol.EnvelopeCodec;
import jp.pirijuggler.common.protocol.Handshake;
import jp.pirijuggler.common.protocol.Protocol;
import jp.pirijuggler.common.protocol.ProtocolException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PiriPayloadTest {
    @Test void fabricWireBytesAreExactlyThePaperPluginMessageBytes() {
        byte[] wire = EnvelopeCodec.encode(Handshake.hello());
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            PiriPayload.CODEC.encode(buffer, new PiriPayload(wire));
            byte[] actual = new byte[buffer.readableBytes()];
            buffer.getBytes(0, actual);
            assertArrayEquals(wire, actual);
            assertArrayEquals(wire, PiriPayload.CODEC.decode(buffer).bytes());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void payloadOwnershipAndSizeAreEnforcedBeforeCopying() {
        byte[] wire = EnvelopeCodec.encode(Handshake.hello());
        PiriPayload payload = new PiriPayload(wire);
        wire[0] = 0; payload.bytes()[0] = 0;
        assertEquals(Handshake.hello(), EnvelopeCodec.decode(payload.bytes()));
        assertThrows(ProtocolException.class, () -> new PiriPayload(new byte[Protocol.MAX_ENVELOPE_BYTES + 1]));
    }
}
