package jp.pirijuggler.runtime.client;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.Handshake;
import jp.pirijuggler.common.protocol.PacketType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class RuntimeProbe implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("PiriRuntimeAcceptance");
    private static JsonObject result;
    private static long receivedAt;
    private static long initializedAt;
    private static boolean captured;
    private static boolean written;

    public static boolean mismatch() { return "mismatch".equals(System.getProperty("piri.runtime.scenario")); }

    @Override public void onInitializeClient() {
        if (Phase02Probe.enabled()) Phase02Probe.initialize();
        initializedAt = System.currentTimeMillis();
        ClientTickEvents.END_CLIENT_TICK.register(RuntimeProbe::tick);
        LOG.info("PIRI_RUNTIME_CLIENT_READY Minecraft={} FabricLoader={} scenario={}", SharedConstants.getGameVersion().getName(), version("fabricloader"), System.getProperty("piri.runtime.scenario"));
    }

    public static void sent(Envelope hello) { LOG.info("PIRI_RUNTIME_HELLO protocol={} payload={}", hello.protocol(), hello.payload()); }

    public static void received(Envelope envelope, boolean clientAllowed) {
        if (Phase02Probe.enabled()) { Phase02Probe.received(envelope, clientAllowed); return; }
        if (result != null) return;
        boolean passed = mismatch()
                ? envelope.packetType() == PacketType.ERROR && "PROTOCOL_MISMATCH".equals(envelope.payload().get("errorCode").getAsString()) && !clientAllowed
                : Handshake.validAcknowledgement(envelope) && clientAllowed;
        result = new JsonObject();
        result.addProperty("timestamp", Instant.now().toString());
        result.addProperty("scenario", System.getProperty("piri.runtime.scenario"));
        result.addProperty("minecraftVersion", SharedConstants.getGameVersion().getName());
        result.addProperty("fabricLoader", version("fabricloader"));
        result.addProperty("fabricApi", version("fabric-api"));
        result.addProperty("packetType", envelope.packetType().name());
        result.addProperty("protocol", envelope.protocol());
        result.add("payload", envelope.payload());
        result.addProperty("gameplayAllowed", clientAllowed);
        result.addProperty("passed", passed);
        receivedAt = System.currentTimeMillis();
        LOG.info("PIRI_RUNTIME_CLIENT {}", result);
    }

    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id).orElseThrow().getMetadata().getVersion().getFriendlyString();
    }

    private static void tick(MinecraftClient client) {
        if (Phase02Probe.enabled()) { Phase02Probe.tick(client); return; }
        long now = System.currentTimeMillis();
        if (result == null && now - initializedAt > 180_000) {
            result = new JsonObject(); result.addProperty("passed", false); result.addProperty("reason", "handshake timeout");
            receivedAt = now - 10_000;
        }
        if (result == null || written) return;
        if (!captured && client.world != null && now - receivedAt > 2_000) {
            captured = true;
            ScreenshotRecorder.saveScreenshot(client.runDirectory, "phase01-" + System.getProperty("piri.runtime.scenario") + ".png", client.getFramebuffer(), text -> LOG.info("PIRI_RUNTIME_SCREENSHOT {}", text.getString()));
        }
        if (now - receivedAt > 5_000) {
            written = true;
            try {
                Path output = Path.of(System.getProperty("piri.runtime.clientResult"));
                Files.createDirectories(output.getParent());
                Files.writeString(output, result.toString());
            } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            client.scheduleStop();
        }
    }
}
