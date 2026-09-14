package jp.pirijuggler.common.protocol;

import com.google.gson.JsonObject;
import java.util.Objects;

public record Envelope(int protocol, PacketType packetType, JsonObject payload) {
    public Envelope {
        if (protocol < 0 || protocol > 65535) throw new ProtocolException("Protocol outside uint16");
        Objects.requireNonNull(packetType, "packetType");
        payload = Objects.requireNonNull(payload, "payload").deepCopy();
    }

    @Override public JsonObject payload() { return payload.deepCopy(); }

    public static Envelope current(PacketType type, JsonObject payload) {
        return new Envelope(Protocol.VERSION, type, payload);
    }
}
