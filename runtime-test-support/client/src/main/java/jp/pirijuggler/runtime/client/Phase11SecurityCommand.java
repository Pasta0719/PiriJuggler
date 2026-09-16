package jp.pirijuggler.runtime.client;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.EnvelopeCodec;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.fabric.PiriJugglerClient;
import jp.pirijuggler.fabric.network.PiriPayload;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;

import java.util.UUID;

/** Test-only real-client negative packet probe for Phase11. */
public final class Phase11SecurityCommand {
    private Phase11SecurityCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("pirisecurity")
                        .executes(context -> run(context.getSource()))));
    }

    private static int run(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        if (!PiriJugglerClient.canUseSlot()) {
            source.sendFeedback(Text.literal("PIRI_SECURITY_NOT_CONNECTED"));
            return 0;
        }
        var session = PiriJugglerClient.session();
        if (session.sessionId() == null) {
            source.sendFeedback(Text.literal("PIRI_SECURITY_OPEN_MACHINE_FIRST"));
            return 0;
        }

        long sequence = session.takeSequence();
        Envelope valid = action(session.sessionId(), session.machineId(), sequence);
        byte[] duplicate = EnvelopeCodec.encode(valid);

        // Real C2S duplicate: only the first copy may have a side effect.
        ClientPlayNetworking.send(new PiriPayload(duplicate));
        ClientPlayNetworking.send(new PiriPayload(duplicate));

        // Real C2S forged session using the next sequence. It must be rejected without
        // consuming that sequence, so normal client operation can continue afterward.
        Envelope forged = action(UUID.randomUUID(), session.machineId(), sequence + 1);
        ClientPlayNetworking.send(new PiriPayload(EnvelopeCodec.encode(forged)));

        source.sendFeedback(Text.literal("PIRI_SECURITY_SENT duplicateSequence=" + sequence + " forgedSequence=" + (sequence + 1)));
        source.sendFeedback(Text.literal("EXPECT: exactly one SPACE_ACTION side effect; duplicate and forged session have no side effect"));
        return 1;
    }

    private static Envelope action(UUID sessionId, int machineId, long sequence) {
        JsonObject body = new JsonObject();
        body.addProperty("sessionId", sessionId.toString());
        body.addProperty("machineId", machineId);
        body.addProperty("clientSequence", sequence);
        return Envelope.current(PacketType.SPACE_ACTION, body);
    }
}
