package jp.pirijuggler.runtime.paper;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.HyperDogReelControl;
import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.game.hyperdog.*;
import jp.pirijuggler.paper.machine.*;
import jp.pirijuggler.paper.reel.HyperDogStopSession;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TEST ONLY: two genuinely separate Paper JVM boots using the same isolated
 * piri schema-v4 SQLite file. This exercises real plugin classloading, wallet
 * storage, player ownership and a physically half-stopped HYPER_DOG reel.
 * The production plugin's own piri.db is never opened or modified here.
 */
public final class HyperDogPhase04RestartFixture {
    private static final UUID OWNER=UUID.fromString("1f0c9712-521c-47d2-a72d-df9a1d222801");
    private static final UUID OTHER=UUID.fromString("1f0c9712-521c-47d2-a72d-df9a1d222802");
    private static final UUID WORLD=UUID.fromString("1f0c9712-521c-47d2-a72d-df9a1d222803");
    private final JavaPlugin plugin;
    private final String mode;
    private final Path databaseFile,resultFile;
    public HyperDogPhase04RestartFixture(JavaPlugin plugin,String mode){
        this.plugin=plugin;this.mode=mode;
        databaseFile=Path.of(System.getProperty("piri.runtime.phase04Db"));
        resultFile=Path.of(System.getProperty("piri.runtime.serverResult"));
        Bukkit.getScheduler().runTaskLater(plugin,this::run,45L);
    }
    private void run(){
        JsonObject report=new JsonObject();
        report.addProperty("phase","HYPER_DOG_PHASE_04_RESTART");
        report.addProperty("boot",mode);
        try{
            var production=Bukkit.getPluginManager().getPlugin("PiriJuggler");
            if(production==null||!production.isEnabled())throw new IllegalStateException("production plugin not active");
            Map<String,Object> config;
            try(var configReader=Files.newBufferedReader(
                    production.getDataFolder().toPath().resolve("config.yml"),StandardCharsets.UTF_8)){
                ConfigValidation.Result validation=ConfigValidation.load(configReader);
                if(!validation.valid())throw new IllegalStateException("production config invalid: "+validation.errors());
                config=validation.values();
            }
            long now=System.currentTimeMillis();
            try(PiriDatabase db=new PiriDatabase(databaseFile)){
                db.open(mode.equals("hyperdog04-save")?100:200,now,config,
                        new SplittableRandom(312),plugin.getLogger()::warning);
                var store=new HyperDogStateStore(db);
                if(mode.equals("hyperdog04-save"))save(db,store,now,report);
                else if(mode.equals("hyperdog04-restore"))restore(db,store,now,report);
                else throw new IllegalArgumentException("unsupported mode");
            }
            report.addProperty("passed",true);
            plugin.getLogger().info("HYPER_DOG_PHASE04_RESTART_PASS "+mode);
        }catch(Exception error){
            report.addProperty("passed",false);
            report.addProperty("error",error.toString());
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "HYPER_DOG_PHASE04_RESTART_FAIL "+mode,error);
        }
        AtomicTestEvidence.write(resultFile,report.toString());
    }
    private static void assertTest(boolean condition,String reason){
        if(!condition)throw new IllegalStateException(reason);
    }
    private static HyperDogStopSession physicallyStopTwo(UUID session,int machine,long now){
        HyperDogStopSession reels=new HyperDogStopSession(
                session,machine,UUID.randomUUID(),HyperDogReelControl.Role.BAR,
                now,new double[]{0,7,14});
        for(PacketType type:List.of(PacketType.STOP_LEFT,PacketType.STOP_CENTER)){
            JsonObject body=new JsonObject();
            body.addProperty("sessionId",session.toString());
            body.addProperty("machineId",machine);
            body.addProperty("clientSequence",reels.snapshot().round().lastSequence()+1);
            body.addProperty("pressedIndex",0);
            var responses=reels.receive(Envelope.current(type,body),now+1500);
            assertTest(responses.stream().anyMatch(p->p.packetType()==PacketType.ACTION_ACCEPTED),
                    "physical STOP rejected "+type);
        }
        assertTest(reels.snapshot().round().history().count()==2,"wrong physical stop count");
        return reels;
    }
    private static void forcedBar(HyperDogFullGame game) throws ReflectiveOperationException{
        var force=HyperDogNormalGame.class.getDeclaredMethod("leverForced",HyperDogNormalGame.Role.class);
        force.setAccessible(true);
        force.invoke(game.normal(),HyperDogNormalGame.Role.BAR);
    }
    private void save(PiriDatabase db,HyperDogStateStore store,long now,JsonObject r) throws Exception{
        assertTest(!Files.exists(databaseFile)||Files.size(databaseFile)>0,"unexpected db");
        Machine.Location location=new Machine.Location(WORLD,"world",12,70,9,"NORTH");
        int machine=db.create(location,MachineType.HYPER_DOG,now);
        var seated=db.seat(OWNER,machine,now);
        UUID session=seated.id();
        db.sql("UPDATE player_sessions SET credit=50,held_medals=450 WHERE session_id=?",
                session.toString());
        var game=new HyperDogFullGame(3,20261109L,500);
        store.commit(OWNER,session,machine,0,1,game,null,now+1);
        game.bet();
        store.commit(OWNER,session,machine,1,2,game,null,now+2);
        forcedBar(game);
        var physical=physicallyStopTwo(session,machine,now+3);
        JsonObject reel=physical.toJson();
        store.commit(OWNER,session,machine,2,3,game,reel,now+4);
        JsonObject before=game.snapshot();
        assertTest(store.load(OWNER,session,machine).game().snapshot().equals(before),"bad sqlite save");
        db.disconnect(OWNER,now+5,700);
        db.expire(now+900);
        assertTest(db.state().busy(machine),"unsettled mid-reel must retain lock");
        boolean foreignDenied=false;
        try{db.seat(OTHER,machine,now+901);}
        catch(DomainException denied){foreignDenied="MACHINE_OCCUPIED".equals(denied.getMessage());}
        assertTest(foreignDenied,"foreign seat not rejected on disconnect");
        r.addProperty("machineId",machine);r.addProperty("sessionId",session.toString());
        r.addProperty("owner",OWNER.toString());
        r.add("savedFullState",before);
        r.add("savedPhysicalStops",reel);
        r.addProperty("physicalStopCount",2);
        r.addProperty("credit",game.credit());
        r.addProperty("totalIn",game.totalIn());
        r.addProperty("totalOut",game.totalOut());
        r.addProperty("heldMedals",db.state().session(OWNER).number("held_medals"));
        r.addProperty("foreignSeatDenied",true);
        db.shutdown(now+1000,700);
    }
    private void restore(PiriDatabase db,HyperDogStateStore store,long now,JsonObject r) throws Exception{
        Path prior=Path.of(System.getProperty("piri.runtime.phase04Prior"));
        JsonObject saved=JsonParser.parseString(Files.readString(prior)).getAsJsonObject();
        assertTest(saved.get("passed").getAsBoolean(),"first Paper boot did not pass");
        int machine=saved.get("machineId").getAsInt();
        UUID session=UUID.fromString(saved.get("sessionId").getAsString());
        assertTest(db.state().busy(machine),"foreign lock lost during actual JVM restart");
        boolean foreignDenied=false;
        try{db.seat(OTHER,machine,now+1);}
        catch(DomainException denied){foreignDenied="MACHINE_OCCUPIED".equals(denied.getMessage());}
        assertTest(foreignDenied,"foreign seat unexpectedly acquired restart-protected machine");
        var resumed=db.seat(OWNER,machine,now+2);
        assertTest(resumed.id().equals(session),"seat identity changed on JVM restart");
        var state=store.load(OWNER,session,machine);
        assertTest(state.game().snapshot().equals(saved.getAsJsonObject("savedFullState")),
                "RNG / medal / prepared BAR result changed across JVMs");
        assertTest(state.reel().equals(saved.getAsJsonObject("savedPhysicalStops")),
                "physical stop history changed across JVMs");
        HyperDogStopSession physical=HyperDogStopSession.fromJson(state.reel());
        assertTest(physical.snapshot().round().history().count()==2,"two stops not restored");
        JsonObject body=new JsonObject();
        body.addProperty("sessionId",session.toString());
        body.addProperty("machineId",machine);
        body.addProperty("clientSequence",physical.snapshot().round().lastSequence()+1);
        body.addProperty("pressedIndex",0);
        var responses=physical.receive(Envelope.current(PacketType.STOP_RIGHT,body),
                physical.snapshot().startedEpochMs()+1600);
        assertTest(responses.stream().anyMatch(p->p.packetType()==PacketType.ACTION_ACCEPTED),
                "third stop not accepted after JVM restart");
        assertTest(physical.complete(),"recovered BAR physical stop not complete");
        HyperDogFullGame game=state.game();
        game.finish();
        assertTest("RED7_CONFIRM".equals(game.phase()),"BAR direct upper lost");
        assertTest(game.credit()==saved.get("credit").getAsLong()+15,
                "payout not credited exactly once");
        store.commit(OWNER,session,machine,3,4,game,null,now+3);
        game.confirmRed7();
        store.commit(OWNER,session,machine,4,5,game,null,now+4);
        for(int i=0;i<5;i++){
            game.lever();
            game.finish();
        }
        assertTest(game.at()!=null&&game.at().stage()==HyperDogAtGame.Stage.UPPER_AT,
                "upper 5G rights not restored");
        assertTest(game.at().upperInitialTotal()>=500,"upper 500 guarantee absent");
        store.commit(OWNER,session,machine,5,6,game,null,now+5);
        assertTest(store.load(OWNER,session,machine).game().snapshot().equals(game.snapshot()),
                "continued upper balance not persisted");
        db.disconnect(OWNER,now+6,500);
        db.maintain(now+10000,1000);
        assertTest(db.state().busy(machine),"idle release dropped resumed upper rights");
        r.addProperty("foreignSeatDenied",foreignDenied);
        r.addProperty("physicalStopCountAfterRestart",3);
        r.addProperty("recoveredCredit",game.credit());
        r.addProperty("recoveredTotalIn",game.totalIn());
        r.addProperty("recoveredTotalOut",game.totalOut());
        r.addProperty("upperInitialMedals",game.at().upperInitialTotal());
        r.addProperty("stageAfterRestart",game.phase());
        r.addProperty("sessionId",session.toString());
        r.addProperty("replayNoDoubleCredit",true);
        db.shutdown(now+10001,500);
    }
}
