package jp.pirijuggler.paper.database;

import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.reel.StopCatalogue;
import jp.pirijuggler.paper.reel.StopSolver;
import jp.pirijuggler.paper.session.Session;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryStoreTest {
    @TempDir Path directory;
    PiriDatabase db;
    Map<String,Object> config;
    static final long NOW=1_700_000_000_000L;

    @BeforeEach void open() throws Exception {
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"))){
            config=ConfigValidation.load(reader).values();
        }
        db=new PiriDatabase(directory.resolve("piri.db"));
        db.open(1,NOW,config,new SplittableRandom(1),ignored->{});
    }

    @AfterEach void close() throws Exception { db.close(); }

    @Test void sameSnapshotForceSettlementIsIdempotent() throws Exception {
        int machine=db.create(new Machine.Location(UUID.randomUUID(),"world",0,64,0,"NORTH"),NOW);
        UUID player=UUID.randomUUID();
        db.seat(player,machine,NOW);
        db.sql("UPDATE player_sessions SET game_state='BONUS_PENDING_BIG',credit=10,held_medals=0,bonus_type='BIG',last_client_sequence=7 WHERE player_uuid=?",player.toString());
        Session stale=db.state().session(player);
        RecoveryStore recovery=new RecoveryStore(db,config,new StopSolver(new StopCatalogue()),123L);

        db.transaction(()->{recovery.settle(stale,NOW+1);return null;});
        Session first=db.state().session(player);
        assertEquals(Session.GameState.SEATED_READY,first.state());
        assertEquals(50,first.number("credit"));
        assertEquals(199,first.number("held_medals"));
        assertEquals(1,scalar("SELECT big_count FROM machine_period_stats"));
        assertEquals(1,scalar("SELECT count(*) FROM bonus_history"));
        assertEquals(1,scalar("SELECT count(*) FROM economy_transactions WHERE operation='FORCE_SETTLEMENT'"));
        assertEquals("SETTLE:"+stale.id()+":7",RecoveryStore.settlementTransactionId(stale));

        db.transaction(()->{recovery.settle(stale,NOW+2);return null;});
        Session second=db.state().session(player);
        assertEquals(first.number("credit"),second.number("credit"));
        assertEquals(first.number("held_medals"),second.number("held_medals"));
        assertEquals(1,scalar("SELECT big_count FROM machine_period_stats"));
        assertEquals(1,scalar("SELECT count(*) FROM bonus_history"));
        assertEquals(1,scalar("SELECT count(*) FROM economy_transactions WHERE operation='FORCE_SETTLEMENT'"));
    }

    private long scalar(String sql) throws Exception {
        return ((Number)db.rows(sql).getFirst().values().iterator().next()).longValue();
    }
}
