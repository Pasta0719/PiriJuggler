package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.database.GameStore;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import jp.pirijuggler.paper.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Native Paper GameEngine -> actual schema v4 SQLite (no test-only fixture adapter). */
class HyperDogGameEngineIntegrationTest {
    @TempDir Path temp;
    private static final long NOW=1_800_000_000_000L;
    private static Map<String,Object> config()throws Exception{
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("piri.specRoot"),
                "paper/src/main/resources/config.yml"))){
            return ConfigValidation.load(reader).values();
        }
    }
    @Test void nativeBetLeverThreePhysicalStopsAndRecoveryUseAtomicExistingEconomy()throws Exception {
        try(var db=new PiriDatabase(temp.resolve("native-hyperdog.db"))){
            db.open(300,NOW,config(),new SplittableRandom(81),warning->{});
            UUID owner=UUID.randomUUID();
            int mid=db.create(new Machine.Location(UUID.randomUUID(),"world",31,64,31,"NORTH"),
                    MachineType.HYPER_DOG,NOW);
            db.seat(owner,mid,NOW+1);
            // Real economy writes exactly these owned player_sessions columns.
            db.sql("UPDATE player_sessions SET credit=50,held_medals=450 WHERE player_uuid=?",
                    owner.toString());
            var engine=new HyperDogGameEngine();
            var store=new GameStore(db);
            Machine machine=db.state().machine(mid);
            Session before=db.state().session(owner);
            assertEquals(500,before.number("credit")+before.number("held_medals"));

            var bet=engine.plan(before,machine,PacketType.SPACE_ACTION,1,NOW+1000,0,0,null);
            assertEquals(497,bet.after().number("credit")+bet.after().number("held_medals"));
            assertEquals("NORMAL_BETTED",bet.after().text("game_state"));
            assertTrue(bet.packets().stream().anyMatch(p->p.packetType()==PacketType.ACTION_ACCEPTED));
            Session committed=store.commit(bet);
            assertEquals(497,committed.number("credit")+committed.number("held_medals"));
            assertThrows(DomainException.class,()->engine.plan(committed,machine,
                    PacketType.SPACE_ACTION,1,NOW+1100,0,0,null));
            assertEquals(committed.snapshot(),store.commit(bet).snapshot(),"retry idempotent");

            var lever=engine.plan(committed,machine,PacketType.SPACE_ACTION,2,NOW+2000,0,0,null);
            Session started=store.commit(lever);
            assertEquals("NORMAL_SPINNING",started.text("game_state"));
            assertTrue(lever.packets().stream().anyMatch(p->p.packetType()==PacketType.SPIN_START));
            assertTrue(started.machineState().has("reel"));
            assertEquals(0,started.number("stopped_mask"));
            String internal=started.machineState().toString();
            assertTrue(internal.contains("rngState") || internal.contains("\"rng\""));
            String publicJson=lever.packets().stream()
                    .filter(p->p.packetType()==PacketType.PUBLIC_STATE)
                    .findFirst().orElseThrow().payload().toString();
            assertFalse(publicJson.contains("rng"));
            assertFalse(publicJson.contains("internalRole"));
            assertFalse(publicJson.contains("setting"));

            Session playing=started;
            for(int i=0;i<3;i++){
                PacketType stop=switch(i){
                    case 0->PacketType.STOP_LEFT;case 1->PacketType.STOP_CENTER;
                    default->PacketType.STOP_RIGHT;
                };
                var step=engine.plan(playing,machine,stop,3+i,NOW+4200+i*1200,0,0,0);
                assertTrue(step.packets().stream().anyMatch(p->p.packetType()==PacketType.REEL_STOP));
                playing=store.commit(step);
                if(i<2)assertTrue(playing.machineState().has("reel"));
                else assertFalse(playing.machineState().has("reel"));
            }
            assertEquals(5,playing.sequence());
            var game=HyperDogFullGame.restore(playing.machineState().getAsJsonObject("hyperDog"));
            assertEquals(playing.number("credit")+playing.number("held_medals"),game.credit());
            assertEquals(1,game.normal().games());
            assertEquals(3,game.totalIn());
            long total=playing.number("credit")+playing.number("held_medals");
            assertEquals(500-game.totalIn()+game.totalOut(),total);
            var rows=db.rows("SELECT total_games,today_difference,big_count,reg_count FROM machine_period_stats WHERE machine_id=?",
                    mid);
            assertEquals(1L,((Number)rows.getFirst().get("total_games")).longValue());
            assertEquals(game.totalOut()-3,((Number)rows.getFirst().get("today_difference")).longValue());
            assertEquals(0L,((Number)rows.getFirst().get("big_count")).longValue());
            assertEquals(0L,((Number)rows.getFirst().get("reg_count")).longValue());
            assertEquals(game.snapshot(),new HyperDogStateStore(db).load(owner,playing.id(),mid).game().snapshot());
        }
    }
    @Test void externalLoanReconcilesAuthoritativeWalletWithoutTouchingGameRng()throws Exception {
        try(var db=new PiriDatabase(temp.resolve("loan-reconcile.db"))){
            db.open(401,NOW,config(),new SplittableRandom(82),warning->{});
            UUID owner=UUID.randomUUID();
            int mid=db.create(new Machine.Location(UUID.randomUUID(),"world",32,64,32,"NORTH"),
                    MachineType.HYPER_DOG,NOW);
            db.seat(owner,mid,NOW+1);
            db.sql("UPDATE player_sessions SET credit=50,held_medals=100 WHERE player_uuid=?",owner.toString());
            var engine=new HyperDogGameEngine();
            Machine machine=db.state().machine(mid);
            Session before=db.state().session(owner);
            var bet=engine.plan(before,machine,PacketType.SPACE_ACTION,1,NOW+2,0,0,null);
            Session current=new GameStore(db).commit(bet);
            String rng=current.machineState().getAsJsonObject("hyperDog")
                    .getAsJsonObject("normal").get("rngState").getAsString();

            // Approved external economy write: apply more medals server-side
            // before next spin. No random draw or prize is created.
            db.sql("UPDATE player_sessions SET credit=50,held_medals=200 WHERE session_id=?",
                    current.id().toString());
            var fresh=db.state().session(owner);
            var lever=engine.plan(fresh,machine,PacketType.SPACE_ACTION,2,NOW+3,0,0,null);
            var stored=new GameStore(db).commit(lever);
            assertEquals(250,stored.number("credit")+stored.number("held_medals"));
            var ng=HyperDogFullGame.restore(stored.machineState().getAsJsonObject("hyperDog"));
            assertEquals(250,ng.credit());
            assertNotEquals(rng,ng.normal().snapshot().get("rngState").getAsString(),
                    "only the actual lever consumes the RNG stream");
        }
    }
}
