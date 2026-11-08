package jp.pirijuggler.runtime.client;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.Reel;
import jp.pirijuggler.fabric.PiriJugglerClient;
import jp.pirijuggler.fabric.ui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import java.nio.file.*;
import java.util.*;

/** Actual Fabric -> Paper BET/LEVER/STOP simulation via production UI and packets. */
public final class HyperDogPhase03Probe {
    private static final int TARGET=100;
    private static final String[] SYMBOLS={"red7","rainbow7","bar","grape","replay","cherry","watermelon"};
    private static final PacketType[][] ORDERS={
        {PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT},
        {PacketType.STOP_LEFT,PacketType.STOP_RIGHT,PacketType.STOP_CENTER},
        {PacketType.STOP_CENTER,PacketType.STOP_LEFT,PacketType.STOP_RIGHT},
        {PacketType.STOP_CENTER,PacketType.STOP_RIGHT,PacketType.STOP_LEFT},
        {PacketType.STOP_RIGHT,PacketType.STOP_LEFT,PacketType.STOP_CENTER},
        {PacketType.STOP_RIGHT,PacketType.STOP_CENTER,PacketType.STOP_LEFT}
    };
    private static final JsonArray rounds=new JsonArray();
    private static final Set<String> pngs=new LinkedHashSet<>();
    private static UUID active;
    private static String lastCommandKey="",error="";
    private static int lastSentMask=-1,stopRequests,betRequests,leverRequests,rendered;
    private static long finalAt=-1;
    private static boolean imageCheck,screenshot;
    private static long initAt=System.currentTimeMillis();
    public static boolean enabled(){
        return "hyperdog03-main".equals(System.getProperty("piri.runtime.scenario",""));
    }
    public static void tick(MinecraftClient client){
        try{
            if(System.currentTimeMillis()-initAt>360_000&&rounds.size()<TARGET)
                throw new IllegalStateException("Phase03 Fabric timeout, completed="+rounds.size());
            if(!imageCheck&&client.world!=null){
                imageCheck=true;
                for(String s:SYMBOLS){
                    var id=Identifier.of("piri","textures/gui/original_at/symbols/"+s+".png");
                    if(client.getResourceManager().getResource(id).isPresent())pngs.add(s);
                }
                if(pngs.size()!=SYMBOLS.length)
                    throw new IllegalStateException("missing HYPER_DOG textures "+pngs);
            }
            var reels=PiriJugglerClient.hyperDogReels();
            if(client.currentScreen instanceof HyperDogReelPreviewScreen screen){
                rendered=Math.max(rendered,screen.renderedFrames());
                if(reels.spinning()){
                    if(!reels.spinId().equals(active)){
                        active=reels.spinId();lastSentMask=-1;
                    }
                    if(!reels.complete()){
                        PacketType next=nextStop(reels);
                        if(next!=null && lastSentMask!=reels.stoppedMask()
                                && screen.requestStop(next)){
                            stopRequests++;lastSentMask=reels.stoppedMask();
                        }
                    }else if(!contains(reels.spinId())){
                        JsonObject row=new JsonObject();
                        row.addProperty("spinId",reels.spinId().toString());
                        JsonArray stops=new JsonArray(),resources=new JsonArray();
                        for(Reel r:Reel.values()){
                            stops.add(reels.stopIndex(r));
                            for(int y=-1;y<=1;y++){
                                String path=reels.texturePath(r,y);
                                Identifier id=Identifier.of("piri","textures/"+path);
                                if(client.getResourceManager().getResource(id).isEmpty())
                                    throw new IllegalStateException("missing actual reel symbol "+id);
                                resources.add(path);
                            }
                        }
                        row.add("stops",stops);row.add("visibleTextures",resources);
                        rounds.add(row);
                    }
                }
                JsonObject state=PiriJugglerClient.session().publicState();
                if(state!=null&&rounds.size()<TARGET){
                    String gameState=state.get("gameState").getAsString();
                    String key=gameState+"@"+state.get("gameCount").getAsLong();
                    if(("SEATED_READY".equals(gameState)||"NORMAL_BETTED".equals(gameState)||
                            "REPLAY_READY".equals(gameState))
                            && (!reels.spinning()||reels.complete())
                            && !key.equals(lastCommandKey)){
                        if(screen.requestPlay()){
                            lastCommandKey=key;
                            if("NORMAL_BETTED".equals(gameState)||"REPLAY_READY".equals(gameState))
                                leverRequests++;
                            else betRequests++;
                        }
                    }
                }
                if(rounds.size()==TARGET&&finalAt<0)finalAt=System.currentTimeMillis();
                if(finalAt>=0&&!screenshot&&screen.renderedFrames()>2){
                    screenshot=true;
                    ScreenshotRecorder.saveScreenshot(client.runDirectory,
                            "hyperdog-phase03-normal-100-games.png",client.getFramebuffer(),text->{});
                }
            }
            write(client);
            if(finalAt>=0&&screenshot&&System.currentTimeMillis()-finalAt>8000)
                client.scheduleStop();
        }catch(Exception ex){
            error=ex.toString();
            write(client);
            client.scheduleStop();
        }
    }
    private static PacketType nextStop(HyperDogClientReels reels){
        PacketType[] order=ORDERS[rounds.size()%ORDERS.length];
        for(PacketType t:order)if(reels.canStop(t))return t;
        return null;
    }
    private static boolean contains(UUID spin){
        for(JsonElement e:rounds)
            if(spin.toString().equals(e.getAsJsonObject().get("spinId").getAsString()))return true;
        return false;
    }
    private static void write(MinecraftClient client){
        JsonObject j=new JsonObject();
        j.addProperty("connected",client.world!=null&&client.player!=null);
        j.addProperty("handshake",PiriJugglerClient.canUseSlot());
        j.addProperty("completedRounds",rounds.size());j.addProperty("betRequests",betRequests);
        j.addProperty("leverRequests",leverRequests);j.addProperty("stopRequests",stopRequests);
        j.addProperty("renderedFrames",rendered);j.addProperty("screenshot",screenshot);
        j.addProperty("error",error);j.add("textures",new Gson().toJsonTree(pngs));
        j.add("rounds",rounds);
        try{
            Path path=Path.of(System.getProperty("piri.runtime.clientResult"));
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path,j.toString());
        }catch(java.nio.file.FileSystemException sharing){/* next tick */}
        catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
    }
    private HyperDogPhase03Probe(){}
}
