package jp.pirijuggler.runtime.paper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.game.hyperdog.HyperDogEventJournal;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineService;
import jp.pirijuggler.paper.machine.MachineType;
import jp.pirijuggler.paper.machine.RemoteMachineSync;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;

/**
 * TEST-ONLY live production protocol projection, with two genuine Minecraft
 * players and the real authenticated mobile HTTP service. Phase08's public
 * HYPER_DOG create/seat gate is not touched.
 */
public final class HyperDogPhase07LiveFixture {
    private final JavaPlugin helper;
    private final Path result=Path.of(System.getProperty("piri.runtime.serverResult"));
    private boolean started;
    private final Path audioTrigger=Path.of(System.getProperty("piri.runtime.phase07AudioTrigger"));
    private RemoteMachineSync liveRemote;
    private int liveHyper;
    private boolean audioTriggered;
    public HyperDogPhase07LiveFixture(JavaPlugin helper){
        this.helper=helper;
        Bukkit.getScheduler().runTaskTimer(helper,this::tick,20L,10L);
        Bukkit.getScheduler().runTaskTimer(helper,this::emitAudioAfterSubscribersReady,25L,10L);
    }
    /** Trigger the actual production spectator audio path after both live clients subscribe.
     * Never synthesize a successful sound assertion or modify production routing.
     */
    private void emitAudioAfterSubscribersReady(){
        if(liveRemote==null||audioTriggered||!Files.isRegularFile(audioTrigger))return;
        try{
            Files.deleteIfExists(audioTrigger);
            JsonObject spin=new JsonObject();
            spin.addProperty("spinId",UUID.randomUUID().toString());
            spin.addProperty("animation","NORMAL");
            spin.addProperty("rngState","MUST_NEVER_LEAK");
            spin.addProperty("setting",6);
            spin.addProperty("premium",true);
            JsonObject phase=new JsonObject();
            phase.addProperty("left",0);phase.addProperty("center",7);phase.addProperty("right",14);
            spin.add("startPhase",phase);
            liveRemote.publishOwnerPacket(liveHyper,Envelope.current(PacketType.SPIN_START,spin));
            audioTriggered=true;
            JsonObject output=JsonParser.parseString(Files.readString(result)).getAsJsonObject();
            output.addProperty("audioTriggered",true);
            AtomicTestEvidence.write(result,output.toString());
        }catch(Exception failure){fail(failure);}
    }
    private void tick(){
        if(started)return;
        var p=Bukkit.getPluginManager().getPlugin("PiriJuggler");
        if(!(p instanceof PiriJugglerPlugin production)||!production.isEnabled()||
           production.machines()==null||!production.machines().ready()||
           Bukkit.getPlayerExact("PiriRuntimeTest")==null||
           Bukkit.getPlayerExact("PiriRuntimeTest2")==null)return;
        started=true;
        MachineService service=production.machines();
        var world=Bukkit.getWorlds().getFirst();
        Machine.Location hyperLocation=new Machine.Location(world.getUID(),world.getName(),0,100,-9,"SOUTH");
        Machine.Location legacyLocation=new Machine.Location(world.getUID(),world.getName(),3,100,-9,"SOUTH");
        production.executors().database(()->{
            Field field=MachineService.class.getDeclaredField("database");
            field.setAccessible(true);
            PiriDatabase db=(PiriDatabase)field.get(service);
            int hyper=db.create(hyperLocation,MachineType.HYPER_DOG,System.currentTimeMillis());
            int legacy=db.create(legacyLocation,MachineType.JUGGLER,System.currentTimeMillis());
            String period=db.state().period();
            HyperDogEventJournal.ensure(db);
            long now=System.currentTimeMillis();
            db.transaction(()->{
                for(int i=0;i<4;i++)
                    db.sql("INSERT INTO hyper_dog_events VALUES(?,?,?,?,?,?)",
                        hyper,period,"at-"+i,"AT_FIRST_HIT",0,now+i);
                for(int i=0;i<3;i++)
                    db.sql("INSERT INTO hyper_dog_events VALUES(?,?,?,?,?,?)",
                        hyper,period,"cz-"+i,"CZ_CHALLENGE",0,now+10+i);
                for(int i=0;i<5;i++)
                    db.sql("INSERT INTO hyper_dog_events VALUES(?,?,?,?,?,?)",
                        hyper,period,"upper-"+i,"UPPER_ENTRY",0,now+20+i);
                db.sql("UPDATE machine_period_stats SET total_games=350,current_games=18,"+
                    "today_difference=1200,today_max_difference=1500 WHERE machine_id=? AND business_period_id=?",
                    hyper,period);
                db.sql("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(?,?,?,?,?)",
                    hyper,period,350,1200,now+30);
                db.sql("UPDATE machine_period_stats SET total_games=20,big_count=1,reg_count=2 "+
                    "WHERE machine_id=? AND business_period_id=?",legacy,period);
                return null;
            });
            return new Ready(db.state(),hyper,legacy,period);
        },(ready,error)->{
            if(error!=null){fail(error);return;}
            try{
                Field state=MachineService.class.getDeclaredField("state");
                state.setAccessible(true);
                state.set(service,ready.state());
                Field field=MachineService.class.getDeclaredField("remote");
                field.setAccessible(true);
                RemoteMachineSync remote=(RemoteMachineSync)field.get(service);
                remote.machineChanged(ready.hyper());
                remote.machineChanged(ready.legacy());
                JsonObject hdr=new JsonObject();
                hdr.addProperty("machineId",ready.hyper());hdr.addProperty("totalGames",350);
                hdr.addProperty("bigCount",0);hdr.addProperty("regCount",0);
                hdr.addProperty("atFirstHits",4);hdr.addProperty("czAttempts",3);
                hdr.addProperty("upperEntries",5);
                service.updateRemoteDataLamp(hdr);
                JsonObject old=new JsonObject();
                old.addProperty("machineId",ready.legacy());old.addProperty("totalGames",20);
                old.addProperty("bigCount",1);old.addProperty("regCount",2);
                service.updateRemoteDataLamp(old);
                remote.refreshOnlineViewers();
                // Do not emit transient packets before Minecraft clients have subscribed.
                liveRemote=remote;
                liveHyper=ready.hyper();
                JsonObject output=new JsonObject();
                output.addProperty("ready",true);output.addProperty("hyperMachine",ready.hyper());
                output.addProperty("legacyMachine",ready.legacy());output.addProperty("period",ready.period());
                output.addProperty("playerCount",Bukkit.getOnlinePlayers().size());
                output.addProperty("atFirstHits",4);output.addProperty("czAttempts",3);
                output.addProperty("upperEntries",5);
                AtomicTestEvidence.write(result,output.toString());
                helper.getLogger().info("HYPER_DOG_PHASE07_LIVE_READY "+output);
            }catch(Exception failure){fail(failure);}
        });
    }
    private record Ready(PiriDatabase.State state,int hyper,int legacy,String period){}
    private void fail(Throwable failure){
        JsonObject out=new JsonObject();out.addProperty("ready",false);
        out.addProperty("error",failure.toString());
        AtomicTestEvidence.write(result,out.toString());
        helper.getLogger().log(java.util.logging.Level.SEVERE,"HYPER_DOG_PHASE07_LIVE_FAIL",failure);
    }
}
