package jp.pirijuggler.common.protocol;

import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class EnvelopeCodec {
    private EnvelopeCodec() { }

    public static byte[] encode(Envelope envelope) {
        if (envelope.packetType().isReserved()) throw new ProtocolException("Reserved packet IDs cannot be sent");
        if (envelope.payload().has("type")) throw new ProtocolException("Packet type belongs only in the envelope");
        byte[] payload = envelope.payload().toString().getBytes(StandardCharsets.UTF_8);
        if (payload.length > Protocol.MAX_PAYLOAD_BYTES) throw new ProtocolException("Payload too large");
        ByteBuffer buffer = ByteBuffer.allocate(Protocol.MAX_ENVELOPE_BYTES).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(Protocol.MAGIC).putShort((short) envelope.protocol()).put((byte) envelope.packetType().id());
        int length = payload.length;
        do {
            int bits = length & 0x7f;
            length >>>= 7;
            buffer.put((byte) (bits | (length == 0 ? 0 : 0x80)));
        } while (length != 0);
        buffer.put(payload);
        byte[] result = new byte[buffer.position()];
        buffer.flip().get(result);
        return result;
    }

    public static Envelope decode(byte[] bytes) {
        if (bytes == null || bytes.length < 8 || bytes.length > Protocol.MAX_ENVELOPE_BYTES)
            throw new ProtocolException("Invalid envelope size");
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (buffer.getInt() != Protocol.MAGIC) throw new ProtocolException("Invalid PIRI magic");
        int protocol = Short.toUnsignedInt(buffer.getShort());
        PacketType type = PacketType.fromId(Byte.toUnsignedInt(buffer.get()));
        int length = readLength(buffer);
        if (length != buffer.remaining()) throw new ProtocolException("Payload length mismatch");
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer).toString();
            validateJson(json);
            var payload = JsonParser.parseString(json);
            if (!payload.isJsonObject()) throw new ProtocolException("Payload must be a JSON object");
            if (payload.getAsJsonObject().has("type")) throw new ProtocolException("Packet type belongs only in the envelope");
            return new Envelope(protocol, type, payload.getAsJsonObject());
        } catch (CharacterCodingException exception) {
            throw new ProtocolException("Invalid UTF-8", exception);
        } catch (IOException | IllegalStateException | com.google.gson.JsonParseException exception) {
            throw new ProtocolException("Invalid JSON", exception);
        }
    }

    private static int readLength(ByteBuffer buffer) {
        int length = 0;
        for (int i = 0; i < 3; i++) {
            if (!buffer.hasRemaining()) throw new ProtocolException("Truncated VarInt");
            int value = Byte.toUnsignedInt(buffer.get());
            length |= (value & 0x7f) << (i * 7);
            if ((value & 0x80) == 0) {
                if ((i > 0 && (value & 0x7f) == 0) || length > Protocol.MAX_PAYLOAD_BYTES)
                    throw new ProtocolException("Invalid payload length");
                return length;
            }
        }
        throw new ProtocolException("Oversized VarInt");
    }

    private static void validateJson(String json) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            readValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new ProtocolException("Trailing JSON data");
        }
    }

    private static void readValue(JsonReader reader, int depth) throws IOException {
        if (depth > 64) throw new ProtocolException("JSON nesting too deep");
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var names = new java.util.HashSet<String>();
                while (reader.hasNext()) {
                    if (!names.add(reader.nextName())) throw new ProtocolException("Duplicate JSON field");
                    readValue(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) readValue(reader, depth + 1);
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new ProtocolException("Expected JSON value");
        }
    }
}
