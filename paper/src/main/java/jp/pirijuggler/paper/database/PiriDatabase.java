package jp.pirijuggler.paper.database;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import jp.pirijuggler.paper.game.god.GodMachineRuntime;
import jp.pirijuggler.paper.reel.StopCatalogue;
import jp.pirijuggler.paper.reel.StopSolver;
import jp.pirijuggler.paper.session.Session;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/** Connection, queries and transactions are confined to one dedicated DB thread. */
public final class PiriDatabase implements AutoCloseable {
    public record State(String period, String profile, List<Machine> machines, List<Session> sessions) {
        public Machine machine(int id) { return machines.stream().filter(m -> m.id() == id && !m.deleted()).findFirst().orElse(null); }
        public Session session(UUID player) { return sessions.stream().filter(s -> s.player().equals(player)).findFirst().orElse(null); }
        public boolean busy(int id) { return sessions.stream().anyMatch(s -> s.machine() == id && s.ownsLock()); }
    }
    @FunctionalInterface public interface Transaction<T> { T run() throws Exception; }
    private final Path file;
    private final Thread owner;
    private Connection connection;
    private String period;
    private String profile;
    private Map<String,Object> recoveryConfig;
    private StopSolver recoverySolver;
    public PiriDatabase(Path file) { this.file = file; owner = Thread.currentThread(); }
    private void checkThread() { if (Thread.currentThread() != owner) throw new IllegalStateException("DB thread required"); }
    public State open(long jvmStart, long now, Map<String, Object> config, RandomGenerator eventRng, Consumer<String> warning) throws Exception {
        checkThread(); Files.createDirectories(file.toAbsolutePath().getParent());
        recoveryConfig=config; recoverySolver=new StopSolver(new StopCatalogue());
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try {
            sql("PRAGMA journal_mode=WAL"); sql("PRAGMA foreign_keys=ON"); sql("PRAGMA synchronous=FULL"); sql("PRAGMA busy_timeout=5000");
            boolean exists = !rows("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").isEmpty();
            if (exists && (!tableExists("metadata") || !"4".equals(metadata("schema_version"))))
                throw new SQLException("schema_version != 4: back up piri.db, then remove the old development DB before restarting. No data was deleted.");
            transaction(() -> {
                if (!exists) {
                    try (var input = PiriDatabase.class.getResourceAsStream("/schema-v4.sql")) {
                        if (input == null) throw new SQLException("Missing schema-v4.sql");
                        for (String statement : new String(input.readAllBytes(), StandardCharsets.UTF_8).split(";"))
                            if (!statement.isBlank()) sql(statement);
                    }
                    metadata("schema_version", "4");
                }
                ensureMachineTypeColumn();
                ensureMachineRuntimeColumn();
                ensureMachineStateColumn();
                String oldJvm = metadata("current_jvm_start_ms");
                if (Long.toString(jvmStart).equals(oldJvm)) {
                    period = Objects.requireNonNull(metadata("current_business_period_id"));
                    profile = (String) one("SELECT profile_name FROM business_periods WHERE business_period_id=?", period).get("profile_name");
                } else {
                    RecoveryStore recovery=recovery();
                    for (Session session : sessions()) if (session.ownsLock()) {
                        if(!session.ready()) recovery.settle(session,now);
                    }
                    sql("UPDATE player_sessions SET lifecycle='SUSPENDED_SAFE',lock_expires_at=NULL WHERE lifecycle IN ('ACTIVE','SUSPENDED_GRACE')");
                    var day = Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Tokyo")).toLocalDate();
                    var resolved = StartupProfile.resolve(config, day, metadata("next_start_profile"));
                    period = UUID.randomUUID().toString(); profile = resolved.name();
                    sql("INSERT INTO business_periods VALUES(?,?,?,?,?,?)", period, day.toString(), now, jvmStart, profile, resolved.source());
                    List<Machine> machines = machines();
                    for (Machine machine : machines) if (!machine.deleted()) initializeStats(machine.id(), now);
                    for (var allocation : resolved.allocate(machines, eventRng, warning).entrySet()) {
                        int id = allocation.getKey(), setting = allocation.getValue();
                        int old = machines.stream().filter(m -> m.id() == id).findFirst().orElseThrow().setting();
                        sql("UPDATE machines SET setting=?,updated_at=? WHERE machine_id=?", setting, now, id);
                        sql("INSERT INTO setting_history(machine_id,business_period_id,changed_at,old_setting,new_setting,reason,profile_name) VALUES(?,?,?,?,?,?,?)",
                                id, period, now, old, setting, resolved.reason(), profile);
                    }
                    metadata("current_jvm_start_ms", Long.toString(jvmStart)); metadata("current_business_period_id", period);
                    sql("DELETE FROM metadata WHERE key='next_start_profile'");
                }
                return null;
            });
            return state();
        } catch (Exception error) { connection.close(); connection = null; throw error; }
    }
    public <T> T transaction(Transaction<T> operation) throws Exception {
        checkThread(); connection.setAutoCommit(false);
        try { T result = operation.run(); connection.commit(); return result; }
        catch (Exception error) { connection.rollback(); throw error; }
        finally { connection.setAutoCommit(true); }
    }
    public State state() throws SQLException { return new State(period, profile, List.copyOf(machines()), List.copyOf(sessions())); }
    public int create(Machine.Location location, long now) throws Exception {
        return create(location, MachineType.JUGGLER, now);
    }
    public int create(Machine.Location location, MachineType type, long now) throws Exception {
        Objects.requireNonNull(type);
        return transaction(() -> {
            rejectDuplicate(location, 0);
            int id = ((Number) one("SELECT COALESCE(MAX(machine_id),0)+1 AS next FROM machines").get("next")).intValue();
            sql("INSERT INTO machines(machine_id,world_uuid,world_name,x,y,z,facing,machine_type,setting,created_at,updated_at) VALUES(?,?,?,?,?,?,?, ?,1,?,?)",
                    id, location.world().toString(), location.worldName(), location.x(), location.y(), location.z(), location.facing(), type.name(), now, now);
            initializeStats(id, now); return id;
        });
    }
    public void setMachineType(int id, MachineType type, long now) throws Exception {
        Objects.requireNonNull(type);
        transaction(() -> {
            requireMachine(id); requireFree(id);
            sql("UPDATE machines SET machine_type=?,updated_at=? WHERE machine_id=?", type.name(), now, id);
            return null;
        });
    }
    public void redefine(int id, Machine.Location location, long now) throws Exception {
        transaction(() -> {
            Machine machine = requireMachine(id); requireFree(id); rejectDuplicate(location, id);
            if (machine.location().sameBlock(location)) sql("UPDATE machines SET updated_at=? WHERE machine_id=?", now, id);
            else sql("UPDATE machines SET world_uuid=?,world_name=?,x=?,y=?,z=?,facing=?,updated_at=? WHERE machine_id=?",
                    location.world().toString(), location.worldName(), location.x(), location.y(), location.z(), location.facing(), now, id);
            return null;
        });
    }
    public void remove(int id, long now) throws Exception {
        transaction(() -> { requireMachine(id); requireFree(id); sql("UPDATE machines SET deleted=1,enabled=0,updated_at=? WHERE machine_id=?", now, id); return null; });
    }
    public Session seat(UUID player, int id, long now) throws Exception {
        return transaction(() -> {
            Machine machine = requireMachine(id);
            if (!machine.enabled()) throw new DomainException("MACHINE_DISABLED");
            Session existing = session(player);
            if (existing != null && existing.machine() != id) throw new DomainException("RECOVERY_REQUIRED");
            if (!rows("SELECT session_id FROM player_sessions WHERE machine_id=? AND lifecycle IN ('ACTIVE','SUSPENDED_GRACE') AND player_uuid<>?", id, player.toString()).isEmpty())
                throw new DomainException("MACHINE_OCCUPIED");
            String machineState=machine.type()==MachineType.GOD
                    ? GodMachineRuntime.fromJson(machine.runtimeJson()).gameplay().toJsonString()
                    : null;
            if (existing == null) {
                sql("INSERT INTO player_sessions(session_id,player_uuid,machine_id,source_business_period_id,game_state,lifecycle,credit,held_medals,display_left_stop,display_center_stop,display_right_stop,machine_state_json,last_activity) VALUES(?,?,?,?,'SEATED_READY','ACTIVE',0,0,?,?,?,?,?)",
                        UUID.randomUUID().toString(), player.toString(), id, period, machine.left(), machine.center(), machine.right(), machineState, now);
            } else {
                if (existing.lifecycle() == Session.Lifecycle.SUSPENDED_GRACE && existing.number("lock_expires_at") <= now) {
                    if(!existing.ready()) recovery().settle(existing,now);
                    sql("UPDATE player_sessions SET lifecycle='SUSPENDED_SAFE',lock_expires_at=NULL WHERE player_uuid=?", player.toString());
                    existing = session(player);
                }
                if (existing.lifecycle() == Session.Lifecycle.SUSPENDED_SAFE)
                    sql("UPDATE player_sessions SET source_business_period_id=? WHERE player_uuid=?", period, player.toString());
                sql("UPDATE player_sessions SET lifecycle='ACTIVE',lock_expires_at=NULL,machine_state_json=?,last_activity=? WHERE player_uuid=?", machineState, now, player.toString());
            }
            return session(player);
        });
    }
    public String closeSession(UUID player, UUID sessionId, int machine, long sequence, long now, long graceMs) throws Exception {
        return closeSession(player,sessionId,machine,sequence,now,graceMs,null);
    }
    public String closeSession(UUID player, UUID sessionId, int machine, long sequence, long now, long graceMs, Session motion) throws Exception {
        return transaction(() -> {
            Session session = session(player);
            if (session == null || !session.id().equals(sessionId) || session.machine() != machine || session.lifecycle() != Session.Lifecycle.ACTIVE)
                throw new DomainException("SESSION_MISMATCH");
            if (sequence <= session.sequence()) throw new DomainException("SEQUENCE_OLD");
            if(motion!=null)new GameStore(this).saveMotion(motion);
            boolean cashout = !rows("SELECT transaction_id FROM cashout_transactions WHERE player_uuid=? AND status<>'COMPLETED'", player.toString()).isEmpty();
            if (session.ready() && session.number("credit") == 0 && session.number("held_medals") == 0 && !cashout) {
                sql("DELETE FROM player_sessions WHERE player_uuid=?", player.toString()); return "SESSION_END";
            }
            String lifecycle = session.ready() ? "SUSPENDED_SAFE" : "SUSPENDED_GRACE";
            sql("UPDATE player_sessions SET lifecycle=?,lock_expires_at=?,last_activity=?,last_client_sequence=? WHERE player_uuid=?",
                    lifecycle, session.ready() ? null : now + graceMs, now, sequence, player.toString());
            return session.ready() ? "USER_CLOSE_SAFE" : "USER_CLOSE_GRACE";
        });
    }
    public void disconnect(UUID player, long now, long graceMs) throws Exception {
        disconnect(player,now,graceMs,null);
    }
    public void disconnect(UUID player,long now,long graceMs,Session motion) throws Exception {
        transaction(() -> { if(motion!=null)new GameStore(this).saveMotion(motion); sql("UPDATE player_sessions SET lifecycle='SUSPENDED_GRACE',lock_expires_at=?,last_activity=? WHERE player_uuid=? AND lifecycle='ACTIVE'",
                now + graceMs, now, player.toString()); return null; });
    }
    public void expire(long now) throws Exception {
        transaction(() -> {
            RecoveryStore recovery=recovery();
            for (Session session : sessions()) if (session.lifecycle() == Session.Lifecycle.SUSPENDED_GRACE && session.number("lock_expires_at") <= now) {
                if(!session.ready()) recovery.settle(session,now);
                sql("UPDATE player_sessions SET lifecycle='SUSPENDED_SAFE',lock_expires_at=NULL WHERE session_id=?", session.id().toString());
            }
            return null;
        });
    }
    /** Force-settle both expired grace sessions and ACTIVE sessions that reached idle timeout. Returns players idled now. */
    public List<UUID> maintain(long now,long idleMs) throws Exception {
        if(idleMs<0)throw new IllegalArgumentException("idleMs");
        return transaction(() -> {
            RecoveryStore recovery=recovery();List<UUID> idled=new ArrayList<>();
            for(Session session:sessions()){
                boolean grace=session.lifecycle()==Session.Lifecycle.SUSPENDED_GRACE&&session.number("lock_expires_at")<=now;
                boolean idle=session.lifecycle()==Session.Lifecycle.ACTIVE&&now-session.number("last_activity")>=idleMs;
                if(!grace&&!idle)continue;
                if(!session.ready())recovery.settle(session,now);
                sql("UPDATE player_sessions SET lifecycle='SUSPENDED_SAFE',lock_expires_at=NULL,last_activity=? WHERE session_id=?",now,session.id().toString());
                if(idle)idled.add(session.player());
            }
            return List.copyOf(idled);
        });
    }
    public JsonObject adminState(int id) throws SQLException {
        Machine machine = requireMachine(id); JsonObject json = new JsonObject();
        json.addProperty("machineId", id); json.addProperty("setting", machine.setting());
        json.addProperty("autoSetting", machine.autoSetting()); json.addProperty("enabled", machine.enabled());
        json.addProperty("activeProfile", profile); json.addProperty("busy", state().busy(id));
        Map<String, Object> stats = one("SELECT * FROM machine_period_stats WHERE machine_id=? AND business_period_id=?", id, period);
        String[] sql = {"total_games", "big_count", "reg_count", "current_games", "today_difference", "today_max_difference"};
        String[] wire = {"totalGames", "bigCount", "regCount", "currentGames", "todayDifference", "todayMaxDifference"};
        for (int i = 0; i < sql.length; i++) json.addProperty(wire[i], (Number) stats.get(sql[i]));
        JsonArray history = new JsonArray();
        for (var row : rows("SELECT * FROM setting_history WHERE machine_id=? ORDER BY changed_at DESC,id DESC LIMIT 30", id)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("machineId", id); entry.addProperty("businessPeriodId", (String) row.get("business_period_id"));
            entry.addProperty("time", (Number) row.get("changed_at")); entry.addProperty("old", (Number) row.get("old_setting"));
            entry.addProperty("new", (Number) row.get("new_setting")); entry.addProperty("reason", (String) row.get("reason"));
            entry.addProperty("actorUuid", (String) row.get("actor_uuid")); entry.addProperty("profileName", (String) row.get("profile_name")); history.add(entry);
        }
        json.add("settingHistory", history); return json;
    }
    private void rejectDuplicate(Machine.Location location, int own) throws SQLException {
        if (!rows("SELECT machine_id FROM machines WHERE world_uuid=? AND x=? AND y=? AND z=? AND deleted=0 AND machine_id<>?",
                location.world().toString(), location.x(), location.y(), location.z(), own).isEmpty()) throw new DomainException("LOCATION_ALREADY_REGISTERED");
    }
    private void requireFree(int id) throws SQLException {
        if (!rows("SELECT session_id FROM player_sessions WHERE machine_id=? AND lifecycle IN ('ACTIVE','SUSPENDED_GRACE')", id).isEmpty()) throw new DomainException("MACHINE_OCCUPIED");
    }
    private Machine requireMachine(int id) throws SQLException {
        return machines().stream().filter(m -> m.id() == id && !m.deleted()).findFirst().orElseThrow(() -> new DomainException("INVALID_STATE"));
    }
    private void initializeStats(int id, long now) throws SQLException {
        sql("INSERT INTO machine_period_stats(machine_id,business_period_id) VALUES(?,?)", id, period);
        sql("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(?,?,0,0,?)", id, period, now);
    }
    private Session session(UUID player) throws SQLException {
        var rows = rows("SELECT * FROM player_sessions WHERE player_uuid=?", player.toString()); return rows.isEmpty() ? null : new Session(rows.getFirst());
    }
    private List<Session> sessions() throws SQLException { return rows("SELECT * FROM player_sessions ORDER BY rowid").stream().map(Session::new).toList(); }
    private List<Machine> machines() throws SQLException {
        List<Machine> result = new ArrayList<>();
        for (var row : rows("SELECT * FROM machines ORDER BY machine_id")) {
            var location = new Machine.Location(UUID.fromString((String) row.get("world_uuid")), (String) row.get("world_name"), n(row,"x"), n(row,"y"), n(row,"z"), (String) row.get("facing"));
            MachineType type=MachineType.valueOf(Objects.toString(row.get("machine_type"),"JUGGLER"));
            result.add(new Machine(n(row,"machine_id"), location, type, n(row,"setting"), n(row,"enabled") != 0, n(row,"auto_setting") != 0,
                    n(row,"deleted") != 0, n(row,"last_left_stop"), n(row,"last_center_stop"), n(row,"last_right_stop"),
                    (String)row.get("machine_runtime_json"), ((Number)row.get("created_at")).longValue(), ((Number)row.get("updated_at")).longValue()));
        }
        return result;
    }
    private void ensureMachineTypeColumn() throws SQLException {
        boolean present=rows("PRAGMA table_info(machines)").stream().anyMatch(row->"machine_type".equals(row.get("name")));
        if(!present) sql("ALTER TABLE machines ADD COLUMN machine_type TEXT NOT NULL DEFAULT 'JUGGLER'");
    }
    private void ensureMachineRuntimeColumn() throws SQLException {
        boolean present=rows("PRAGMA table_info(machines)").stream().anyMatch(row->"machine_runtime_json".equals(row.get("name")));
        if(!present) sql("ALTER TABLE machines ADD COLUMN machine_runtime_json TEXT");
    }
    private void ensureMachineStateColumn() throws SQLException {
        boolean present=rows("PRAGMA table_info(player_sessions)").stream().anyMatch(row->"machine_state_json".equals(row.get("name")));
        if(!present) sql("ALTER TABLE player_sessions ADD COLUMN machine_state_json TEXT");
    }

    public void setMachineRuntimeJson(int id, String runtimeJson, long now) throws SQLException {
        requireMachine(id);
        sql("UPDATE machines SET machine_runtime_json=?,updated_at=? WHERE machine_id=?", runtimeJson, now, id);
    }
    private static int n(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }
    private boolean tableExists(String name) throws SQLException { return !rows("SELECT name FROM sqlite_master WHERE type='table' AND name=?", name).isEmpty(); }
    private String metadata(String key) throws SQLException { var rows = rows("SELECT value FROM metadata WHERE key=?", key); return rows.isEmpty() ? null : (String) rows.getFirst().get("value"); }
    private void metadata(String key, String value) throws SQLException { sql("INSERT INTO metadata(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", key, value); }
    private RecoveryStore recovery(){if(recoveryConfig==null||recoverySolver==null)throw new IllegalStateException("Recovery not configured");return new RecoveryStore(this,recoveryConfig,recoverySolver);}
    public int sql(String sql, Object... values) throws SQLException {
        checkThread(); try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement.execute() ? 0 : statement.getUpdateCount();
        }
    }
    public List<Map<String, Object>> rows(String sql, Object... values) throws SQLException {
        checkThread(); try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            try (ResultSet rows = statement.executeQuery()) {
                List<Map<String, Object>> result = new ArrayList<>();
                while (rows.next()) { Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.put(rows.getMetaData().getColumnLabel(i), rows.getObject(i)); result.add(row); }
                return result;
            }
        }
    }
    private Map<String, Object> one(String sql, Object... values) throws SQLException { return rows(sql, values).getFirst(); }
    public void shutdown(long now, long graceMs) throws Exception {
        if (connection == null) return;
        transaction(() -> { sql("UPDATE player_sessions SET lifecycle='SUSPENDED_GRACE',lock_expires_at=?,last_activity=? WHERE lifecycle='ACTIVE'", now + graceMs, now); return null; });
        close();
    }
    @Override public void close() throws SQLException {
        checkThread(); if (connection != null) { try { sql("PRAGMA wal_checkpoint(TRUNCATE)"); } finally { connection.close(); connection = null; } }
    }
}
