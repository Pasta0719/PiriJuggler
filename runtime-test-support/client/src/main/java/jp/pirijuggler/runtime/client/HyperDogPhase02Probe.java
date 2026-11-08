package jp.pirijuggler.runtime.client;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.reel.Reel;
import jp.pirijuggler.fabric.PiriJugglerClient;
import jp.pirijuggler.fabric.ui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import java.nio.file.*;
import java.util.*;

/** Observes the real production Fabric reel model and drives real C2S screen inputs. */
public final class HyperDogPhase02Probe {
    private static final int TARGET=57;
    private static final String[] TEXTURES={"red7","rainbow7","bar","grape","replay","cherry","watermelon"};
    private static final PacketType[][] ORDERS={
        {PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT},
        {PacketType.STOP_LEFT,PacketType.STOP_RIGHT,PacketType.STOP_CENTER},
        {PacketType.STOP_CENTER,PacketType.STOP_LEFT,PacketType.STOP_RIGHT},
        {PacketType.STOP_CENTER,PacketType.STOP_RIGHT,PacketType.STOP_LEFT},
        {PacketType.STOP_RIGHT,PacketType.STOP_LEFT,PacketType.STOP_CENTER},
        {PacketType.STOP_RIGHT,PacketType.STOP_CENTER,PacketType.STOP_LEFT}
    };
    private static final JsonArray completed=new JsonArray();
    private static UUID activeSpin;
    private static int sentMask=-1,totalInputs,renderedFrames;
    private static String error="";
    private static boolean screenshot,checkedAssets;
    private static final Set<String> available=new LinkedHashSet<>();
    private static long finalRoundCompletedAt=-1L;

    public static boolean enabled(){return "hyperdog02-main".equals(System.getProperty("piri.runtime.scenario"));}
    public static void tick(MinecraftClient client){
        try{
            var reels=PiriJugglerClient.hyperDogReels();
            if(!checkedAssets&&client.world!=null){
                checkedAssets=true;
                for(String symbol:TEXTURES){
                    Identifier id=Identifier.of("piri","textures/gui/original_at/symbols/"+symbol+".png");
                    if(client.getResourceManager().getResource(id).isPresent())available.add(symbol);
                }
                if(available.size()!=TEXTURES.length)error="missing approved images: "+available;
            }
            if(reels.spinning()){
                if(!reels.spinId().equals(activeSpin)){
                    activeSpin=reels.spinId();
                    sentMask=-1;
                }
                if(client.currentScreen instanceof HyperDogReelPreviewScreen screen){
                    renderedFrames=Math.max(renderedFrames,screen.renderedFrames());
                    if(!reels.complete() && !reels.presentation().equals("AT_RED7_CONFIRM")){
                        PacketType next=nextStop(reels);
                        if(next!=null && sentMask!=reels.stoppedMask() && screen.requestStop(next)){
                            totalInputs++;sentMask=reels.stoppedMask();
                        }
                    }
                    if(reels.complete() && !containsSpin(reels.spinId())){
                        JsonObject round=new JsonObject();
                        round.addProperty("spinId",reels.spinId().toString());
                        round.addProperty("presentation",reels.presentation());
                        JsonArray stops=new JsonArray(),paths=new JsonArray();
                        for(Reel reel:Reel.values()){
                            stops.add(reels.stopIndex(reel));
                            for(int row=-1;row<=1;row++){
                                String path=reels.texturePath(reel,row);
                                paths.add(path);
                                Identifier id=Identifier.of("piri","textures/"+path);
                                if(client.getResourceManager().getResource(id).isEmpty())
                                    throw new IllegalStateException("missing displayed PNG "+id);
                            }
                        }
                        round.add("stops",stops);round.add("visibleTextures",paths);
                        completed.add(round);
                    }
                    if(completed.size()==TARGET && finalRoundCompletedAt<0L)
                        finalRoundCompletedAt=System.currentTimeMillis();
                    if(completed.size()==TARGET && !screenshot && screen.renderedFrames()>2){
                        screenshot=true;
                        ScreenshotRecorder.saveScreenshot(client.runDirectory,"hyperdog-phase02-57-rounds.png",client.getFramebuffer(),text->{});
                    }
                }
            }
            write(client);
            // Paper finalizes each round after its dwell interval. Do not exit
            // as soon as the 3rd packet arrives; leave the server time to persist
            // the 57th result and leave screenshot capture time to complete.
            if(completed.size()==TARGET && screenshot && finalRoundCompletedAt>=0L
                    && System.currentTimeMillis()-finalRoundCompletedAt>=6000){
                // Client is safe to exit. Paper fixture remains the source of all role evidence.
                client.scheduleStop();
            }
        }catch(Exception failure){
            error=failure.toString();
            write(client);
            client.scheduleStop();
        }
    }
    private static boolean containsSpin(UUID id){
        for(JsonElement e:completed)if(id.toString().equals(e.getAsJsonObject().get("spinId").getAsString()))return true;
        return false;
    }
    private static PacketType nextStop(HyperDogClientReels reels){
        if("CZ".equals(reels.presentation())){
            return reels.canStop(PacketType.STOP_CENTER)?PacketType.STOP_CENTER:null;
        }
        PacketType[] order=ORDERS[(completed.size()/12)%ORDERS.length];
        for(PacketType type:order)if(reels.canStop(type))return type;
        return null;
    }
    private static void write(MinecraftClient client){
        var result=new JsonObject();
        result.addProperty("connected",client.player!=null&&client.world!=null);
        result.addProperty("handshake",PiriJugglerClient.canUseSlot());
        result.addProperty("completedRounds",completed.size());
        result.addProperty("inputRequests",totalInputs);
        result.addProperty("renderedFrames",renderedFrames);
        result.addProperty("screenshotRequested",screenshot);
        result.addProperty("error",error);
        result.add("availableTextures",new Gson().toJsonTree(available));
        result.add("rounds",completed);
        try{
            Path dest=Path.of(System.getProperty("piri.runtime.clientResult"));
            Files.createDirectories(dest.toAbsolutePath().getParent());
            Files.writeString(dest,result.toString());
        }catch(java.nio.file.FileSystemException locked){
            // Python monitor may concurrently read, next tick will retry.
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    private HyperDogPhase02Probe(){}
}
