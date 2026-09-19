package jp.pirijuggler.fabric;

import jp.pirijuggler.common.protocol.EnvelopeCodec;
import jp.pirijuggler.fabric.network.ClientHandshake;
import jp.pirijuggler.fabric.network.ClientSession;
import jp.pirijuggler.fabric.network.PiriPayload;
import jp.pirijuggler.fabric.network.RemoteMachineRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import jp.pirijuggler.fabric.render.WorldCabinetRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import jp.pirijuggler.fabric.ui.*;

public final class PiriJugglerClient implements ClientModInitializer {
    private static final ClientHandshake HANDSHAKE = new ClientHandshake();
    private static final ClientSession SESSION = new ClientSession();
    private static final RemoteMachineRegistry REMOTE = new RemoteMachineRegistry();

    @Override public void onInitializeClient() {
        SlotKeys.register(); PiriSounds.register(); WorldCabinetRenderer.register();
        PayloadTypeRegistry.playC2S().register(PiriPayload.ID, PiriPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PiriPayload.ID, PiriPayload.CODEC);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            HANDSHAKE.reset();
            SESSION.reset();
            REMOTE.reset();
            SlotUi.reset();
            sendHelloWhenChannelAvailable(client);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> { HANDSHAKE.reset(); SESSION.reset(); REMOTE.reset(); SlotUi.reset(); }));
        ClientTickEvents.END_CLIENT_TICK.register(PiriJugglerClient::sendHelloWhenChannelAvailable);
        ClientTickEvents.END_CLIENT_TICK.register(client -> SlotUi.tick());
        ClientPlayNetworking.registerGlobalReceiver(PiriPayload.ID, (payload, context) -> context.client().execute(() -> {
            try {
                var envelope = EnvelopeCodec.decode(payload.bytes());
                HANDSHAKE.receive(envelope); SESSION.receive(envelope, HANDSHAKE.canUseSlot());
                if (HANDSHAKE.canUseSlot()) {
                    if (RemoteMachineRegistry.isRemote(envelope.packetType())) {
                        REMOTE.receive(envelope);
                        return;
                    }
                    var outbound = (java.util.function.Consumer<jp.pirijuggler.common.protocol.Envelope>) packet -> ClientPlayNetworking.send(new PiriPayload(EnvelopeCodec.encode(packet)));
                    AdminUi.receive(envelope, SESSION, outbound);
                    SlotUi.receive(envelope, SESSION, outbound);
                }
            } catch (RuntimeException exception) {
                HANDSHAKE.reject(); SESSION.reset(); REMOTE.reset();
                if (SlotUi.hidesHud() || AdminUi.isOpen()) context.client().setScreen(null);
                SlotUi.reset();
            }
        }));
    }

    private static void sendHelloWhenChannelAvailable(MinecraftClient client) {
        if (!client.isOnThread()) throw new IllegalStateException("Fabric sends must run on the main thread");
        if (client.getNetworkHandler() != null && !HANDSHAKE.helloSent() && ClientPlayNetworking.canSend(PiriPayload.ID))
            ClientPlayNetworking.send(new PiriPayload(EnvelopeCodec.encode(HANDSHAKE.begin())));
    }

    public static boolean canUseSlot() {
        if (!MinecraftClient.getInstance().isOnThread()) throw new IllegalStateException("Client state requires main thread");
        return HANDSHAKE.canUseSlot();
    }
    public static ClientSession session() {
        if (!MinecraftClient.getInstance().isOnThread()) throw new IllegalStateException("Client state requires main thread");
        return SESSION;
    }
    public static RemoteMachineRegistry remoteMachines() {
        if (!MinecraftClient.getInstance().isOnThread()) throw new IllegalStateException("Client state requires main thread");
        return REMOTE;
    }
}
