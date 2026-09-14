package jp.pirijuggler.fabric.network;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.Handshake;
import jp.pirijuggler.common.protocol.Protocol;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.protocol.ErrorCode;

/** Accessed on the Minecraft client thread. A reconnect always needs a new ACK. */
public final class ClientHandshake {
    private boolean helloSent;
    private boolean compatible;

    public Envelope begin() { helloSent = true; compatible = false; return Handshake.hello(); }
    public void receive(Envelope envelope) {
        if (envelope.protocol() != Protocol.VERSION) { compatible = false; return; }
        if (envelope.packetType() == PacketType.ERROR) {
            var code = envelope.payload().get("errorCode");
            if (code != null && code.isJsonPrimitive() && code.getAsJsonPrimitive().isString()
                    && ErrorCode.PROTOCOL_MISMATCH.name().equals(code.getAsString())) compatible = false;
        }
        if (envelope.packetType() == jp.pirijuggler.common.protocol.PacketType.HELLO_ACK)
            compatible = helloSent && Handshake.validAcknowledgement(envelope);
    }
    public boolean helloSent() { return helloSent; }
    public boolean canUseSlot() { return compatible; }
    public void reject() { compatible = false; }
    public void reset() { helloSent = false; compatible = false; }
}
