package jp.pirijuggler.paper.database;

import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.machine.*;
import jp.pirijuggler.paper.session.Session;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class PiriDatabaseTest {
    @TempDir Path directory;
    PiriDatabase db;
    Map<String, Object> config;
    final UUID world = UUID.randomUUID(), player = UUID.randomUUID(), other = UUID.randomUUID();
    static final long NOW = 1_700_000_000_000L;
    @BeforeEach void open() throws Exception {
        try (var reader = Files.newBufferedReader(Path.of(System.getProperty("piri.specRoot"), "paper/src/main/resources/config.yml"))) { config = ConfigValidation.load(reader).values(); }
        db = new PiriDatabase(directory.resolve("piri.db"));
        db.open(1, NOW, config, new SplittableRandom(1), ignored -> {});
    }
    @AfterEach void close() throws Exception { db.close(); }
    Machine.Location location(int x) { return new Machine.Location(world, "world", x, 64, 0, "NORTH"); }
    int create(int x) throws Exception { return db.create(location(x), NOW); }
    void code(String code, org.junit.jupiter.api.function.Executable operation) { assertEquals(code, assertThrows(DomainException.class, operation).getMessage()); }
    Object scalar(String sql) throws Exception { return db.rows(sql).getFirst().values().iterator().next(); }
    long count(String table) throws Exception { return ((Number) scalar("SELECT count(*) FROM " + table)).longValue(); }
    @Test void schemaMatchesEverySpecColumnAndPragmas() throws Exception {
        assertEquals("4", scalar("SELECT value FROM metadata WHERE key='schema_version'"));
        assertEquals("wal", scalar("PRAGMA journal_mode")); assertEquals(1, scalar("PRAGMA foreign_keys"));
        assertEquals(2, scalar("PRAGMA synchronous")); assertEquals(5000, scalar("PRAGMA busy_timeout"));
        assertEquals(14, ((Number) scalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name<>'sqlite_sequence'")).intValue());
        assertEquals(1, scalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='juggler_god_history'"));
        String spec = Files.readString(Path.of(System.getProperty("piri.specRoot"), "SPEC.md"));
        String schema = Files.readString(Path.of(System.getProperty("piri.specRoot"), "paper/src/main/resources/schema-v4.sql"));
        for (String statement : schema.replace("\r", "").split(";")) if (!statement.isBlank()) assertTrue(spec.replace("\r", "").contains(statement.strip()), statement);
        assertThrows(SQLException.class, () -> db.sql("INSERT INTO player_wallet VALUES('p',-1,0)"));
        assertThrows(SQLException.class, () -> db.sql("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(999,'absent',0,0,0)"));
    }
    @Test void machineDefaultsDuplicateAndDeletedIdNeverReused() throws Exception {
        int id = create(0); assertEquals(1, id); Machine machine = db.state().machine(id);
        assertEquals(1, machine.setting()); assertTrue(machine.enabled()); assertTrue(machine.autoSetting());
        assertEquals(0, machine.left() + machine.center() + machine.right());
        code("LOCATION_ALREADY_REGISTERED", () -> create(0));
        assertEquals(1, count("machines")); assertEquals(1, count("graph_points"));
        db.remove(id, NOW + 1); assertNull(db.state().machine(id)); assertEquals(2, create(0));
        assertEquals(2, count("machine_period_stats"));
    }
    @Test void sameCoordinateInDifferentWorldIsAllowed() throws Exception {
        create(0); assertEquals(2, db.create(new Machine.Location(UUID.randomUUID(), "other", 0,64,0,"NORTH"), NOW));
    }
    @Test void redefinePreservesEverythingExceptLocationAndUpdatedAt() throws Exception {
        int id = create(0); db.sql("UPDATE machines SET setting=6,enabled=0,auto_setting=0,last_left_stop=8,last_center_stop=9,last_right_stop=10 WHERE machine_id=?", id);
        db.sql("UPDATE machine_period_stats SET total_games=12,today_difference=500 WHERE machine_id=?", id);
        db.redefine(id, location(2), NOW + 1); Machine machine = db.state().machine(id);
        assertEquals(6, machine.setting()); assertFalse(machine.enabled()); assertFalse(machine.autoSetting());
        assertEquals(8, machine.left()); assertEquals(9, machine.center()); assertEquals(10, machine.right()); assertEquals(NOW, machine.createdAt());
        assertEquals(12, scalar("SELECT total_games FROM machine_period_stats")); assertEquals(1, count("graph_points"));
        db.redefine(id, new Machine.Location(world,"renamed",2,64,0,"SOUTH"), NOW + 2);
        assertEquals(location(2), db.state().machine(id).location()); assertEquals(NOW + 2, db.state().machine(id).updatedAt());
    }
    @Test void redefineDuplicateRollsBackAndBusyRejectsMutations() throws Exception {
        int id = create(0); create(1); code("LOCATION_ALREADY_REGISTERED", () -> db.redefine(id, location(1), NOW));
        assertEquals(location(0), db.state().machine(id).location()); db.seat(player, id, NOW);
        code("MACHINE_OCCUPIED", () -> db.remove(id, NOW)); code("MACHINE_OCCUPIED", () -> db.redefine(id, location(2), NOW));
        db.disconnect(player, NOW, 60_000); code("MACHINE_OCCUPIED", () -> db.remove(id, NOW));
    }
    @Test void seatIsUniquePerPlayerAndMachineAndPersistsBeforeReturn() throws Exception {
        int first = create(0), second = create(1); Session seat = db.seat(player, first, NOW);
        assertEquals(0, seat.number("credit")); assertEquals(0, seat.number("held_medals")); assertEquals(29, seat.snapshot().size());
        assertTrue(db.state().busy(first)); code("RECOVERY_REQUIRED", () -> db.seat(player, second, NOW));
        code("MACHINE_OCCUPIED", () -> db.seat(other, first, NOW)); assertEquals(1, count("player_sessions"));
        assertEquals(seat.id(), db.seat(player, first, NOW + 1).id());
    }
    @Test void disabledAndRemovedCannotBeSeated() throws Exception {
        int id = create(0); db.sql("UPDATE machines SET enabled=0"); code("MACHINE_DISABLED", () -> db.seat(player, id, NOW));
        db.remove(id, NOW); code("INVALID_STATE", () -> db.seat(player, id, NOW));
    }
    @Test void disconnectAndExactResumePreserveEntireSnapshot() throws Exception {
        int id = create(0); db.seat(player, id, NOW);
        db.sql("UPDATE player_sessions SET game_state='NORMAL_SPINNING',credit=32,held_medals=442,spin_id=?,internal_role='BIG',premium_type=NULL,notice_state='PENDING',lamp_on=1,bonus_type='BIG',bonus_payout_count=4,current_bet=3,pay_display=15,display_left_stop=8,display_center_stop=9,display_right_stop=10,stopped_mask=1,phase_left=2.5,phase_center=3.5,phase_right=4.5,motion_profile='NORMAL',last_client_sequence=123", UUID.randomUUID().toString());
        Map<String, Object> before = db.state().session(player).snapshot();
        db.disconnect(player, NOW + 1, 60_000); assertTrue(db.state().busy(id));
        assertEquals(Session.Lifecycle.SUSPENDED_GRACE, db.state().session(player).lifecycle());
        Session resumed = db.seat(player, id, NOW + 30_000);
        before.forEach((key, value) -> { if (!Set.of("last_activity", "lock_expires_at", "lifecycle").contains(key)) assertEquals(value, resumed.snapshot().get(key), key); });
        assertEquals(Session.Lifecycle.ACTIVE, resumed.lifecycle()); assertNull(resumed.snapshot().get("lock_expires_at"));
    }
    @Test void graceReadyRetainsLockUntilExpiryThenAssetsStaySafe() throws Exception {
        int id = create(0); db.seat(player, id, NOW); db.sql("UPDATE player_sessions SET credit=7,held_medals=90");
        db.disconnect(player, NOW, 60_000); db.expire(NOW + 59_999); assertTrue(db.state().busy(id));
        db.expire(NOW + 60_000); assertFalse(db.state().busy(id)); assertEquals(7, db.state().session(player).number("credit"));
        assertEquals(90, db.state().session(player).number("held_medals")); assertEquals(0, count("player_wallet"));
        db.seat(other, id, NOW + 60_001); code("MACHINE_OCCUPIED", () -> db.seat(player, id, NOW + 60_002));
    }
    @Test void unresolvedSnapshotForceSettlesOnGraceExpiryAndTrueRestart() throws Exception {
        int id=create(0);db.seat(player,id,NOW);
        db.sql("UPDATE player_sessions SET game_state='BONUS_PENDING_BIG',credit=10,held_medals=0,bonus_type='BIG'");
        db.disconnect(player,NOW,0);db.expire(NOW);
        Session safe=db.state().session(player);assertEquals(Session.Lifecycle.SUSPENDED_SAFE,safe.lifecycle());assertEquals(Session.GameState.SEATED_READY,safe.state());assertFalse(db.state().busy(id));
        assertEquals(50,safe.number("credit"));assertEquals(199,safe.number("held_medals"));assertEquals(1,((Number)db.rows("SELECT big_count FROM machine_period_stats").getFirst().get("big_count")).longValue());
        String old=db.state().period();
        db.sql("UPDATE player_sessions SET lifecycle='ACTIVE',game_state='BONUS_PENDING_REG',credit=10,held_medals=0,bonus_type='REG',last_client_sequence=1");
        db.close();db=new PiriDatabase(directory.resolve("piri.db"));db.open(2,NOW+100,config,new SplittableRandom(2),ignored->{});
        Session restarted=db.state().session(player);assertEquals(Session.Lifecycle.SUSPENDED_SAFE,restarted.lifecycle());assertEquals(Session.GameState.SEATED_READY,restarted.state());assertNotEquals(old,db.state().period());
        assertEquals(1,((Number)db.rows("SELECT reg_count FROM machine_period_stats WHERE business_period_id=?",old).getFirst().get("reg_count")).longValue());
    }
    @Test void idleMaintenanceForceSettlesAndUnlocksActiveSession() throws Exception {
        int id=create(0);db.seat(player,id,NOW);db.sql("UPDATE player_sessions SET game_state='BONUS_PENDING_REG',credit=10,bonus_type='REG',last_activity=?",NOW);
        assertTrue(db.maintain(NOW+179_999,180_000).isEmpty());assertTrue(db.state().busy(id));
        assertEquals(List.of(player),db.maintain(NOW+180_000,180_000));Session safe=db.state().session(player);
        assertEquals(Session.Lifecycle.SUSPENDED_SAFE,safe.lifecycle());assertEquals(Session.GameState.SEATED_READY,safe.state());assertFalse(db.state().busy(id));
    }
    @Test void closeEmptyDeletesAndSequenceMismatchCannotChangeState() throws Exception {
        int id = create(0); Session seat = db.seat(player, id, NOW);
        code("SESSION_MISMATCH", () -> db.closeSession(other, seat.id(), id, 1, NOW, 60_000));
        code("SEQUENCE_OLD", () -> db.closeSession(player, seat.id(), id, 0, NOW, 60_000)); assertTrue(db.state().busy(id));
        assertEquals("SESSION_END", db.closeSession(player, seat.id(), id, 1, NOW, 60_000));
        assertNull(db.state().session(player)); assertFalse(db.state().busy(id));
    }
    @Test void closeFundedSafeRetainsAssetsAndSafeRemoveAllowed() throws Exception {
        int id = create(0); Session seat = db.seat(player, id, NOW); db.sql("UPDATE player_sessions SET credit=50,held_medals=612");
        assertEquals("USER_CLOSE_SAFE", db.closeSession(player, seat.id(), id, 1, NOW, 60_000));
        assertFalse(db.state().busy(id)); assertEquals(612, db.state().session(player).number("held_medals"));
        db.remove(id, NOW); assertEquals(1, count("player_sessions")); code("INVALID_STATE", () -> db.seat(player, id, NOW));
    }
    @Test void unfinishedCashoutPreventsDeletingEmptySession() throws Exception {
        int id = create(0); Session seat = db.seat(player, id, NOW);
        db.sql("INSERT INTO cashout_transactions(transaction_id,player_uuid,amount,status,created_at,updated_at) VALUES(?,?,1,'PENDING',0,0)", "cashout", player.toString());
        assertEquals("USER_CLOSE_SAFE", db.closeSession(player, seat.id(), id, 1, NOW, 60_000)); assertNotNull(db.state().session(player));
    }
    @Test void closeEveryUnresolvedStateUsesGraceWithoutChangingRights() throws Exception {
        int id = create(0); Session seated = db.seat(player, id, NOW); long sequence = 1;
        for (Session.GameState game : Session.GameState.values()) if (game != Session.GameState.SEATED_READY) {
            db.sql("UPDATE player_sessions SET game_state=?,lifecycle='ACTIVE',credit=20,held_medals=30,current_bet=3,internal_role='CHERRY_BIG'", game.name());
            assertEquals("USER_CLOSE_GRACE", db.closeSession(player, seated.id(), id, sequence++, NOW, 60_000));
            Session result = db.state().session(player); assertEquals(game, result.state()); assertEquals(20, result.number("credit"));
            assertEquals(30, result.number("held_medals")); assertEquals(3, result.number("current_bet")); assertEquals("CHERRY_BIG", result.text("internal_role"));
            assertEquals(NOW + 60_000, result.number("lock_expires_at")); assertTrue(result.ownsLock());
        }
    }
    @Test void reloadReusesPeriodWhileTrueRestartCreatesNewRowsAndPreservesOldAssets() throws Exception {
        int id = create(0); String old = db.state().period(); db.seat(player, id, NOW);
        db.sql("UPDATE player_sessions SET credit=32,held_medals=442"); db.sql("UPDATE machine_period_stats SET total_games=77,today_difference=900");
        db.sql("INSERT INTO player_wallet VALUES(?,100,?)", player.toString(), NOW);
        db.shutdown(NOW, 60_000); db = new PiriDatabase(directory.resolve("piri.db")); db.open(1, NOW + 10, config, new SplittableRandom(2), ignored -> {});
        assertEquals(old, db.state().period()); assertEquals(1, count("business_periods")); db.close();
        db = new PiriDatabase(directory.resolve("piri.db")); db.open(2, NOW + 20, config, new SplittableRandom(2), ignored -> {});
        assertNotEquals(old, db.state().period()); assertEquals(2, count("business_periods")); assertEquals(2, count("machine_period_stats"));
        assertEquals(77, db.rows("SELECT total_games FROM machine_period_stats WHERE business_period_id=?", old).getFirst().get("total_games"));
        assertEquals(2, count("graph_points")); assertEquals(1, count("setting_history"));
        Session safe = db.state().session(player); assertEquals(Session.Lifecycle.SUSPENDED_SAFE, safe.lifecycle()); assertFalse(db.state().busy(id));
        assertEquals(old, safe.text("source_business_period_id")); assertEquals(32, safe.number("credit")); assertEquals(442, safe.number("held_medals"));
        assertEquals(100, scalar("SELECT pending_medals FROM player_wallet"));
        assertEquals(db.state().period(), db.seat(player, id, NOW + 30).text("source_business_period_id"));
    }
    @Test void schemaMismatchRejectsWithoutDeletingData() throws Exception {
        create(0); db.sql("UPDATE metadata SET value='3' WHERE key='schema_version'"); db.close();
        db = new PiriDatabase(directory.resolve("piri.db"));
        SQLException error = assertThrows(SQLException.class, () -> db.open(2, NOW, config, new SplittableRandom(1), ignored -> {}));
        assertTrue(error.getMessage().contains("back up"));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("piri.db")); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT count(*) FROM machines")) { assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); }
    }
    @Test void transactionFailureRollsBackAllWritesAndThreadConfinementIsEnforced() throws Exception {
        create(0); assertThrows(SQLException.class, () -> db.transaction(() -> { db.sql("UPDATE machines SET setting=6"); db.sql("UPDATE machines SET setting=7"); return null; }));
        assertEquals(1, db.state().machine(1).setting());
        try (var executor = Executors.newSingleThreadExecutor()) { assertInstanceOf(IllegalStateException.class, executor.submit(() -> { try { db.state(); return null; } catch (Exception e) { return e; } }).get()); }
    }
    @Test void publicWireStateDoesNotLeakPrivateSnapshot() throws Exception {
        int id = create(0); db.seat(player, id, NOW); db.sql("UPDATE player_sessions SET internal_role='BIG',premium_type=NULL");
        var session = db.state().session(player); assertEquals(3, session.openPacket().size());
        var json = session.publicState(); assertEquals(13, json.size()); assertFalse(json.toString().contains("internal")); assertFalse(json.has("setting"));
    }
}