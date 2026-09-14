package jp.pirijuggler.fabric.network;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.Protocol;
import java.util.UUID;

/** Connection-local state for the screen/input phase. Contains public server data only. */
public final class ClientSession {
    private UUID session;
    private int machine;
    private long nextSequence;
    private JsonObject publicState;
    private JsonObject adminState;
    public void receive(Envelope envelope, boolean compatible) {
        if (!compatible || envelope.protocol() != Protocol.VERSION) { reset(); return; }
        JsonObject body = envelope.payload();
        switch (envelope.packetType()) {
            case OPEN_MACHINE -> {
                session = UUID.fromString(body.get("sessionId").getAsString()); machine = body.get("machineId").getAsInt();
                nextSequence = body.get("expectedNextClientSequence").getAsLong(); publicState = null; adminState = null;
            }
            case PUBLIC_STATE -> { if (matches(body)) { publicState = body.deepCopy(); nextSequence = Math.max(nextSequence, body.get("expectedNextClientSequence").getAsLong()); } }
            case SESSION_END -> { if (session != null && session.toString().equals(body.get("sessionId").getAsString())) clearGame(); }
            case SESSION_SUSPENDED -> { if (matches(body)) clearGame(); }
            case ADMIN_STATE -> { UUID.fromString(body.get("adminSessionId").getAsString()); adminState = body.deepCopy(); }
            default -> { }
        }
    }
    private boolean matches(JsonObject body) {
        return session != null && body.has("sessionId") && body.has("machineId")
                && session.toString().equals(body.get("sessionId").getAsString()) && machine == body.get("machineId").getAsInt();
    }
    public UUID sessionId() { return session; }
    public int machineId() { return machine; }
    public long nextSequence() { return nextSequence; }
    public long takeSequence() {
        if (session == null) throw new IllegalStateException("No open session");
        long value = nextSequence; nextSequence = Math.addExact(nextSequence, 1); return value;
    }
    public JsonObject publicState() { return publicState == null ? null : publicState.deepCopy(); }
    public JsonObject adminState() { return adminState == null ? null : adminState.deepCopy(); }
    private void clearGame() { session = null; machine = 0; nextSequence = 0; publicState = null; }
    public void reset() { clearGame(); adminState = null; }
}
