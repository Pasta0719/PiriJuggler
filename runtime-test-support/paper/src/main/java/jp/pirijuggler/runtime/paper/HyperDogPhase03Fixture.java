package jp.pirijuggler.runtime.paper;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.game.hyperdog.HyperDogNormalGame;
import jp.pirijuggler.paper.reel.HyperDogStopSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/**
 * Real 1.21 Paper plugin-message drive for the Phase03 server-owned normal
 * engine. The dedicated test fixture is NEVER present in production.
 * The production MachineService cannot sell/seat the unfinished type.
 */
public final class HyperDogPhase03Fixture {
    private static final int MACHINE=772, TARGET=100, SETTING=3;
    private static final long SEED=20261108L;
    private final JavaPlugin plugin;
    private final UUID session=UUID.randomUUID();
    private final Path resultPath=Path.of(System.getProperty("piri.runtime.serverResult"));
    private final JsonArray completed=new JsonArray();
    private HyperDogNormalGame game=new HyperDogNormalGame(SETTING,SEED,10000);
    private HyperDogStopSession round;
    private HyperDogNormalGame.Outcome roundOutcome;
    private Player player;
    private long nextSequence=1,lastFinish,openedAt,roundStarted;
    private int rejected,acceptedBet,acceptedLever,acceptedStops,replays,atStarts,restorations;
    private boolean ready,open,finished;

    public HyperDogPhase03Fixture(JavaPlugin plugin) {
        this.plugin=Objects.requireNonNull(plugin);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,Protocol.CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,Protocol.CHANNEL,this::onPacket);
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
        plugin.getLogger().info("HYPER_DOG_PHASE03_FIXTURE_READY");
    }
    private void onPacket(String channel,Player sender,byte[] bytes) {
        if(!ready||player==null||!sender.getUniqueId().equals(player.getUniqueId()))return;
        Envelope packet;
        try{packet=EnvelopeCodec.decode(bytes);}
        catch(RuntimeException invalid){rejected++;return;}
        PacketType type=packet.packetType();
        if(type!=PacketType.SPACE_ACTION &&
           type!=PacketType.STOP_LEFT && type!=PacketType.STOP_CENTER && type!=PacketType.STOP_RIGHT)return;
        JsonObject body=packet.payload();
        long seq=-1;
        try{
            if(!body.has("sessionId")||!body.has("machineId")||!body.has("clientSequence") ||
               !session.toString().equals(body.get("sessionId").getAsString()) ||
               body.get("machineId").getAsInt()!=MACHINE)throw new IllegalArgumentException("session");
            seq=body.get("clientSequence").getAsLong();
            if(seq!=nextSequence){rejected++;send(ErrorPackets.rejected(seq,ErrorCode.SEQUENCE_OLD));return;}
            if(type==PacketType.SPACE_ACTION && round==null && !finished){
                if(System.currentTimeMillis()-lastFinish<350)return;
                if(game.canBet()){
                    game.bet();acceptedBet++;nextSequence++;
                    accepted(type,seq);sendPublic();
                }else if(game.canLever()){
                    roundOutcome=game.lever();
                    roundStarted=System.currentTimeMillis();
                    round=new HyperDogStopSession(session,MACHINE,UUID.randomUUID(),
                            roundOutcome.reelRole(),roundStarted,new double[]{1,8,15});
                    acceptedLever++;nextSequence++;
                    accepted(type,seq);send(round.begin());sendPublic();
                }else {rejected++;send(ErrorPackets.rejected(seq,ErrorCode.INVALID_STATE));}
            }else if(round!=null &&
                     (type==PacketType.STOP_LEFT||type==PacketType.STOP_CENTER||type==PacketType.STOP_RIGHT)){
                if(System.currentTimeMillis()-roundStarted<650)return;
                for(Envelope response:round.receive(packet,System.currentTimeMillis())){
                    if(response.packetType()==PacketType.ACTION_ACCEPTED){
                        acceptedStops++;nextSequence++;
                    }else if(response.packetType()==PacketType.ACTION_REJECTED)rejected++;
                    send(response);
                }
                if(round.complete())finishRound();
                else if(completed.size()==19 && restorations==0 && round.snapshot().round().history().count()==1){
                    // Simulate process-object destruction/reconstruction in the
                    // middle of a real network round; verify no role/seed is redrawn.
                    JsonObject full=game.snapshot();
                    JsonObject physical=round.toJson();
                    game=HyperDogNormalGame.restore(full);
                    round=HyperDogStopSession.fromJson(physical);
                    for(Envelope replay:round.replayOnReconnect())send(replay);
                    restorations++;
                }
            }else {
                rejected++;send(ErrorPackets.rejected(seq,ErrorCode.INVALID_STATE));
            }
        }catch(RuntimeException error){
            rejected++;
            plugin.getLogger().warning("HYPER_DOG_PHASE03_INVALID "+error);
            send(ErrorPackets.rejected(seq,ErrorCode.INVALID_STATE));
        }
        write();
    }
    private void finishRound(){
        HyperDogNormalGame.Outcome result=game.finish();
        if(result.serial()!=completed.size()+1||!roundOutcome.equals(result))
            throw new IllegalStateException("mismatched normal game/physical outcome");
        JsonObject row=new JsonObject();
        row.addProperty("number",result.serial());
        row.addProperty("role",result.role().name()); // server-side evidence only
        row.addProperty("bet",result.bet());row.addProperty("payout",result.payout());
        row.addProperty("modeBefore",result.modeBefore().name());
        row.addProperty("modeAfter",result.modeAfter().name());
        row.addProperty("trigger",result.trigger());row.addProperty("hit",result.hit().name());
        row.addProperty("spinId",round.snapshot().round().spinId().toString());
        row.add("stops",new Gson().toJsonTree(round.snapshot().round().history().stops()));
        if(result.role()==HyperDogNormalGame.Role.REPLAY)replays++;
        if(result.entersAt()){
            atStarts++;
            row.addProperty("savedHitMode",game.savedHitMode().name());
            // Standalone Phase03 acceptance tests the saved entry mode and
            // exit mode distribution. Real AT/CZ accounting belongs to Phase04.
            row.addProperty("testResumeMode",game.resumeAfterAt().name());
        }
        row.addProperty("credit",game.credits());
        completed.add(row);
        round=null;roundOutcome=null;lastFinish=System.currentTimeMillis();
        if(completed.size()==TARGET)finished=true;
        // Allow the last physical symbol-stop animation to finish before
        // advertising that the next BET/LEVER can be accepted.
        Bukkit.getScheduler().runTaskLater(plugin,this::sendPublic,8L);
    }
    private void tick(){
        try{
            if(player==null||!player.isOnline()){
                player=Bukkit.getPlayerExact("PiriRuntimeTest");
                if(player==null||!player.isOnline()){write();return;}
            }
            var prod=(PiriJugglerPlugin)Bukkit.getPluginManager().getPlugin("PiriJuggler");
            if(prod==null||!prod.isEnabled()||!prod.canUseSlot(player.getUniqueId())){write();return;}
            if(!ready){ready=true;openedAt=System.currentTimeMillis();lastFinish=openedAt-500;sendOpen();sendPublic();}
            write();
        }catch(RuntimeException error){
            plugin.getLogger().severe("HYPER_DOG_PHASE03_FATAL "+error);
            throw error;
        }
    }
    private void sendOpen(){
        JsonObject j=new JsonObject();
        j.addProperty("sessionId",session.toString());j.addProperty("machineId",MACHINE);
        j.addProperty("machineType","HYPER_DOG");
        j.addProperty("expectedNextClientSequence",nextSequence);
        send(Envelope.current(PacketType.OPEN_MACHINE,j));open=true;
    }
    private void sendPublic(){
        JsonObject j=game.publicState();
        j.addProperty("sessionId",session.toString());
        j.addProperty("machineId",MACHINE);
        j.addProperty("machineType","HYPER_DOG");
        j.addProperty("expectedNextClientSequence",nextSequence);
        send(Envelope.current(PacketType.PUBLIC_STATE,j));
    }
    private void accepted(PacketType type,long seq){
        JsonObject j=new JsonObject();j.addProperty("clientSequence",seq);
        j.addProperty("action",type.name());send(Envelope.current(PacketType.ACTION_ACCEPTED,j));
    }
    private void send(Envelope packet){
        if(player!=null&&player.isOnline())
            player.sendPluginMessage(plugin,Protocol.CHANNEL,EnvelopeCodec.encode(packet));
    }
    private void write(){
        JsonObject out=new JsonObject();
        out.addProperty("ready",ready);out.addProperty("opened",open);
        out.addProperty("complete",finished);out.addProperty("completedRounds",completed.size());
        out.addProperty("acceptedBets",acceptedBet);out.addProperty("acceptedLevers",acceptedLever);
        out.addProperty("acceptedStops",acceptedStops);out.addProperty("rejected",rejected);
        out.addProperty("replays",replays);out.addProperty("atStarts",atStarts);
        out.addProperty("snapshotRestorations",restorations);
        out.addProperty("credit",game.credits());out.addProperty("totalBet",game.totalBet());
        out.addProperty("totalPayout",game.totalPayout());
        out.addProperty("setting",game.setting());
        out.add("normalSnapshot",game.snapshot()); // NEVER sent on network
        out.add("rounds",completed);
        try{
            Files.createDirectories(resultPath.toAbsolutePath().getParent());
            Files.writeString(resultPath,out.toString());
        }catch(java.nio.file.FileSystemException locked){
            // Retry writing after external Windows/CI monitor releases its reader.
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
}
