package jp.pirijuggler.fabric;

import jp.pirijuggler.common.protocol.EnvelopeCodec;
import jp.pirijuggler.common.protocol.ProtocolException;
import jp.pirijuggler.fabric.network.ClientHandshake;
import jp.pirijuggler.fabric.network.ClientSession;
import jp.pirijuggler.fabric.network.PiriPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import jp.pirijuggler.fabric.ui.*;

public final class PiriJugglerClient implements ClientModInitializer {
    private static final ClientHandshake HANDSHAKE = new ClientHandshake();
    private static final ClientSession SESSION = new ClientSession();

    @Override public void onInitializeClient() {
        SlotKeys.register(); PiriSounds.register();
        PayloadTypeRegistry.playC2S().register(PiriPayload.ID, PiriPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PiriPayload.ID, PiriPayload.CODEC);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            HANDSHAKE.reset();
            SESSION.reset();
            SlotUi.reset();
            sendHelloWhenChannelAvailable(client);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> { HANDSHAKE.reset(); SESSION.reset(); SlotUi.reset(); }));
        ClientTickEvents.END_CLIENT_TICK.register(PiriJugglerClient::sendHelloWhenChannelAvailable);
        ClientTickEvents.END_CLIENT_TICK.register(client -> SlotUi.tick());
        ClientPlayNetworking.registerGlobalReceiver(PiriPayload.ID, (payload, context) -> context.client().execute(() -> {
            try {
                var envelope = EnvelopeCodec.decode(payload.bytes());
                HANDSHAKE.receive(envelope); SESSION.receive(envelope, HANDSHAKE.canUseSlot());
                if (HANDSHAKE.canUseSlot()) SlotUi.receive(envelope,SESSION,outbound -> ClientPlayNetworking.send(new PiriPayload(EnvelopeCodec.encode(outbound))));
            } catch (RuntimeException exception) {
                HANDSHAKE.reject(); SESSION.reset();
                if (SlotUi.hidesHud()) context.client().setScreen(null);
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
}
