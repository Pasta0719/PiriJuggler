package jp.pirijuggler.paper.admin;

import com.google.gson.JsonObject;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.database.StartupProfile;
import jp.pirijuggler.paper.game.JugglerGodRuntime;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.machine.Machine;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** DB-thread-only Phase10 administration operations. */
public final class AdminStore {
    private final PiriDatabase db;
    private final Map<String, Object> config;

    public AdminStore(PiriDatabase db, Map<String, Object> config) {
        this.db = Objects.requireNonNull(db);
        this.config = Objects.requireNonNull(config);
    }

    public int setSetting(PiriDatabase.State state, int machineId, int setting, UUID actor, long now) throws Exception {
        if (setting < 1 || setting > 6) throw new DomainException("INVALID_STATE");
        Machine machine = requireMachine(state, machineId);
        if (machine.setting() == setting) return setting;
        db.transaction(() -> {
            db.sql("UPDATE machines SET setting=?,updated_at=? WHERE machine_id=? AND deleted=0", setting, now, machineId);
            db.sql("INSERT INTO setting_history(machine_id,business_period_id,changed_at,old_setting,new_setting,reason,actor_uuid,profile_name) VALUES(?,?,?,?,?,?,?,?)",
                    machineId, state.period(), now, machine.setting(), setting, "MANUAL", actor == null ? null : actor.toString(), state.profile());
            return null;
        });
        return setting;
    }

    public boolean setAuto(PiriDatabase.State state, int machineId, boolean value, long now) throws Exception {
        Machine machine = requireMachine(state, machineId);
        if (machine.autoSetting() == value) return value;
        db.transaction(() -> {
            db.sql("UPDATE machines SET auto_setting=?,updated_at=? WHERE machine_id=? AND deleted=0", value ? 1 : 0, now, machineId);
            return null;
        });
        return value;
    }

    public boolean setEnabled(PiriDatabase.State state, int machineId, boolean value, long now) throws Exception {
        Machine machine = requireMachine(state, machineId);
        if (machine.enabled() == value) return value;
        db.transaction(() -> {
            db.sql("UPDATE machines SET enabled=?,updated_at=? WHERE machine_id=? AND deleted=0", value ? 1 : 0, now, machineId);
            return null;
        });
        return value;
    }

    public void resetDaily(PiriDatabase.State state, int machineId, long now) throws Exception {
        requireMachine(state, machineId);
        db.transaction(() -> {
            resetDailyRow(state.period(), machineId, now);
            return null;
        });
    }

    public void resetDailyAll(PiriDatabase.State state, List<Integer> machineIds, long now) throws Exception {
        db.transaction(() -> {
            for (int machineId : machineIds) {
                requireMachine(state, machineId);
                resetDailyRow(state.period(), machineId, now);
            }
            return null;
        });
    }

    private void resetDailyRow(String period, int machineId, long now) throws SQLException {
        db.sql("UPDATE machine_period_stats SET total_games=0,big_count=0,reg_count=0,current_games=0,today_difference=0,today_max_difference=0,last_bonus_type=NULL,last_bonus_at=NULL WHERE machine_id=? AND business_period_id=?", machineId, period);
        db.sql("DELETE FROM bonus_history WHERE machine_id=? AND business_period_id=?", machineId, period);
        db.sql("DELETE FROM juggler_god_history WHERE machine_id=? AND business_period_id=?", machineId, period);
        db.sql("UPDATE machines SET machine_runtime_json=?,updated_at=? WHERE machine_id=? AND machine_type='JUGGLER_GOD'", JugglerGodRuntime.initial().toJsonString(), now, machineId);
        db.sql("UPDATE player_sessions SET machine_state_json=? WHERE machine_id=? AND lifecycle='SUSPENDED_SAFE'", JugglerGodRuntime.initial().toJsonString(), machineId);
        db.sql("DELETE FROM graph_points WHERE machine_id=? AND business_period_id=?", machineId, period);
        db.sql("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(?,?,0,0,?)", machineId, period, now);
    }

    public void setNextProfile(String profile) throws Exception {
        if (!profiles().containsKey(profile)) throw new DomainException("INVALID_STATE");
        db.transaction(() -> {
            db.sql("INSERT INTO metadata(key,value) VALUES('next_start_profile',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", profile);
            return null;
        });
    }

    public void clearNextProfile() throws Exception {
        db.transaction(() -> {
            db.sql("DELETE FROM metadata WHERE key='next_start_profile'");
            return null;
        });
    }

    public JsonObject eventStatus(PiriDatabase.State state) throws SQLException {
        JsonObject result = new JsonObject();
        result.addProperty("activeProfile", state.profile());
        var rows = db.rows("SELECT value FROM metadata WHERE key='next_start_profile'");
        if (rows.isEmpty()) result.add("nextProfile", com.google.gson.JsonNull.INSTANCE);
        else result.addProperty("nextProfile", (String) rows.getFirst().get("value"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> profiles() {
        return (Map<String, Object>) StartupProfile.map(config.get("events")).get("profiles");
    }

    private static Machine requireMachine(PiriDatabase.State state, int id) {
        Machine machine = state.machine(id);
        if (machine == null) throw new DomainException("INVALID_STATE");
        return machine;
    }
}
