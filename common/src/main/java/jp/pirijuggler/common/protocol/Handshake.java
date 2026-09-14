package jp.pirijuggler.common.protocol;

import com.google.gson.JsonObject;

public final class Handshake {
    private Handshake() { }

    public static Envelope hello() { return packet(PacketType.HELLO, "modVersion", Protocol.MOD_VERSION); }
    public static Envelope acknowledgement() {
        return packet(PacketType.HELLO_ACK, "serverVersion", Protocol.SERVER_VERSION);
    }

    public static boolean validHello(Envelope envelope) {
        return valid(envelope, PacketType.HELLO, "modVersion");
    }

    public static boolean validAcknowledgement(Envelope envelope) {
        return valid(envelope, PacketType.HELLO_ACK, "serverVersion");
    }

    private static Envelope packet(PacketType type, String versionKey, String version) {
        JsonObject payload = new JsonObject();
        payload.addProperty("protocol", Protocol.VERSION);
        payload.addProperty(versionKey, version);
        return Envelope.current(type, payload);
    }

    private static boolean valid(Envelope envelope, PacketType type, String versionKey) {
        if (envelope.protocol() != Protocol.VERSION || envelope.packetType() != type) return false;
        JsonObject payload = envelope.payload();
        return payload.size() == 2
                && payload.has("protocol") && payload.get("protocol").isJsonPrimitive()
                && payload.getAsJsonPrimitive("protocol").isNumber()
                && payload.get("protocol").getAsString().equals(Integer.toString(Protocol.VERSION))
                && payload.has(versionKey) && payload.get(versionKey).isJsonPrimitive()
                && payload.getAsJsonPrimitive(versionKey).isString()
                && !payload.get(versionKey).getAsString().isBlank();
    }

}
