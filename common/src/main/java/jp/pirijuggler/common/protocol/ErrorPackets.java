package jp.pirijuggler.common.protocol;

import com.google.gson.JsonObject;

/** SPEC v4 chapter 87 error responses. */
public final class ErrorPackets {
    private ErrorPackets() { }

    public static Envelope error(ErrorCode code) {
        JsonObject payload = new JsonObject();
        payload.addProperty("errorCode", code.name());
        return Envelope.current(PacketType.ERROR, payload);
    }

    public static Envelope rejected(long clientSequence, ErrorCode code) {
        JsonObject payload = new JsonObject();
        payload.addProperty("clientSequence", clientSequence);
        payload.addProperty("errorCode", code.name());
        return Envelope.current(PacketType.ACTION_REJECTED, payload);
    }
}
