package jp.pirijuggler.runtime.paper;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.HyperDogReelControl;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.game.hyperdog.HyperDogAtGame;
import jp.pirijuggler.paper.reel.HyperDogStopSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/**
 * Test-only, actual Paper 1.21 + Fabric 1.21 Phase04 input/stop/AT/CZ path.
 * Roles are forced ONLY by an isolated test plugin through reflection; no
 * production command or network packet permits overriding gameplay RNG.
 */
public final class HyperDogPhase05Fixture {
    private static final int MACHINE=774, SETTING=3;
    private static final long INITIAL_CREDIT=10000;
    private static final Method FORCE;
    static {
        try{
            FORCE=HyperDogAtGame.class.getDeclaredMethod("leverForced",HyperDogAtGame.Role.class);
            FORCE.setAccessible(true);
        }catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
    }
    private static HyperDogAtGame.Round force(HyperDogAtGame game,HyperDogAtGame.Role role){
        try{return (HyperDogAtGame.Round)FORCE.invoke(game,role);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("test role injection failed",e);}
    }
    private static void runOne(HyperDogAtGame game,HyperDogAtGame.Role role){
        if(game.waitingForBet())game.bet();
        force(game,role);game.finish();
    }
    /** Find a reproducible first-upper CZ failure without changing its real 50% logic. */
    private static long selectUpperLossSeed(){
        for(long seed=1;seed<1000;seed++){
            var test=new HyperDogAtGame(SETTING,seed,INITIAL_CREDIT,0,0,true);
            test.confirmRed7();
            for(int i=0;i<5;i++)runOne(test,HyperDogAtGame.Role.MISS);
            int max=0;
            while(test.stage()==HyperDogAtGame.Stage.UPPER_AT&&max++<2000)
                runOne(test,HyperDogAtGame.Role.GRAPE);
            if(test.stage()!=HyperDogAtGame.Stage.UPPER_CZ)continue;
            for(int i=0;i<5 && test.stage()==HyperDogAtGame.Stage.UPPER_CZ;i++)
                runOne(test,HyperDogAtGame.Role.MISS);
            if(test.stage()==HyperDogAtGame.Stage.COMPLETE)return seed;
        }
        throw new IllegalStateException("no deterministic upper CZ loss seed");
    }

    private final JavaPlugin plugin;
    private final UUID session=UUID.randomUUID();
    private final Path resultPath=Path.of(System.getProperty("piri.runtime.serverResult"));
    private final JsonArray completed=new JsonArray();
    private final long upperSeed=selectUpperLossSeed();
    private HyperDogAtGame game=new HyperDogAtGame(SETTING,upperSeed,INITIAL_CREDIT,0,0,true);
    private HyperDogStopSession round;
    private HyperDogAtGame.Round outcome;
    private Player player;
    private long nextSequence=1,lastFinish,roundStarted;
    private int rejected,acceptedBet,acceptedLever,manualStops,automaticStops,restorations;
    private int lowerTicketsIssued,czSuccesses,czFailures,upperRounds,lowerRounds;
    private int episode; // 0: upper -> loss; 1: lower -> guaranteed CZ -> upper
    private long lastAward,upperInitialTotal;
    private int closeRequests,reseats;
    private int lastSourceFree,resultsPublished;
    private final JsonArray displayEvents=new JsonArray();
    private boolean ready,opened,finished;

    public HyperDogPhase05Fixture(JavaPlugin plugin){
        this.plugin=Objects.requireNonNull(plugin);
        game.confirmRed7();
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,Protocol.CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin,Protocol.CHANNEL,this::onPacket);
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
        plugin.getLogger().info("HYPER_DOG_PHASE05_FIXTURE_READY upperSeed="+upperSeed);
    }
    private HyperDogAtGame.Role scripted(){
        return switch(game.stage()){
            case LOWER_INITIAL, UPPER_INITIAL, UPPER_CZ -> HyperDogAtGame.Role.MISS;
            case UPPER_AT -> HyperDogAtGame.Role.GRAPE;
            case LOWER_AT -> lowerTicketsIssued<3?HyperDogAtGame.Role.STRONG_CHANCE:
                    HyperDogAtGame.Role.GRAPE;
            default -> throw new IllegalStateException("not a spin stage "+game.stage());
        };
    }
    private void onPacket(String channel,Player sender,byte[] data){
        if(!ready||player==null||!sender.getUniqueId().equals(player.getUniqueId()))return;
        Envelope packet;
        try{packet=EnvelopeCodec.decode(data);}
        catch(RuntimeException bad){rejected++;write();return;}
        PacketType type=packet.packetType();
        if(type!=PacketType.SPACE_ACTION && type!=PacketType.CLOSE_REQUEST && type!=PacketType.STOP_LEFT &&
           type!=PacketType.STOP_CENTER && type!=PacketType.STOP_RIGHT)return;
        long seq=-1;
        try{
            JsonObject body=packet.payload();
            if(!body.has("sessionId")||!body.has("machineId")||!body.has("clientSequence")||
               !session.toString().equals(body.get("sessionId").getAsString())||
               body.get("machineId").getAsInt()!=MACHINE)
                throw new IllegalArgumentException("session");
            seq=body.get("clientSequence").getAsLong();
            if(seq!=nextSequence) {
                rejected++;send(ErrorPackets.rejected(seq,ErrorCode.SEQUENCE_OLD));return;
            }
            if(type==PacketType.CLOSE_REQUEST && finished && closeRequests==0){
                closeRequests++;nextSequence++;
                accepted(type,seq);
                JsonObject closed=new JsonObject();
                closed.addProperty("sessionId",session.toString());closed.addProperty("machineId",MACHINE);
                send(Envelope.current(PacketType.SESSION_SUSPENDED,closed));
                Bukkit.getScheduler().runTaskLater(plugin,()->{
                    if(player!=null && player.isOnline()){
                        reseats++;sendOpen();sendPublic();
                    }
                },24L);
            }else if(type==PacketType.SPACE_ACTION && round==null && !finished){
                // Never silently discard a C2S play action after the prior
                // physical third stop. The client de-duplicates by state key,
                // so an unacknowledged cooldown could deadlock the entire run.
                if(game.waitingForBet()){
                    game.bet();acceptedBet++;nextSequence++;
                    accepted(type,seq);sendPublic();
                }else if(game.canLever()){
                    HyperDogAtGame.Role selected=scripted();
                    outcome=force(game,selected);
                    roundStarted=System.currentTimeMillis();
                    HyperDogReelControl.Role reelRole=outcome.stage()==HyperDogAtGame.Stage.UPPER_CZ?
                            (outcome.czSuccess()?HyperDogReelControl.Role.CZ_WIN:HyperDogReelControl.Role.CZ_LOSE):
                            HyperDogReelControl.Role.valueOf(outcome.role().name());
                    round=new HyperDogStopSession(session,MACHINE,UUID.randomUUID(),reelRole,
                            roundStarted,new double[]{1,8,15});
                    acceptedLever++;nextSequence++;accepted(type,seq);
                    send(round.begin());sendPublic();
                }else {rejected++;send(ErrorPackets.rejected(seq,ErrorCode.INVALID_STATE));}
            }else if(round!=null &&
                    (type==PacketType.STOP_LEFT||type==PacketType.STOP_CENTER||type==PacketType.STOP_RIGHT)){
                // The production transport validates exact reel stop positions and
                // the CZ center-only 900ms lock on its own.
                for(Envelope response:round.receive(packet,System.currentTimeMillis()))
                    emit(response);
                if(round.complete())finishRound();
                else if(restorations==0&&completed.size()>10&&
                        round.snapshot().round().history().count()==1){
                    JsonObject full=game.snapshot(),physical=round.toJson();
                    game=HyperDogAtGame.restore(full);
                    round=HyperDogStopSession.fromJson(physical);
                    for(Envelope replay:round.replayOnReconnect())send(replay);
                    restorations++;
                }
            }else{
                rejected++;send(ErrorPackets.rejected(seq,ErrorCode.INVALID_STATE));
            }
        }catch(RuntimeException failure){
            rejected++;
            plugin.getLogger().warning("HYPER_DOG_PHASE05_INVALID "+failure);
            send(ErrorPackets.rejected(seq,ErrorCode.INVALID_STATE));
        }finally{write();}
    }
    private void tick(){
        try{
            if(player==null||!player.isOnline()){
                player=Bukkit.getPlayerExact("PiriRuntimeTest");
                if(player==null||!player.isOnline()){write();return;}
            }
            var prod=(PiriJugglerPlugin)Bukkit.getPluginManager().getPlugin("PiriJuggler");
            if(prod==null||!prod.isEnabled()||!prod.canUseSlot(player.getUniqueId())){write();return;}
            if(!ready){
                ready=true;lastFinish=System.currentTimeMillis()-500;
                sendOpen();sendPublic();
            }
            if(round!=null){
                for(Envelope auto:round.tick(System.currentTimeMillis()))emit(auto);
                if(round.complete())finishRound();
            }
            write();
        }catch(RuntimeException err){
            plugin.getLogger().severe("HYPER_DOG_PHASE05_FATAL "+err);
            throw err;
        }
    }
    private void emit(Envelope packet){
        if(packet.packetType()==PacketType.ACTION_ACCEPTED){
            manualStops++;nextSequence++;
        }else if(packet.packetType()==PacketType.ACTION_REJECTED)rejected++;
        else if(packet.packetType()==PacketType.REEL_STOP &&
                packet.payload().has("automatic")&&packet.payload().get("automatic").getAsBoolean())
            automaticStops++;
        send(packet);
    }
    private void finishRound(){
        if(round==null||!round.complete())throw new IllegalStateException("incomplete");
        var actual=game.finish();
        if(!actual.equals(outcome))throw new IllegalStateException("AT result mismatch");
        lastAward=actual.award();
        // Only third-stop committed public awards enter the display queue.
        // The fifth upper INITIAL grants +N; the first PAID upper game shows
        // its prior five-game TOTAL before that paid game's +N, FIFO.
        displayEvents.removeAll();
        long id=(completed.size()+1L)*4L;
        if(actual.stage()==HyperDogAtGame.Stage.LOWER_INITIAL){
            addDisplay(id+1,"TOTAL",game.lowerInitialTotal());
        }else if(actual.stage()==HyperDogAtGame.Stage.UPPER_INITIAL){
            upperInitialTotal=game.upperInitialTotal();
            addDisplay(id+1,"ADD",actual.award());
        }else if(actual.stage()==HyperDogAtGame.Stage.UPPER_AT){
            if(lastSourceFree==5){
                addDisplay(id+1,"TOTAL",upperInitialTotal);
                lastSourceFree=6;
            }
            if(actual.award()>0)addDisplay(id+2,"ADD",actual.award());
        }else if(actual.stage()==HyperDogAtGame.Stage.LOWER_AT&&actual.award()>0){
            addDisplay(id+1,"ADD",actual.award());
        }
        if(actual.stage()==HyperDogAtGame.Stage.UPPER_INITIAL){
            lastSourceFree++;
        }
        JsonObject row=new JsonObject();
        row.addProperty("number",completed.size()+1);
        row.addProperty("episode",episode);
        row.addProperty("stage",actual.stage().name());
        row.addProperty("role",actual.role().name()); // private server evidence only
        row.addProperty("bet",actual.bet());row.addProperty("payout",actual.payout());
        row.addProperty("award",actual.award());row.addProperty("ticket",actual.ticket());
        row.addProperty("czSuccess",actual.czSuccess());
        row.addProperty("spinId",round.snapshot().round().spinId().toString());
        row.add("stops",new Gson().toJsonTree(round.snapshot().round().history().stops()));
        row.addProperty("remaining",game.remaining());
        row.addProperty("credit",game.credit());completed.add(row);
        if(actual.stage()==HyperDogAtGame.Stage.LOWER_AT){
            lowerRounds++;lowerTicketsIssued+=actual.ticket();
        }else if(actual.stage()==HyperDogAtGame.Stage.UPPER_AT)upperRounds++;
        if(actual.stage()==HyperDogAtGame.Stage.UPPER_CZ){
            if(actual.czSuccess())czSuccesses++;
            else if(game.stage()==HyperDogAtGame.Stage.COMPLETE)czFailures++;
        }
        round=null;outcome=null;lastFinish=System.currentTimeMillis();
        if(episode==0&&game.stage()==HyperDogAtGame.Stage.COMPLETE){
            episode=1;
            game=new HyperDogAtGame(SETTING,20261109L,INITIAL_CREDIT,0,0,false);
            game.confirmRed7();
            lastSourceFree=0;
            upperInitialTotal=0;
        }else if(episode==1&&game.stage()==HyperDogAtGame.Stage.UPPER_AT&&
                 completed.size()>60&&czSuccesses>0){
            finished=true;
        }
        Bukkit.getScheduler().runTaskLater(plugin,this::sendPublic,8L);
    }
    private void addDisplay(long id,String kind,long value){
        if(value<=0)throw new IllegalArgumentException("not a positive presentation award");
        JsonObject e=new JsonObject();
        e.addProperty("id",id);e.addProperty("kind",kind);e.addProperty("amount",value);
        displayEvents.add(e);resultsPublished++;
    }
    private void sendDataLamp(){
        JsonObject j=new JsonObject();
        j.addProperty("machineId",MACHINE);j.addProperty("totalGames",completed.size());
        j.addProperty("currentGames",completed.size());j.addProperty("todayDifference",game.totalOut()-game.totalIn());
        j.addProperty("todayMaxDifference",Math.max(0L,game.totalOut()-game.totalIn()));
        j.addProperty("atCount",upperRounds+lowerRounds>0?2:0);
        JsonArray graph=new JsonArray();
        for(int i=0;i<completed.size();i+=8){
            JsonObject g=new JsonObject();g.addProperty("game",i+1);
            g.addProperty("difference",completed.get(i).getAsJsonObject().get("credit").getAsLong()-INITIAL_CREDIT);
            graph.add(g);
        }
        j.add("graph",graph);j.add("history",new JsonArray());
        send(Envelope.current(PacketType.DATA_LAMP,j));
    }
    private void sendOpen(){
        JsonObject j=new JsonObject();
        j.addProperty("sessionId",session.toString());j.addProperty("machineId",MACHINE);
        j.addProperty("machineType","HYPER_DOG");
        j.addProperty("expectedNextClientSequence",nextSequence);
        send(Envelope.current(PacketType.OPEN_MACHINE,j));opened=true;
    }
    private void sendPublic(){
        JsonObject j=new JsonObject();
        String state=finished?"ACCEPTANCE_COMPLETE":round!=null?"NORMAL_SPINNING":
                game.waitingForBet()?"SEATED_READY":game.canLever()?
                (game.snapshot().get("betPosted").getAsBoolean()?"NORMAL_BETTED":"REPLAY_READY"):
                "NORMAL_SPINNING";
        j.addProperty("gameState",state);
        j.addProperty("credit",Math.min(50L,game.credit()));j.addProperty("bet",game.pending()!=null?game.pending().bet():0);
        j.addProperty("pay",0);j.addProperty("gameCount",completed.size());
        j.addProperty("heldMedals",Math.max(0L,game.credit()-50));
        j.addProperty("atStage",game.stage().name());j.addProperty("atRemaining",game.remaining());
        j.addProperty("hasCzTicket",game.czTickets()>0);
        j.addProperty("lampOn",game.stage()==HyperDogAtGame.Stage.UPPER_INITIAL && episode==0);
        j.addProperty("newAward",round!=null?0:lastAward);
        long award=round==null?lastAward:0;
        String color=award>=500?"RED":award>=100?"PURPLE":award>=50?"YELLOW":
                award>=30?"GREEN":award>=20?"BLUE":award>=10?"WHITE":"OFF";
        j.addProperty("rightLampColor",color);
        j.addProperty("rightLampCount",award>=10?1:0);
        j.add("displayEvents",displayEvents.deepCopy());
        j.addProperty("sessionId",session.toString());j.addProperty("machineId",MACHINE);
        j.addProperty("machineType","HYPER_DOG");j.addProperty("expectedNextClientSequence",nextSequence);
        send(Envelope.current(PacketType.PUBLIC_STATE,j));
        if(completed.size()%5==0)sendDataLamp();
    }
    private void accepted(PacketType type,long seq){
        JsonObject j=new JsonObject();j.addProperty("clientSequence",seq);
        j.addProperty("action",type.name());send(Envelope.current(PacketType.ACTION_ACCEPTED,j));
    }
    private void send(Envelope p){
        if(player!=null&&player.isOnline())
            player.sendPluginMessage(plugin,Protocol.CHANNEL,EnvelopeCodec.encode(p));
    }
    private void write(){
        JsonObject j=new JsonObject();
        j.addProperty("ready",ready);j.addProperty("opened",opened);j.addProperty("complete",finished);
        j.addProperty("completedRounds",completed.size());j.addProperty("episode",episode);
        j.addProperty("acceptedBets",acceptedBet);j.addProperty("acceptedLevers",acceptedLever);
        j.addProperty("manualStops",manualStops);j.addProperty("automaticStops",automaticStops);
        j.addProperty("rejected",rejected);j.addProperty("snapshotRestorations",restorations);
        j.addProperty("lowerRounds",lowerRounds);j.addProperty("upperRounds",upperRounds);
        j.addProperty("lowerTicketsIssued",lowerTicketsIssued);
        j.addProperty("czSuccesses",czSuccesses);j.addProperty("czFailures",czFailures);
        j.addProperty("upperSeed",upperSeed);
        j.addProperty("stage",game.stage().name());
        j.addProperty("displayEventCount",resultsPublished);
        j.addProperty("closeRequests",closeRequests);j.addProperty("reseats",reseats);
        j.add("rounds",completed);
        AtomicTestEvidence.write(resultPath,j.toString());
    }
}
