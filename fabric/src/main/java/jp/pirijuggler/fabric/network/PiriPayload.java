package jp.pirijuggler.fabric.network;

import jp.pirijuggler.common.protocol.Protocol;
import jp.pirijuggler.common.protocol.ProtocolException;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Raw PIRI envelope; no additional length prefix inside the custom payload. */
public record PiriPayload(byte[] bytes) implements CustomPayload {
    public static final Id<PiriPayload> ID = new Id<>(Identifier.of(Protocol.CHANNEL));
    public static final PacketCodec<RegistryByteBuf, PiriPayload> CODEC = PacketCodec.of(PiriPayload::write, PiriPayload::read);

    public PiriPayload {
        if (bytes.length > Protocol.MAX_ENVELOPE_BYTES) throw new ProtocolException("Envelope too large");
        bytes = bytes.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
    private void write(RegistryByteBuf buffer) { buffer.writeBytes(bytes); }
    private static PiriPayload read(RegistryByteBuf buffer) {
        if (buffer.readableBytes() > Protocol.MAX_ENVELOPE_BYTES) throw new ProtocolException("Envelope too large");
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return new PiriPayload(bytes);
    }
}
