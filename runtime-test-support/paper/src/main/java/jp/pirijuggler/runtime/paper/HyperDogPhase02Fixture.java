package jp.pirijuggler.runtime.paper;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.reel.HyperDogStopSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Dedicated real Paper/Fabric Phase02 transport fixture; no production machine or economy mutations. */
public final class HyperDogPhase02Fixture {
    private static final int MACHINE=771, TOTAL_ROUNDS=57;
    private final JavaPlugin plugin;
    private final UUID sessionId=UUID.randomUUID();
    private final JsonArray rounds=new JsonArray();
    private HyperDogStopSession current;
    private int started, finished, manualStops, automaticStops, rejected;
    private boolean ready, restoredOnce;
    private Player player;
    private long lastFinish, spinStarted;
    private final Path output;

    public HyperDogPhase02Fixture(JavaPlugin plugin) {
        this.plugin=plugin;
        output=Path.of(System.getProperty("piri.runtime.serverResult"));
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,Protocol.CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,Protocol.CHANNEL,(channel,p,bytes)->receive(p,bytes));
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
        plugin.getLogger().info("HYPER_DOG_PHASE02_FIXTURE_READY Paper real network");
    }
    private void receive(Player p,byte[] bytes) {
        if(player==null||!p.getUniqueId().equals(player.getUniqueId())||current==null||current.complete())return;
        try {
            Envelope input=EnvelopeCodec.decode(bytes);
            if(!input.packetType().isClientToServer() || input.packetType().isReserved())return;
            if(!EnumSet.of(PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT,PacketType.SPACE_ACTION).contains(input.packetType()))return;
            for(Envelope response:current.receive(input,System.currentTimeMillis())) {
                if(response.packetType()==PacketType.REEL_STOP) {
                    manualStops++;
                } else if(response.packetType()==PacketType.ACTION_REJECTED) {
                    rejected++;
                }
                send(response);
            }
        }catch(RuntimeException problem){
            plugin.getLogger().warning("HYPER_DOG_RUNTIME_REJECT "+problem);
            rejected++;
        }
    }
    private void tick() {
        long now=System.currentTimeMillis();
        if(player==null||!player.isOnline()){
            player=Bukkit.getPlayerExact("PiriRuntimeTest");
            if(player==null||!player.isOnline()){write();return;}
        }
        var production=(PiriJugglerPlugin)Bukkit.getPluginManager().getPlugin("PiriJuggler");
        if(production==null||!production.isEnabled()||!production.canUseSlot(player.getUniqueId())){write();return;}
        if(!ready){
            ready=true;lastFinish=now;
            JsonObject open=new JsonObject();
            open.addProperty("sessionId",sessionId.toString());
            open.addProperty("machineId",MACHINE);
            open.addProperty("machineType","HYPER_DOG");
            open.addProperty("expectedNextClientSequence",1);
            send(Envelope.current(PacketType.OPEN_MACHINE,open));
        }
        if(current==null && started<TOTAL_ROUNDS && now-lastFinish>=400)start(now);
        if(current!=null) {
            for(Envelope packet:current.tick(now)) {
                if(packet.packetType()==PacketType.REEL_STOP)automaticStops++;
                send(packet);
            }
            // Force an actual snapshot restoration after the first manual stop of a
            // normal role. All replayed packets travel again over real Minecraft network.
            if(started==6 && !restoredOnce && current.snapshot().round().history().count()>=1){
                JsonObject durable=current.toJson();
                current=HyperDogStopSession.fromJson(durable);
                for(Envelope packet:current.replayOnReconnect())send(packet);
                restoredOnce=true;
            }
            if(current.complete() && now-spinStarted>=1100) {
                var state=current.snapshot().round();
                int[] stops=state.history().stops();
                if(!HyperDogReelControl.legalFinal(state.role(),stops))
                    throw new AssertionError("bad Paper HYPER_DOG stop "+state.role());
                JsonObject record=new JsonObject();
                record.addProperty("index",finished);
                record.addProperty("spinId",state.spinId().toString());
                record.addProperty("role",state.role().name()); // test server report only, NEVER sent to Fabric
                record.add("stops",new Gson().toJsonTree(stops));
                record.addProperty("lastClientSequence",state.lastSequence());
                rounds.add(record);
                current=null;finished++;lastFinish=now;
            }
        }
        write();
    }
    private void start(long now){
        int i=started;
        HyperDogReelControl.Role role=HyperDogReelControl.Role.values()[i%HyperDogReelControl.Role.values().length];
        double[] phases={i%21,(i*5)%21,(i*11)%21};
        current=new HyperDogStopSession(sessionId,MACHINE,UUID.randomUUID(),role,now,phases);
        spinStarted=now;
        started++;
        send(current.begin());
    }
    private void send(Envelope packet){
        if(player!=null&&player.isOnline())player.sendPluginMessage(plugin,Protocol.CHANNEL,EnvelopeCodec.encode(packet));
    }
    private void write(){
        JsonObject result=new JsonObject();
        result.addProperty("ready",ready);
        result.addProperty("complete",finished==TOTAL_ROUNDS);
        result.addProperty("startedRounds",started);
        result.addProperty("completedRounds",finished);
        result.addProperty("manualStops",manualStops);
        result.addProperty("automaticStops",automaticStops);
        result.addProperty("rejected",rejected);
        result.addProperty("snapshotRestored",restoredOnce);
        result.add("rounds",rounds);
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output,result.toString());
        }catch(java.nio.file.FileSystemException conflict){
            plugin.getLogger().fine("HYPER_DOG fixture temporary result file lock "+conflict);
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
}
