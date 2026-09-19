package jp.pirijuggler.common.protocol;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class EnvelopeCodecTest {
    @Test void wireBytesMatchTheSpecifiedHelloExactly() {
        String json = "{\"protocol\":2,\"modVersion\":\"1.0.0\"}";
        byte[] encoded = EnvelopeCodec.encode(Handshake.hello());
        assertArrayEquals(new byte[]{0x50, 0x49, 0x52, 0x49, 0, 2, 1, (byte) json.length()}, Arrays.copyOf(encoded, 8));
        assertEquals(json, new String(encoded, 8, encoded.length - 8, StandardCharsets.UTF_8));
        assertEquals(Handshake.hello(), EnvelopeCodec.decode(encoded));
    }

    @Test void unicodeAndEveryPacketIdRoundTrip() {
        JsonObject json = new JsonObject();
        json.addProperty("text", "ピリ🌶\n\"\\");
        for (PacketType type : PacketType.values()) {
            if (type.isReserved()) {
                assertThrows(ProtocolException.class, () -> EnvelopeCodec.encode(Envelope.current(type, json)));
                continue;
            }
            Envelope envelope = new Envelope(65535, type, json);
            assertEquals(envelope, EnvelopeCodec.decode(EnvelopeCodec.encode(envelope)));
        }
    }

    @Test void maxPayloadAndVarIntBoundariesUseByteLength() {
        for (int size : new int[]{127, 128, 16383, 16384, Protocol.MAX_PAYLOAD_BYTES}) {
            JsonObject json = new JsonObject();
            json.addProperty("a", "x".repeat(size - 8));
            Envelope envelope = Envelope.current(PacketType.PUBLIC_STATE, json);
            byte[] encoded = EnvelopeCodec.encode(envelope);
            assertEquals(envelope, EnvelopeCodec.decode(encoded));
            assertEquals(size + 7 + (size < 128 ? 1 : size < 16384 ? 2 : 3), encoded.length);
        }
        JsonObject oversized = new JsonObject();
        oversized.addProperty("a", "あ".repeat(11000));
        assertThrows(ProtocolException.class, () -> EnvelopeCodec.encode(Envelope.current(PacketType.PUBLIC_STATE, oversized)));
    }

    @Test void rejectsTruncationTrailingBytesMagicAndUnknownIds() {
        byte[] valid = EnvelopeCodec.encode(Handshake.hello());
        for (int i = 0; i < valid.length; i++) {
            byte[] cut = Arrays.copyOf(valid, i);
            assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(cut));
        }
        assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(Arrays.copyOf(valid, valid.length + 1)));
        byte[] badMagic = valid.clone(); badMagic[0] = 0;
        assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(badMagic));
        byte[] badType = valid.clone(); badType[6] = 99;
        assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(badType));
    }

    @ParameterizedTest @ValueSource(strings = {"[]", "null", "123", "{bad:1}", "{'a':1}", "{\"a\":NaN}", "{\"a\":1,}", "{} {}", "{\"a\":1,\"a\":2}", "{\"a\":/*x*/1}"})
    void rejectsNonObjectAndNonStrictJson(String json) {
        assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(raw(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test void rejectsInvalidUtf8OverlongVarIntAndDeepJson() {
        assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(raw(new byte[]{'{', '"', 'a', '"', ':', '"', (byte) 0xc3, '"', '}'})));
        for (byte[] length : new byte[][]{{(byte) 0x80}, {(byte) 0x82, 0}, {(byte) 0xff, (byte) 0xff, 0x02}, {(byte) 0x80, (byte) 0x80, (byte) 0x80}}) {
            byte[] bytes = ByteBuffer.allocate(7 + length.length).putInt(Protocol.MAGIC).putShort((short) 2).put((byte) 1).put(length).array();
            assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(bytes));
        }
        String deep = "{\"a\":" + "[".repeat(1000) + "0" + "]".repeat(1000) + "}";
        assertThrows(ProtocolException.class, () -> EnvelopeCodec.decode(raw(deep.getBytes(StandardCharsets.UTF_8))));
    }

    @Test void envelopeOwnsItsPayload() {
        JsonObject json = new JsonObject(); json.addProperty("a", 1);
        Envelope envelope = Envelope.current(PacketType.PUBLIC_STATE, json);
        json.addProperty("a", 2);
        envelope.payload().addProperty("a", 3);
        assertEquals(1, envelope.payload().get("a").getAsInt());
    }

    private static byte[] raw(byte[] payload) {
        var buffer = ByteBuffer.allocate(10 + payload.length).putInt(Protocol.MAGIC).putShort((short) 2).put((byte) 1);
        int length = payload.length;
        do { int b = length & 127; length >>>= 7; buffer.put((byte) (b | (length == 0 ? 0 : 128))); } while (length != 0);
        buffer.put(payload);
        return Arrays.copyOf(buffer.array(), buffer.position());
    }
}
