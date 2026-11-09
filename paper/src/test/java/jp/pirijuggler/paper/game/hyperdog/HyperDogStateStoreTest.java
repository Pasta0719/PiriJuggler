package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.JsonObject;
import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogStateStoreTest {
    @TempDir Path directory;
    private PiriDatabase db;
    private Map<String,Object> config;
    private final UUID world=UUID.randomUUID(),player=UUID.randomUUID(),other=UUID.randomUUID();
    private final long now=1_700_000_000_000L;
    private int machine;
    private UUID session;
    private HyperDogStateStore store;
    private HyperDogFullGame full;

    @BeforeEach void setup() throws Exception {
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("piri.specRoot"),
                "paper/src/main/resources/config.yml"))) {
            config=ConfigValidation.load(reader).values();
        }
        db=new PiriDatabase(directory.resolve("piri.db"));
        db.open(1,now,config,new SplittableRandom(4),ignored->{});
        machine=db.create(new Machine.Location(world,"world",20,64,0,"NORTH"),
                MachineType.HYPER_DOG,now);
        var seat=db.seat(player,machine,now);
        session=seat.id();
        db.sql("UPDATE player_sessions SET credit=50,held_medals=450 WHERE session_id=?",
                session.toString());
        store=new HyperDogStateStore(db);
        full=new HyperDogFullGame(3,20261109L,500);
    }
    @AfterEach void close() throws Exception {
        if(db!=null)db.close();
    }
    private void assertDom(String value,org.junit.jupiter.api.function.Executable executable) {
        assertEquals(value,assertThrows(DomainException.class,executable).getMessage());
    }
    @Test void storesOnExistingSchemaAndChecksActualCreditHeldConservation() throws Exception {
        var one=store.commit(player,session,machine,0,1,full,null,now+1);
        assertEquals(50,one.number("credit"));
        assertEquals(450,one.number("held_medals"));
        assertEquals(1,one.sequence());
        assertEquals(full.snapshot(),store.load(player,session,machine).game().snapshot());
        // Duplicate delivery with same sequence and payload must be idempotent.
        assertEquals(one.snapshot(),store.commit(player,session,machine,0,1,full,null,now+2).snapshot());
        full.bet();
        var two=store.commit(player,session,machine,1,2,full,null,now+3);
        assertEquals(50,two.number("credit"));assertEquals(447,two.number("held_medals"));
        assertEquals("NORMAL_BETTED",two.text("game_state"));
        full.normal().leverForced(HyperDogNormalGame.Role.BAR);
        JsonObject reel=new JsonObject();reel.addProperty("spinId",UUID.randomUUID().toString());
        reel.addProperty("stoppedMask",1);
        var three=store.commit(player,session,machine,2,3,full,reel,now+4);
        assertEquals("NORMAL_SPINNING",three.text("game_state"));
        assertEquals(reel,store.load(player,session,machine).reel());
        full.finish();
        assertEquals("RED7_CONFIRM",full.phase());
        var four=store.commit(player,session,machine,3,4,full,null,now+5);
        assertEquals(500-3+15,four.number("credit")+four.number("held_medals"));
        assertDom("SEQUENCE_OLD",()->store.commit(player,session,machine,2,3,full,null,now+6));
    }
    @Test void trueDatabaseRestartResumesUnfinishedAtAndStopsForeignSeat() throws Exception {
        store.commit(player,session,machine,0,1,full,null,now+1);
        full.bet();
        full.normal().leverForced(HyperDogNormalGame.Role.BAR);
        full.finish();
        full.confirmRed7();
        full.at().leverForced(HyperDogAtGame.Role.REPLAY);
        JsonObject reel=new JsonObject();reel.addProperty("physicalStop",2);
        store.commit(player,session,machine,1,2,full,reel,now+2);
        JsonObject original=full.snapshot();
        db.disconnect(player,now+3,60000);
        db.shutdown(now+4,60000);
        db.close();
        db=new PiriDatabase(directory.resolve("piri.db"));
        db.open(2,now+200,config,new SplittableRandom(6),ignored->{});
        store=new HyperDogStateStore(db);
        assertTrue(db.state().busy(machine),"unfinished HYPER_DOG must keep the cabinet lock");
        assertDom("MACHINE_OCCUPIED",()->db.seat(other,machine,now+201));
        db.seat(player,machine,now+201);
        var recovered=store.load(player,session,machine);
        assertEquals(original,recovered.game().snapshot());
        assertEquals(reel,recovered.reel());
        recovered.game().at().finish();
        assertEquals(HyperDogAtGame.Stage.UPPER_INITIAL,recovered.game().at().stage());
        assertEquals(0,recovered.game().at().freeGames()==1?0:1);
    }
    @Test void comebackReadyAfterAtMustKeepSamePlayerLockThroughRestartAndAdminRelease() throws Exception {
        // REPLAY / third-stop completion may leave a valuable 32G comeback in
        // SEATED_READY. The old restart predicate protected only non-ready games.
        store.commit(player,session,machine,0,1,full,null,now+1);
        full.bet();
        full.normal().leverForced(HyperDogNormalGame.Role.BAR);
        full.finish();
        assertNotNull(full.at());
        int spins=0;
        while(full.at()!=null && spins++<12000) {
            switch(full.at().stage()){
                case RED7_CONFIRM -> full.confirmRed7();
                case UPPER_INITIAL,LOWER_INITIAL,UPPER_CZ -> {
                    full.at().leverForced(HyperDogAtGame.Role.MISS);
                    full.finish();
                }
                case UPPER_AT,LOWER_AT -> {
                    if(full.waitingForBet())full.bet();
                    full.at().leverForced(HyperDogAtGame.Role.GRAPE);
                    full.finish();
                }
                default -> throw new AssertionError("unexpected AT state");
            }
        }
        assertNull(full.at(),"test must reach complete AT exit");
        assertEquals(32,full.comebackPaidLeft());
        var owned=store.commit(player,session,machine,1,2,full,null,now+2);
        assertEquals("SEATED_READY",owned.text("game_state"));
        JsonObject checkpoint=full.snapshot();

        db.disconnect(player,now+3,1000);
        db.expire(now+2000);
        assertTrue(db.state().busy(machine),"grace expiry must not evict comeback rights");
        assertEquals(checkpoint,store.load(player,session,machine).game().snapshot());
        assertDom("RECOVERY_REQUIRED",()->db.releaseMachineForSimulation(machine,now+2001));
        db.shutdown(now+3000,1000);
        db.close();
        db=new PiriDatabase(directory.resolve("piri.db"));
        db.open(2,now+6000,config,new SplittableRandom(7),ignored->{});
        store=new HyperDogStateStore(db);
        assertTrue(db.state().busy(machine),"32 paid games must survive true JVM restart");
        assertDom("MACHINE_OCCUPIED",()->db.seat(other,machine,now+6001));
        var reseated=db.seat(player,machine,now+6001);
        assertEquals(session,reseated.id());
        var recovered=store.load(player,session,machine);
        assertEquals(checkpoint,recovered.game().snapshot());
        assertEquals(32,recovered.game().comebackPaidLeft());
        assertEquals(50,recovered.session().number("credit"));
        assertEquals(full.credit()-50,recovered.session().number("held_medals"));
    }

    @Test void rejectsForeignOwnerAndStaleConcurrentWritesWithoutAnyMutation() throws Exception {
        store.commit(player,session,machine,0,1,full,null,now+1);
        var before=db.state().session(player).snapshot();
        assertDom("MACHINE_OCCUPIED",()->store.commit(other,session,machine,1,2,full,null,now+2));
        assertDom("SEQUENCE_OLD",()->store.commit(player,session,machine,0,2,full,null,now+2));
        assertDom("INVALID_STATE",()->store.commit(player,session,machine+99,1,2,full,null,now+2));
        assertEquals(before,db.state().session(player).snapshot());
    }
}
