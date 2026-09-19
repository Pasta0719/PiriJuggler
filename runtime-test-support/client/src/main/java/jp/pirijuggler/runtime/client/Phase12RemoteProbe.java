package jp.pirijuggler.runtime.client;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.fabric.PiriJugglerClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

import java.util.EnumMap;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Test-only Phase12 spectator packet recorder. Never included in production jars. */
public final class Phase12RemoteProbe {
    private static final Map<PacketType,Integer> COUNTS = new EnumMap<>(PacketType.class);
    private static int lastMachineId;

    private Phase12RemoteProbe() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("piri12")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal(summary()));
                            return 1;
                        })
                        .then(literal("reset").executes(ctx -> {
                            COUNTS.clear();
                            lastMachineId = 0;
                            ctx.getSource().sendFeedback(Text.literal("PIRI12 RESET"));
                            return 1;
                        }))));
    }

    public static void received(Envelope envelope) {
        if (!isRemote(envelope.packetType())) return;
        COUNTS.merge(envelope.packetType(), 1, Integer::sum);
        var body = envelope.payload();
        if (body.has("machineId") && body.get("machineId").isJsonPrimitive() && body.getAsJsonPrimitive("machineId").isNumber()) {
            try { lastMachineId = body.get("machineId").getAsInt(); }
            catch (RuntimeException ignored) { }
        }
    }

    private static boolean isRemote(PacketType type) {
        return switch (type) {
            case REMOTE_MACHINE_SNAPSHOT, REMOTE_MACHINE_SPIN, REMOTE_MACHINE_STOP,
                    REMOTE_MACHINE_NOTICE, REMOTE_MACHINE_BONUS,
                    REMOTE_MACHINE_REMOVE, REMOTE_MACHINE_SOUND -> true;
            default -> false;
        };
    }

    private static int count(PacketType type) {
        return COUNTS.getOrDefault(type, 0);
    }

    private static String summary() {
        int cached = PiriJugglerClient.remoteMachines().snapshot().size();
        return "PIRI12"
                + " SNAPSHOT=" + count(PacketType.REMOTE_MACHINE_SNAPSHOT)
                + " SPIN=" + count(PacketType.REMOTE_MACHINE_SPIN)
                + " STOP=" + count(PacketType.REMOTE_MACHINE_STOP)
                + " NOTICE=" + count(PacketType.REMOTE_MACHINE_NOTICE)
                + " BONUS=" + count(PacketType.REMOTE_MACHINE_BONUS)
                + " REMOVE=" + count(PacketType.REMOTE_MACHINE_REMOVE)
                + " SOUND=" + count(PacketType.REMOTE_MACHINE_SOUND)
                + " CACHE=" + cached
                + " LAST_MACHINE=" + lastMachineId;
    }
}
