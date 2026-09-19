package jp.pirijuggler.runtime.client;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.fabric.PiriJugglerClient;
import jp.pirijuggler.fabric.network.PiriPayload;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import jp.pirijuggler.fabric.ui.*;
import jp.pirijuggler.runtime.mixin.MouseEventsInvoker;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** File-driven test controller; all gameplay actions travel over real Minecraft networking. */
public final class Phase02Probe {
    private static final JsonArray packets = new JsonArray(), messages = new JsonArray(), actions = new JsonArray();
    private static long completed;
    private static int ticks;
    private static boolean allowed;
    private static String failure;
    private static Integer targetX;
    private static JsonArray motionTrace = new JsonArray();
    private static String motionSpin;
    private static long motionAt;
    private static JsonObject phaseTap;
    private static long phaseTapAt;
    private static Path output() {
        String configured = System.getProperty("piri.runtime.clientResult");
        if (configured == null || configured.isBlank())
            throw new IllegalStateException("piri.runtime.clientResult is required for automated Phase02-05 probes");
        return Path.of(configured);
    }
    public static boolean enabled() { String scenario=System.getProperty("piri.runtime.scenario", ""); return scenario.startsWith("phase02") || scenario.startsWith("phase03") || scenario.startsWith("phase04") || scenario.startsWith("phase05") || scenario.startsWith("phase12"); }
    public static void initialize() { ClientReceiveMessageEvents.GAME.register((message, overlay) -> messages.add(message.getString())); }
    public static void received(Envelope packet, boolean compatible) {
        allowed = compatible; JsonObject entry = new JsonObject(); entry.addProperty("type",packet.packetType().name()); entry.add("payload",packet.payload());
        packets.add(entry); LoggerFactory.getLogger("PiriRuntimeAcceptance").info("PIRI_PHASE02_PACKET {}",entry);
        if(packet.packetType()==PacketType.SPIN_START){motionSpin=packet.payload().get("spinId").getAsString();motionAt=System.nanoTime();motionTrace=new JsonArray();}
    }
    public static void tick(MinecraftClient client) {
        if (client.player != null) client.mouse.unlockCursor();
        if(phaseTap!=null && client.currentScreen instanceof SlotScreen screen){
            var view=((jp.pirijuggler.runtime.mixin.SlotViewAccessor)(Object)screen).piri$view();double phase=view.phase(phaseTap.get("reel").getAsInt());double desired=phaseTap.get("phase").getAsDouble();
            if(view.canSend(PacketType.STOP_LEFT)&&phase>=desired && phase<desired+.18){
                int key=phaseTap.get("key").getAsInt();client.keyboard.onKey(client.getWindow().getHandle(),key,0,GLFW.GLFW_PRESS,0);client.keyboard.onKey(client.getWindow().getHandle(),key,0,GLFW.GLFW_RELEASE,0);phaseTap=null;
            }else if(System.nanoTime()-phaseTapAt>30_000_000_000L){failure="Timed key did not reach requested visible phase";phaseTap=null;}
        }
        if(motionSpin!=null && client.currentScreen instanceof SlotScreen screen){
            double elapsed=(System.nanoTime()-motionAt)/1e9;
            if(elapsed<=1.5){var view=((jp.pirijuggler.runtime.mixin.SlotViewAccessor)(Object)screen).piri$view();JsonObject sample=new JsonObject();sample.addProperty("elapsed",elapsed);JsonArray phases=new JsonArray();for(int i=0;i<3;i++)phases.add(view.phase(i));sample.add("phases",phases);motionTrace.add(sample);}
        }
        if (++ticks % 5 != 0) return;
        try {
            Path instruction = output().resolveSibling("command-" + (completed + 1) + ".json");
            if (Files.exists(instruction)) {
                JsonObject command = JsonParser.parseString(Files.readString(instruction)).getAsJsonObject();
                long id = command.get("id").getAsLong();
                if (id > completed && client.player != null && client.world != null && client.interactionManager != null) {
                    String kind = command.get("kind").getAsString();
                    switch (kind) {
                        case "aim" -> {
                            targetX = command.get("x").getAsInt(); aim(client,targetX);
                        }
                        case "aimpos" -> {
                            aim(client,command.get("x").getAsInt(),command.get("y").getAsInt(),command.get("z").getAsInt());
                        }
                        case "command" -> { if (targetX != null) aim(client,targetX); client.player.networkHandler.sendChatCommand(command.get("text").getAsString()); }
                        case "click" -> {
                            int x = command.get("x").getAsInt(); aim(client,x); BlockPos block = new BlockPos(x,66,0);
                            var result = client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(x + .5,66.5,.1),Direction.SOUTH,block,false));
                            LoggerFactory.getLogger("PiriRuntimeAcceptance").info("PIRI_PHASE02_REAL_CLICK block={} result={}",block,result);
                        }
                        case "clickpos" -> {
                            int x=command.get("x").getAsInt(),y=command.get("y").getAsInt(),z=command.get("z").getAsInt();
                            aim(client,x,y,z); BlockPos block=new BlockPos(x,y,z);
                            var result=client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(x+.5,y+.5,z+.5),Direction.SOUTH,block,false));
                            LoggerFactory.getLogger("PiriRuntimeAcceptance").info("PIRI_PHASE12_REAL_CLICK block={} result={}",block,result);
                        }
                        case "close" -> {
                            var session = PiriJugglerClient.session(); JsonObject body = new JsonObject();
                            body.addProperty("sessionId",session.sessionId().toString()); body.addProperty("machineId",session.machineId()); body.addProperty("clientSequence",session.nextSequence());
                            ClientPlayNetworking.send(new PiriPayload(EnvelopeCodec.encode(Envelope.current(PacketType.CLOSE_REQUEST,body))));
                        }
                        case "slot" -> client.player.getInventory().selectedSlot = command.get("slot").getAsInt();
                        case "key" -> client.keyboard.onKey(client.getWindow().getHandle(),command.get("key").getAsInt(),0,command.get("action").getAsInt(),0);
                        case "tap", "burst" -> {
                            int key=command.get("key").getAsInt();int repetitions=kind.equals("burst")?2:1;
                            for(int i=0;i<repetitions;i++){client.keyboard.onKey(client.getWindow().getHandle(),key,0,GLFW.GLFW_PRESS,0);client.keyboard.onKey(client.getWindow().getHandle(),key,0,GLFW.GLFW_RELEASE,0);}
                        }
                        case "tap_at_phase" -> {phaseTap=command.deepCopy();phaseTapAt=System.nanoTime();}
                        case "rebind" -> {
                            for(var binding:client.options.allKeys)if(binding.getTranslationKey().equals("key.piri.stop_left"))binding.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(command.get("key").getAsInt()));
                            KeyBinding.updateKeysByCode();
                        }
                        case "mouse" -> {
                            var window=client.getWindow();var v=SlotLayout.Viewport.fit(window.getScaledWidth(),window.getScaledHeight());
                            double x=(v.x()+command.get("x").getAsDouble()*v.scale())*window.getWidth()/window.getScaledWidth();
                            double y=(v.y()+command.get("y").getAsDouble()*v.scale())*window.getHeight()/window.getScaledHeight();
                            var mouse=(MouseEventsInvoker)client.mouse;mouse.piri$cursor(window.getHandle(),x,y);
                            mouse.piri$button(window.getHandle(),command.get("button").getAsInt(),GLFW.GLFW_PRESS,0);mouse.piri$button(window.getHandle(),command.get("button").getAsInt(),GLFW.GLFW_RELEASE,0);
                        }
                        case "resize" -> client.getWindow().setWindowedSize(command.get("width").getAsInt(),command.get("height").getAsInt());
                        case "capture" -> ScreenshotRecorder.saveScreenshot(client.runDirectory,((System.getProperty("piri.runtime.scenario", "").startsWith("phase04") || System.getProperty("piri.runtime.scenario", "").startsWith("phase05"))?(System.getProperty("piri.runtime.scenario", "").startsWith("phase05")?"phase05-":"phase04-"):"phase03-")+command.get("label").getAsString()+".png",client.getFramebuffer(),message -> {});
                        case "reload" -> client.reloadResources();
                        case "screenshot" -> ScreenshotRecorder.saveScreenshot(client.runDirectory,"phase02-" + System.getProperty("piri.runtime.scenario") + ".png",client.getFramebuffer(),message -> {});
                        case "exit" -> client.scheduleStop();
                        default -> throw new IllegalArgumentException("Unknown test action " + kind);
                    }
                    completed = id; actions.add(command);
                }
            }
        } catch (Exception error) { failure = error.toString(); }
        JsonObject result = new JsonObject(); result.addProperty("completed",completed); result.addProperty("connected",client.player != null && client.world != null);
        result.addProperty("handshake",allowed); result.addProperty("failure",failure); result.add("packets",packets); result.add("messages",messages); result.add("actions",actions);
        result.addProperty("motionSpin",motionSpin);result.add("motionTrace",motionTrace);
        result.addProperty("productionSession",PiriJugglerClient.session().sessionId() == null ? null : PiriJugglerClient.session().sessionId().toString());
        if(System.getProperty("piri.runtime.scenario", "").startsWith("phase03") || (System.getProperty("piri.runtime.scenario", "").startsWith("phase04") || System.getProperty("piri.runtime.scenario", "").startsWith("phase05"))) {
            result.addProperty("screen",client.currentScreen==null?"none":client.currentScreen.getClass().getSimpleName());result.addProperty("hudHidden",SlotUi.hidesHud());
            result.addProperty("cursorLocked",client.mouse.isCursorLocked());result.addProperty("hudLeaks",HudAudit.leaks);result.addProperty("hudWorldCalls",HudAudit.worldCalls);
            result.addProperty("width",client.getWindow().getWidth());result.addProperty("height",client.getWindow().getHeight());result.add("publicState",PiriJugglerClient.session().publicState());
            if(client.currentScreen instanceof SlotScreen screen){var view=((jp.pirijuggler.runtime.mixin.SlotViewAccessor)(Object)screen).piri$view();JsonArray phases=new JsonArray();for(int i=0;i<3;i++)phases.add(view.phase(i));result.add("displayPhases",phases);result.addProperty("stopEnabled",view.canSend(PacketType.STOP_LEFT));}
            JsonObject sounds=new JsonObject();for(String sound:PiriSounds.NAMES){JsonObject s=new JsonObject();s.addProperty("registered",Registries.SOUND_EVENT.containsId(Identifier.of("piri",sound)));s.addProperty("available",PiriSounds.available(sound));sounds.add(sound,s);}result.add("sounds",sounds);
        }
        if (client.player != null) { result.addProperty("position",client.player.getPos().toString()); result.addProperty("yaw",client.player.getYaw()); result.addProperty("pitch",client.player.getPitch()); }
        try { Path output = output(); Files.createDirectories(output.getParent()); Files.writeString(output,result.toString()); }
        catch (java.nio.file.FileSystemException sharingConflict) { LoggerFactory.getLogger("PiriRuntimeAcceptance").debug("Retrying observation write on next tick",sharingConflict); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    private static void aim(MinecraftClient client, int x) { aim(client,x,66,0); }
    private static void aim(MinecraftClient client, int x, int y, int z) {
        Vec3d delta = new Vec3d(x + .5,y + .5,z + .1).subtract(client.player.getEyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        client.player.setYaw(yaw); client.player.setPitch(pitch);
        client.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw,pitch,client.player.isOnGround()));
    }
}
