package jp.pirijuggler.runtime.client;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.Reel;
import jp.pirijuggler.fabric.PiriJugglerClient;
import jp.pirijuggler.fabric.ui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/**
 * Phase05 real Fabric 1.21 cabinet interaction/render proof.
 * Drives the ACTUAL public dedicated Screen via screen.mouseClicked/keyPressed,
 * not a synthetic preview or a mocked GUI. Captures baseline SlotScreen and
 * 16:9/4:3/ultrawide HYPER_DOG screenshots from the real framebuffer.
 */
public final class HyperDogPhase05Probe {
    private static final PacketType[][] ORDERS={
        {PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT},
        {PacketType.STOP_LEFT,PacketType.STOP_RIGHT,PacketType.STOP_CENTER},
        {PacketType.STOP_CENTER,PacketType.STOP_LEFT,PacketType.STOP_RIGHT},
        {PacketType.STOP_CENTER,PacketType.STOP_RIGHT,PacketType.STOP_LEFT},
        {PacketType.STOP_RIGHT,PacketType.STOP_LEFT,PacketType.STOP_CENTER},
        {PacketType.STOP_RIGHT,PacketType.STOP_CENTER,PacketType.STOP_LEFT}
    };
    private static final Set<String> SYMBOLS=Set.of("red7","rainbow7","bar","grape","replay","cherry","watermelon");
    private static final JsonArray rounds=new JsonArray();
    private static final Set<String> stages=new LinkedHashSet<>(),screenNames=new LinkedHashSet<>(),screenshots=new LinkedHashSet<>();
    private static final Set<String> observedDisplays=new LinkedHashSet<>();
    private static final Set<String> activeLamps=new LinkedHashSet<>();
    private static final Set<String> loadedTextures=new LinkedHashSet<>();
    private static final long started=System.currentTimeMillis();
    private static String error="",lastCommandKey="";
    private static UUID currentSpin;
    private static int sentMask=-1,stops,betClicks,leverClicks,keyboardStops,mouseStops,frames;
    private static int rightOn,rightOff,displayLockFrames,reopenScreens,resizeStage;
    private static boolean assetCheck,acceptanceComplete,screenshotStarted,closed,reopened,legacyPreview;
    private static long endedAt=-1,resizeAt=-1,closedAt=-1,baselineAt=-1;
    private static HyperDogCabinetScreen original;
    public static boolean enabled(){
        return "hyperdog05-main".equals(System.getProperty("piri.runtime.scenario",""));
    }
    private static void capture(MinecraftClient client,String name){
        screenshots.add(name);
        ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),text->{});
    }
    private static void moveWindow(MinecraftClient client,int w,int h){
        GLFW.glfwSetWindowSize(client.getWindow().getHandle(),w,h);
        resizeAt=System.currentTimeMillis();
    }
    private static boolean physicalClick(HyperDogCabinetScreen s,String control){
        var item=SlotLayout.CONTROLS.stream().filter(c->c.name().equals(control)).findFirst().orElseThrow();
        var viewport=SlotLayout.Viewport.fit(s.width,s.height);
        var r=item.rect();
        double x=viewport.x()+viewport.scale()*(r.x()+r.w()/2d);
        double y=viewport.y()+viewport.scale()*(r.y()+r.h()/2d);
        long before=PiriJugglerClient.session().nextSequence();
        s.mouseClicked(x,y,0);
        s.mouseReleased(x,y,0);
        return PiriJugglerClient.session().nextSequence()>before;
    }
    private static boolean physicalKey(HyperDogCabinetScreen s,PacketType action){
        int key=switch(action){
            case STOP_LEFT -> GLFW.GLFW_KEY_LEFT;
            case STOP_CENTER -> GLFW.GLFW_KEY_DOWN;
            case STOP_RIGHT -> GLFW.GLFW_KEY_RIGHT;
            case SPACE_ACTION -> GLFW.GLFW_KEY_SPACE;
            case CLOSE_REQUEST -> GLFW.GLFW_KEY_ESCAPE;
            default -> throw new IllegalArgumentException("unsupported keyboard action");
        };
        long before=PiriJugglerClient.session().nextSequence();
        s.keyPressed(key,0,0);
        s.keyReleased(key,0,0);
        return PiriJugglerClient.session().nextSequence()>before;
    }
    private static String name(PacketType action) {
        return switch(action){
            case STOP_LEFT -> "LEFT";
            case STOP_CENTER -> "CENTER";
            case STOP_RIGHT -> "RIGHT";
            default -> throw new IllegalArgumentException("not reel");
        };
    }
    private static PacketType next(HyperDogClientReels reels){
        for(PacketType t:ORDERS[rounds.size()%ORDERS.length])if(reels.canStop(t))return t;
        return null;
    }
    private static boolean seen(UUID spin){
        for(JsonElement x:rounds)if(spin.toString().equals(x.getAsJsonObject().get("spinId").getAsString()))
            return true;
        return false;
    }
    private static void inspectAssets(MinecraftClient client){
        if(assetCheck||client.world==null)return;
        assetCheck=true;
        for(String symbol:SYMBOLS){
            String resource="textures/gui/original_at/symbols/"+symbol+".png";
            if(client.getResourceManager().getResource(Identifier.of("piri",resource)).isPresent())
                loadedTextures.add(symbol);
        }
        if(!loadedTextures.equals(SYMBOLS))
            throw new IllegalStateException("missing 7 real approved textures "+loadedTextures);
    }
    private static void observe(HyperDogCabinetScreen s){
        frames=Math.max(frames,s.renderedFrames());
        var view=s.cabinetState();
        screenNames.add(s.getClass().getSimpleName());
        stages.add(view.stage());
        if(view.active(HyperDogCabinetState.Lamp.LOWER))activeLamps.add("LOWER");
        if(view.active(HyperDogCabinetState.Lamp.UPPER))activeLamps.add("UPPER");
        if(view.active(HyperDogCabinetState.Lamp.CZ))activeLamps.add("CZ");
        if(view.rightCount()>0)rightOn++;
        else rightOff++;
        if(view.displayLocked()){
            displayLockFrames++;
            String display=view.displayText();
            if(display.startsWith("TOTAL"))observedDisplays.add("TOTAL");
            if(display.startsWith("+"))observedDisplays.add("ADD");
        }
    }
    private static void legacyBaseline(MinecraftClient client,HyperDogCabinetScreen original){
        // Draw the REAL old SlotScreen for comparison; it is a temporary
        // visual-only view and sends no command to the real server.
        SlotViewState legacy=new SlotViewState(System::nanoTime);
        JsonObject open=new JsonObject();
        open.addProperty("machineType","JUGGLER");
        open.addProperty("machineId",original.cabinetState().machineId());
        open.addProperty("sessionId",original.cabinetState().sessionId().toString());
        legacy.receive(Envelope.current(PacketType.OPEN_MACHINE,open));
        JsonObject pub=new JsonObject();
        pub.addProperty("sessionId",original.cabinetState().sessionId().toString());
        pub.addProperty("machineId",original.cabinetState().machineId());
        pub.addProperty("gameState","SEATED_READY");
        pub.addProperty("credit",50);
        pub.addProperty("bet",0);pub.addProperty("pay",0);
        pub.addProperty("heldMedals",900);
        pub.addProperty("lampOn",false);
        JsonObject positions=new JsonObject();
        positions.addProperty("left",0);positions.addProperty("center",0);positions.addProperty("right",0);
        pub.add("displayStops",positions);
        legacy.receive(Envelope.current(PacketType.PUBLIC_STATE,pub));
        SlotInput harmless=new SlotInput(PiriJugglerClient.session(),packet->{},System::nanoTime);
        client.setScreen(new SlotScreen(legacy,harmless));
        baselineAt=System.currentTimeMillis();
    }
    private static void resizeAndScreenshots(MinecraftClient client,HyperDogCabinetScreen screen){
        long now=System.currentTimeMillis();
        if(resizeStage==0&&frames>15){
            capture(client,"hyperdog-phase05-16x9.png");
            moveWindow(client,1024,768);
            resizeStage=1;
        }else if(resizeStage==1&&now-resizeAt>1800){
            float aspect=client.getWindow().getWidth()/(float)client.getWindow().getHeight();
            if(Math.abs(aspect-4f/3f)>0.03f)
                throw new IllegalStateException("actual GUI 4:3 window resize did not complete "+aspect);
            capture(client,"hyperdog-phase05-4x3.png");
            moveWindow(client,1280,640);
            resizeStage=2;
        }else if(resizeStage==2&&now-resizeAt>1800){
            float aspect=client.getWindow().getWidth()/(float)client.getWindow().getHeight();
            if(Math.abs(aspect-2f)>0.03f)
                throw new IllegalStateException("actual GUI wide resize did not complete "+aspect);
            capture(client,"hyperdog-phase05-2x1.png");
            moveWindow(client,960,540);
            resizeStage=3;
        }
    }
    public static void tick(MinecraftClient client){
        try{
            if(System.currentTimeMillis()-started>580000&&!acceptanceComplete)
                throw new IllegalStateException("Phase05 Fabric timeout at "+rounds.size());
            inspectAssets(client);
            var reels=PiriJugglerClient.hyperDogReels();
            // Real screenshot MUST follow an actual legacy SlotScreen render.
            // Taking it immediately after setScreen would snapshot the prior
            // HYPER_DOG frame and silently invalidate the visual comparison.
            if(baselineAt>=0&&!legacyPreview&&client.currentScreen instanceof SlotScreen &&
                    System.currentTimeMillis()-baselineAt>450){
                capture(client,"hyperdog-phase05-existing-slot-screen-comparison.png");
                client.setScreen(original);
                legacyPreview=true;
            }
            if(client.currentScreen instanceof HyperDogCabinetScreen screen){
                if(original==null)original=screen;
                observe(screen);
                resizeAndScreenshots(client,screen);
                if(reels.spinning()){
                    if(!reels.spinId().equals(currentSpin)){
                        currentSpin=reels.spinId();sentMask=-1;
                    }
                    if(!reels.complete()){
                        PacketType action=next(reels);
                        if(action!=null&&sentMask!=reels.stoppedMask()){
                            boolean useKeyboard=(stops%2)==0;
                            boolean sent=useKeyboard?physicalKey(screen,action):physicalClick(screen,name(action));
                            if(sent){
                                stops++;sentMask=reels.stoppedMask();
                                if(useKeyboard)keyboardStops++;else mouseStops++;
                            }
                        }
                    }else if(!seen(reels.spinId())){
                        JsonObject row=new JsonObject();
                        row.addProperty("spinId",reels.spinId().toString());
                        JsonArray recorded=new JsonArray();
                        for(Reel reel:Reel.values()){
                            recorded.add(reels.stopIndex(reel));
                            for(int i=-1;i<=1;i++){
                                String path=reels.texturePath(reel,i);
                                if(client.getResourceManager().getResource(Identifier.of("piri","textures/"+path)).isEmpty())
                                    throw new IllegalStateException("missing live reel texture "+path);
                            }
                        }
                        row.add("stops",recorded);
                        rounds.add(row);
                    }
                }
                JsonObject state=PiriJugglerClient.session().publicState();
                if(state!=null){
                    String mode=state.get("gameState").getAsString();
                    if("ACCEPTANCE_COMPLETE".equals(mode))acceptanceComplete=true;
                    String key=mode+"@"+state.get("gameCount").getAsLong();
                    if(!acceptanceComplete&&("SEATED_READY".equals(mode)||
                            "NORMAL_BETTED".equals(mode)||"REPLAY_READY".equals(mode))
                            &&(!reels.spinning()||reels.complete())&&!key.equals(lastCommandKey)
                            &&!screen.cabinetState().displayLocked()){
                        String ctrl="SEATED_READY".equals(mode)?"BET":"LEVER";
                        boolean keyInput=leverClicks%2==0 && "LEVER".equals(ctrl);
                        boolean sent=keyInput?physicalKey(screen,PacketType.SPACE_ACTION):physicalClick(screen,ctrl);
                        if(sent){
                            lastCommandKey=key;
                            if("BET".equals(ctrl))betClicks++;else leverClicks++;
                        }
                    }
                }
                if(acceptanceComplete && endedAt<0)endedAt=System.currentTimeMillis();
                if(acceptanceComplete && resizeStage>=3&&!legacyPreview&&baselineAt<0){
                    legacyBaseline(client,screen);
                }
                if(acceptanceComplete && legacyPreview && !closed &&
                        System.currentTimeMillis()-endedAt>1200) {
                    closed=physicalKey(screen,PacketType.CLOSE_REQUEST);
                    closedAt=System.currentTimeMillis();
                }
                if(closed && !reopened && screen!=original && screen.renderedFrames()>3) {
                    reopened=true;reopenScreens++;
                    capture(client,"hyperdog-phase05-after-reseat.png");
                }
            }
            write(client);
            if(reopened&&screenshots.size()>=5&&System.currentTimeMillis()-closedAt>4500)
                client.scheduleStop();
        }catch(Exception failure){
            error=failure.toString();
            write(client);
            client.scheduleStop();
        }
    }
    private static void write(MinecraftClient client){
        JsonObject j=new JsonObject();
        j.addProperty("connected",client.player!=null&&client.world!=null);
        j.addProperty("handshake",PiriJugglerClient.canUseSlot());
        j.addProperty("completedRounds",rounds.size());
        j.addProperty("stopRequests",stops);j.addProperty("betRequests",betClicks);
        j.addProperty("leverRequests",leverClicks);
        j.addProperty("mouseStops",mouseStops);j.addProperty("keyboardStops",keyboardStops);
        j.addProperty("renderedFrames",frames);
        j.addProperty("rightOnFrames",rightOn);j.addProperty("rightOffFrames",rightOff);
        j.addProperty("displayLockFrames",displayLockFrames);
        j.addProperty("resizeStage",resizeStage);
        j.addProperty("closeSent",closed);j.addProperty("reopened",reopened);
        j.addProperty("reopenScreenCount",reopenScreens);
        j.addProperty("legacyComparison",legacyPreview);
        j.addProperty("complete",acceptanceComplete);
        j.add("stages",new Gson().toJsonTree(stages));
        j.add("lamps",new Gson().toJsonTree(activeLamps));
        j.add("displays",new Gson().toJsonTree(observedDisplays));
        j.add("screenTypes",new Gson().toJsonTree(screenNames));
        j.add("images",new Gson().toJsonTree(screenshots));
        j.add("textures",new Gson().toJsonTree(loadedTextures));
        j.add("rounds",rounds);
        j.addProperty("error",error);
        AtomicTestEvidence.write(Path.of(System.getProperty("piri.runtime.clientResult")),j.toString());
    }
    private HyperDogPhase05Probe(){}
}
