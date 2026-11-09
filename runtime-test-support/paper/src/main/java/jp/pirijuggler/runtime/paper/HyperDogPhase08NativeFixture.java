package jp.pirijuggler.runtime.paper;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.database.GameStore;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.game.hyperdog.HyperDogGameEngine;
import jp.pirijuggler.paper.game.hyperdog.HyperDogFullGame;
import jp.pirijuggler.paper.game.hyperdog.HyperDogNormalGame;
import jp.pirijuggler.paper.game.hyperdog.HyperDogStateStore;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import jp.pirijuggler.paper.session.Session;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Test-only native production adapter replay against genuine Paper 1.21 and
 * isolated schema-v4 SQLite. No change to user's hall or public release gates.
 */
public final class HyperDogPhase08NativeFixture {
    private final JavaPlugin helper;
    private final Path result=Path.of(System.getProperty("piri.runtime.serverResult"));
    private final Path dbfile=Path.of(System.getProperty("piri.runtime.phase08Db"));
    private boolean started;
    public HyperDogPhase08NativeFixture(JavaPlugin plugin) {
        helper=plugin;
        Bukkit.getScheduler().runTaskTimer(plugin,this::start,45L,10L);
    }
    private void start() {
        if(started)return;
        var plugin=Bukkit.getPluginManager().getPlugin("PiriJuggler");
        if(!(plugin instanceof PiriJugglerPlugin prod)||!prod.isEnabled()||
           prod.machines()==null||!prod.machines().ready())return;
        started=true;
        prod.executors().database(()->execute(prod), (report,error)->{
            if(error!=null){
                JsonObject failed=new JsonObject();
                failed.addProperty("passed",false);
                failed.addProperty("error",error.toString());
                AtomicTestEvidence.write(result,failed.toString());
                helper.getLogger().severe("HYPER_DOG_PHASE08_NATIVE_FAIL "+error);
            }else{
                AtomicTestEvidence.write(result,report.toString());
                helper.getLogger().info("HYPER_DOG_PHASE08_NATIVE_SUCCESS "+report);
            }
        });
    }
    private static void check(boolean okay,String reason){
        if(!okay)throw new IllegalStateException(reason);
    }
    private static long scalar(PiriDatabase db,String sql,Object...args)throws Exception{
        var rows=db.rows(sql,args);
        if(rows.size()!=1)throw new IllegalStateException(sql);
        return ((Number)rows.getFirst().values().iterator().next()).longValue();
    }
    /** Fixed gameplay transport scenario, not an RTP sample or payout calibration.
     * Find a seed with 30 ordinary rounds so the actual native engine uses
     * genuine v7 draw logic throughout the 30 physical games without a test-only
     * role injection. Independent payout holdout uses unrelated predeclared seeds.
     */
    private static long pickNormalOnlySeed() {
        for(long seed=1;seed<=20000;seed++){
            var normal=new HyperDogNormalGame(1,seed,4050);
            int games=0;
            for(;games<30;games++){
                if(normal.canBet())normal.bet();
                if(!normal.canLever())break;
                var prepared=normal.lever();
                var settled=normal.finish();
                if(!settled.equals(prepared)||settled.entersAt())break;
            }
            if(games==30)return seed;
        }
        throw new IllegalStateException("no normal-only transport seed");
    }

    private JsonObject execute(PiriJugglerPlugin prod)throws Exception{
        Map<String,Object> config;
        try(var reader=Files.newBufferedReader(
                prod.getDataFolder().toPath().resolve("config.yml"),StandardCharsets.UTF_8)){
            var parsed=ConfigValidation.load(reader);
            check(parsed.valid(),"production config invalid");
            config=parsed.values();
        }
        long now=System.currentTimeMillis();
        check(!Files.exists(dbfile),"would reuse nonisolated database");
        try(var db=new PiriDatabase(dbfile)){
            db.open(8,now,config,new SplittableRandom(88),warning->helper.getLogger().warning(warning));
            UUID owner=UUID.fromString("1f0c9712-521c-47d2-a72d-df9a1d228808");
            UUID world=UUID.fromString("1f0c9712-521c-47d2-a72d-df9a1d228809");
            int mid=db.create(new Machine.Location(world,"world",51,72,51,"SOUTH"),
                    MachineType.HYPER_DOG,now);
            db.seat(owner,mid,now+1);
            db.sql("UPDATE player_sessions SET credit=50,held_medals=4000 WHERE player_uuid=?",
                    owner.toString());
            long transportSeed=pickNormalOnlySeed();
            JsonObject initial=new JsonObject();
            initial.addProperty("schema",1);
            initial.add("hyperDog",new HyperDogFullGame(1,transportSeed,4050).snapshot());
            db.sql("UPDATE player_sessions SET machine_state_json=? WHERE player_uuid=?",
                    initial.toString(),owner.toString());
            Session playing=db.state().session(owner);
            Machine cabinet=db.state().machine(mid);
            var adapter=new HyperDogGameEngine();
            var store=new GameStore(db);
            long sequence=playing.sequence(),clock=now+1000;
            int accepted=0,betActions=0,spins=0,physicalStops=0,automaticStops=0;
            for(int i=0;i<30;i++){
                HyperDogFullGame stage=playing.machineState()!=null&&playing.machineState().has("hyperDog")
                        ?HyperDogFullGame.restore(playing.machineState().getAsJsonObject("hyperDog"))
                        :null;
                if(stage==null||stage.waitingForBet()){
                    var bet=adapter.plan(playing,cabinet,PacketType.SPACE_ACTION,++sequence,clock+=1000,0,0,null);
                    check(bet.packets().stream().anyMatch(p->p.packetType()==PacketType.ACTION_ACCEPTED),
                            "native BET not acknowledged");
                    playing=store.commit(bet);betActions++;accepted++;
                }
                var lever=adapter.plan(playing,cabinet,PacketType.SPACE_ACTION,++sequence,clock+=1000,0,0,null);
                check(lever.packets().stream().anyMatch(p->p.packetType()==PacketType.SPIN_START),
                        "real native SPIN_START absent");
                playing=store.commit(lever);accepted++;spins++;
                var mode=lever.packets().stream().filter(p->p.packetType()==PacketType.SPIN_START)
                        .findFirst().orElseThrow().payload().get("mode").getAsString();
                check("NORMAL".equals(mode),"test must not depend on rare jackpot: "+mode);
                for(var action:new PacketType[]{PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT}){
                    var stop=adapter.plan(playing,cabinet,action,++sequence,clock+=1200,0,0,0);
                    check(stop.packets().stream().anyMatch(p->p.packetType()==PacketType.ACTION_ACCEPTED),
                            "native STOP rejected");
                    check(stop.packets().stream().anyMatch(p->p.packetType()==PacketType.REEL_STOP),
                            "native reel event absent");
                    playing=store.commit(stop);accepted++;physicalStops++;
                }
                check(!playing.machineState().has("reel"),"reel not committed by third stop");
                check(playing.sequence()==sequence,"next native input sequence mismatch");
                var game=HyperDogFullGame.restore(playing.machineState().getAsJsonObject("hyperDog"));
                check(playing.number("credit")+playing.number("held_medals")==game.credit(),
                        "medal wallet diverged");
                check(game.totalIn()-game.totalOut()==4050-game.credit(),
                        "medal conservation not maintained");
            }
            var persisted=HyperDogFullGame.restore(
                    new HyperDogStateStore(db).load(owner,playing.id(),mid)
                            .game().snapshot());
            check(persisted.credit()==playing.number("credit")+playing.number("held_medals"),
                    "isolated SQLite snapshot mismatch");
            check(scalar(db,"SELECT total_games FROM machine_period_stats WHERE machine_id=?",mid)==30,
                    "native 30-game period accounting mismatch");
            check(scalar(db,"SELECT big_count+reg_count FROM machine_period_stats WHERE machine_id=?",mid)==0,
                    "false legacy B/R in native play");
            check(scalar(db,"SELECT today_difference FROM machine_period_stats WHERE machine_id=?",mid)==
                    persisted.totalOut()-persisted.totalIn(),"native diff mismatch");
            JsonObject report=new JsonObject();
            report.addProperty("passed",true);
            report.addProperty("nativeEngine","HyperDogGameEngine");
            report.addProperty("productionPersistence","GameStore -> HyperDogStateStore");
            report.addProperty("actualMinecraftServer",Bukkit.getMinecraftVersion());
            report.addProperty("machineId",mid);
            report.addProperty("games",spins);
            report.addProperty("transportSeed",transportSeed);
            report.addProperty("betActions",betActions);
            report.addProperty("acceptedActions",accepted);
            report.addProperty("physicalStops",physicalStops);
            report.addProperty("automaticStops",automaticStops);
            report.addProperty("credit",persisted.credit());
            report.addProperty("totalBet",persisted.totalIn());
            report.addProperty("totalPayout",persisted.totalOut());
            report.addProperty("medalDifference",persisted.totalOut()-persisted.totalIn());
            report.addProperty("schema",4);
            return report;
        }
    }
}
