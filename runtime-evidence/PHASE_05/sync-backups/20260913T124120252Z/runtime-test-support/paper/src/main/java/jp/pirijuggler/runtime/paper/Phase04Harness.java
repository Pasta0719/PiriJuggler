package jp.pirijuggler.runtime.paper;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.reel.*;
import jp.pirijuggler.paper.threading.MainThread;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Test-only controller: real wire input invokes the production reel engine, with no RNG/money. */
public final class Phase04Harness {
    private static Phase04Harness instance;
    private final JavaPlugin helper;private final PiriJugglerPlugin production;
    private final JsonArray rounds=new JsonArray();private final MainThread main=new MainThread(){public boolean isMainThread(){return Bukkit.isPrimaryThread();}public void execute(Runnable r){Bukkit.getScheduler().runTask(helper,r);}};
    private long completed,lastSequence;private ReelRound round;private JsonObject current;private StopTriplet display=new StopTriplet(0,0,0);private DisplayRole role;
    public Phase04Harness(JavaPlugin helper){
        instance=this;this.helper=helper;production=(PiriJugglerPlugin)Bukkit.getPluginManager().getPlugin("PiriJuggler");
        // Phase05 supplies the real game controller. This optional test module routes only the
        // test round's STOPs into the production engine; all other traffic uses the original handler.
        Bukkit.getMessenger().unregisterIncomingPluginChannel(production,Protocol.CHANNEL);
        Bukkit.getMessenger().registerOutgoingPluginChannel(helper,Protocol.CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(helper,Protocol.CHANNEL,(channel,player,bytes)->{
            Envelope input=EnvelopeCodec.decode(bytes);
            if(round!=null&&Set.of(PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT,PacketType.SPACE_ACTION).contains(input.packetType()))stop(player,input);
            else production.onPluginMessageReceived(channel,player,bytes);
        });
        Bukkit.getScheduler().runTaskTimer(helper,this::tick,5,5);
    }
    private void tick(){
        Path file=Path.of(System.getProperty("piri.runtime.serverResult")).resolveSibling("round-"+(completed+1)+".json");
        try {if(!Files.exists(file))return;var p=Bukkit.getPlayerExact("PiriRuntimeTest");if(p==null||!production.canUseSlot(p.getUniqueId()))return;
            var session=production.machines().snapshot().session(p.getUniqueId());if(session==null)return;
            var request=JsonParser.parseString(Files.readString(file)).getAsJsonObject();role=DisplayRole.valueOf(request.get("role").getAsString());boolean premium=request.get("premiumF").getAsBoolean();var profile=ReelMotion.Profile.valueOf(request.get("profile").getAsString());
            UUID spin=UUID.randomUUID();String mode=role==DisplayRole.BIG_ENTRY||role==DisplayRole.REG_ENTRY?"BONUS_ENTRY":"NORMAL";
            double[] starts={display.left(),display.center(),display.right()};
            round=new ReelRound(production.reels().solver(),new ReelRound.Identity(p.getUniqueId(),session.id(),session.machine(),spin),role,premium,profile,mode,starts,display,0,lastSequence,main);
            current=request.deepCopy();current.addProperty("spinId",spin.toString());current.addProperty("sessionId",session.id().toString());current.add("stops",new JsonArray());current.addProperty("complete",false);rounds.add(current);
            var state=session.publicState();state.addProperty("gameState",role==DisplayRole.BIG_ENTRY?"BONUS_ENTRY_SPINNING_BIG":role==DisplayRole.REG_ENTRY?"BONUS_ENTRY_SPINNING_REG":"NORMAL_SPINNING");state.addProperty("lampOn",mode.equals("BONUS_ENTRY"));state.addProperty("expectedNextClientSequence",lastSequence+1);state.addProperty("stoppedMask",0);state.add("displayStops",stops(display));send(p,Envelope.current(PacketType.PUBLIC_STATE,state));
            send(p,round.begin(System.nanoTime()));completed++;
        }catch(Exception error){throw new IllegalStateException("Phase04 test round failed",error);}
    }
    private void stop(Player player,Envelope request){
        if(!production.canUseSlot(player.getUniqueId()))throw new IllegalStateException("Production handshake denied");
        var result=round.receive(player.getUniqueId(),request,System.nanoTime(),player.getPing());lastSequence=round.lastSequence();
        JsonObject observation=new JsonObject();observation.addProperty("packetType",request.packetType().name());observation.add("request",request.payload());observation.addProperty("ping",player.getPing());observation.addProperty("accepted",result.accepted());
        observation.addProperty("tenpaiSound",result.tenpaiSound());observation.addProperty("actualTenpaiLines",result.actualTenpaiLines());
        JsonArray replies=new JsonArray();for(var packet:result.packets()){send(player,packet);var entry=new JsonObject();entry.addProperty("type",packet.packetType().name());entry.add("payload",packet.payload());replies.add(entry);}observation.add("responses",replies);current.getAsJsonArray("stops").add(observation);
        if(result.tenpaiSound()){var sound=new JsonObject();sound.addProperty("spinId",round.identity().spin().toString());send(player,Envelope.current(PacketType.TENPAI_SOUND,sound));}
        if(result.accepted()&&round.stoppedMask()==7){
            display=round.display();current.add("finalStops",stops(display));current.addProperty("strictValid",production.reels().solver().catalogue().evaluation(display).valid(role));current.addProperty("complete",true);
            var session=production.machines().snapshot().session(player.getUniqueId());var state=session.publicState();state.add("displayStops",stops(display));state.addProperty("stoppedMask",7);state.addProperty("expectedNextClientSequence",lastSequence+1);state.addProperty("lampOn",role==DisplayRole.BIG_ENTRY||role==DisplayRole.REG_ENTRY);send(player,Envelope.current(PacketType.PUBLIC_STATE,state));
        }
    }
    private static JsonObject stops(StopTriplet s){var b=new JsonObject();b.addProperty("left",s.left());b.addProperty("center",s.center());b.addProperty("right",s.right());return b;}
    private void send(Player player,Envelope packet){player.sendPluginMessage(helper,Protocol.CHANNEL,EnvelopeCodec.encode(packet));}
    public static JsonObject snapshot(){var b=new JsonObject();if(instance!=null){b.addProperty("completed",instance.completed);b.add("rounds",instance.rounds.deepCopy());b.add("startup",new Gson().toJsonTree(instance.production.reels().verification()));}return b;}
}
