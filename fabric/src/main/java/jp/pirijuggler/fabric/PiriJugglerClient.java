package jp.pirijuggler.fabric;

import jp.pirijuggler.common.protocol.EnvelopeCodec;
import jp.pirijuggler.common.protocol.PacketType;
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
    private static final HyperDogClientReels HYPER_DOG_REELS = new HyperDogClientReels(System::currentTimeMillis);
    private static net.minecraft.registry.RegistryKey<net.minecraft.world.World> LAST_WORLD;

    @Override public void onInitializeClient() {
        SlotKeys.register(); PiriSounds.register(); GodLcdTheme.register(); WorldCabinetRenderer.register();
        PayloadTypeRegistry.playC2S().register(PiriPayload.ID, PiriPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PiriPayload.ID, PiriPayload.CODEC);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            HANDSHAKE.reset();
            SESSION.reset();
            REMOTE.reset();
            HYPER_DOG_REELS.reset();
            HallAudio.reset();
            SlotUi.reset();
            sendHelloWhenChannelAvailable(client);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> { HANDSHAKE.reset(); SESSION.reset(); REMOTE.reset(); HYPER_DOG_REELS.reset(); HallAudio.reset(); SlotUi.reset(); }));
        ClientTickEvents.END_CLIENT_TICK.register(PiriJugglerClient::sendHelloWhenChannelAvailable);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var worldKey=client.world==null?null:client.world.getRegistryKey();
            if(LAST_WORLD!=null&&worldKey!=null&&!LAST_WORLD.equals(worldKey)){REMOTE.reset();HallAudio.reset();}
            LAST_WORLD=worldKey;SlotUi.tick();HallAudio.drain(REMOTE);
        });
        ClientPlayNetworking.registerGlobalReceiver(PiriPayload.ID, (payload, context) -> context.client().execute(() -> {
            try {
                var envelope = EnvelopeCodec.decode(payload.bytes());
                HANDSHAKE.receive(envelope); SESSION.receive(envelope, HANDSHAKE.canUseSlot());
                if (HANDSHAKE.canUseSlot()) {
                    // HYPER_DOG uses its own Phase02 reel-only screen; legacy SlotUi must
                    // never parse its protocol as an existing JUGGLER machine.
                    boolean openingHyper = envelope.packetType()==PacketType.OPEN_MACHINE
                            && envelope.payload().has("machineType")
                            && "HYPER_DOG".equals(envelope.payload().get("machineType").getAsString());
                    boolean wasHyper = HYPER_DOG_REELS.opened();
                    if (wasHyper && envelope.packetType()==PacketType.OPEN_MACHINE && !openingHyper) {
                        HYPER_DOG_REELS.reset(); // Switch back to a legacy machine
                        context.client().setScreen(null);
                    } else if (openingHyper || wasHyper) {
                        HYPER_DOG_REELS.receive(envelope);
                        if (openingHyper) {
                            SlotUi.reset();
                            context.client().setScreen(new HyperDogReelPreviewScreen(HYPER_DOG_REELS));
                        } else if (!HYPER_DOG_REELS.opened()) {
                            context.client().setScreen(null);
                        }
                        return;
                    }
                    if (RemoteMachineRegistry.isRemote(envelope.packetType())) {
                        REMOTE.receive(envelope);
                        Integer success=REMOTE.pollSkillSuccessSound();
                        if(success!=null){
                            var machine=REMOTE.view(success);
                            if(machine!=null)HallAudio.publicNotice(machine,"notice");
                        }
                        return;
                    }
                    var outbound = (java.util.function.Consumer<jp.pirijuggler.common.protocol.Envelope>) packet -> ClientPlayNetworking.send(new PiriPayload(EnvelopeCodec.encode(packet)));
                    AdminUi.receive(envelope, SESSION, outbound);
                    SlotUi.receive(envelope, SESSION, outbound);
                }
            } catch (RuntimeException exception) {
                HANDSHAKE.reject(); SESSION.reset(); REMOTE.reset(); HYPER_DOG_REELS.reset(); HallAudio.reset();
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
    /** Dedicated Phase02 Fabric reel state; existing slot UI stays unchanged. */
    public static HyperDogClientReels hyperDogReels() {
        if (!MinecraftClient.getInstance().isOnThread()) throw new IllegalStateException("Client state requires main thread");
        return HYPER_DOG_REELS;
    }
}
