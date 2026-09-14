package jp.pirijuggler.runtime.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.economy.MedalToken;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Runtime-test-only fault injection for Phase07 LEDGER_COMMITTED reconciliation. */
public final class MedalFaultCommand implements CommandExecutor {
    private static final String UNLIMITED_TABLE = "medal_tokens_unlimited";
    private record Held(int slot, UUID bundleId, int amount) { }
    private record Injected(String transactionId, Held before, UUID afterId) { }

    private final JavaPlugin helper;

    public MedalFaultCommand(JavaPlugin helper) {
        this.helper = helper;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("NOT_OP");
            return true;
        }
        if (args.length < 1 || args.length > 2) return false;

        String action = args[0].toLowerCase(Locale.ROOT);
        Player target = args.length == 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage("PLAYER_REQUIRED");
            return true;
        }

        PiriJugglerPlugin production = (PiriJugglerPlugin) Bukkit.getPluginManager().getPlugin("PiriJuggler");
        if (production == null || !production.isEnabled()) {
            sender.sendMessage("PIRI_NOT_READY");
            return true;
        }

        if (action.equals("status")) {
            showStatus(sender, target, production);
            return true;
        }
        if (!action.equals("before") && !action.equals("after")) return false;

        var session = production.machines().ready() ? production.machines().snapshot().session(target.getUniqueId()) : null;
        if (session != null && session.lifecycle().name().equals("ACTIVE")) {
            sender.sendMessage("CLOSE_SLOT_SCREEN_FIRST");
            return true;
        }

        List<Held> medals = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            MedalToken.Value value = MedalToken.read(target.getInventory().getItem(slot));
            if (value != null) medals.add(new Held(slot, value.bundleId(), value.amount()));
        }
        if (medals.size() != 1) {
            sender.sendMessage("PIRI_FAULT_REQUIRES_EXACTLY_ONE_MEDAL_TOKEN found=" + medals.size() + " (use /pirimerge first)");
            return true;
        }

        Held before = medals.getFirst();
        String mode = action;
        var dbPath = production.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        sender.sendMessage("PIRI_FAULT_PREPARING mode=" + mode + " amount=" + before.amount());

        Bukkit.getScheduler().runTaskAsynchronously(helper, () -> {
            Injected injected;
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                try (var pragma = connection.createStatement()) {
                    pragma.execute("PRAGMA busy_timeout=5000");
                    pragma.execute("PRAGMA foreign_keys=ON");
                }
                ensureUnlimitedTable(connection);
                connection.setAutoCommit(false);
                try {
                    if (hasPendingFault(connection, target.getUniqueId()))
                        throw new IllegalStateException("PENDING_LEDGER_COMMITTED_ALREADY_EXISTS");
                    validateActive(connection, before);

                    long now = System.currentTimeMillis();
                    UUID afterId = UUID.randomUUID();
                    retire(connection, before.bundleId(), now);
                    try (var insert = connection.prepareStatement(
                            "INSERT INTO " + UNLIMITED_TABLE + "(bundle_id,amount,state,source_transaction_id,created_at,updated_at) VALUES(?,?,'ACTIVE',NULL,?,?)")) {
                        insert.setString(1, afterId.toString());
                        insert.setInt(2, before.amount());
                        insert.setLong(3, now);
                        insert.setLong(4, now);
                        insert.executeUpdate();
                    }

                    JsonArray beforeJson = new JsonArray();
                    beforeJson.add(bundleJson(before.bundleId(), before.amount(), before.slot()));
                    JsonArray afterJson = new JsonArray();
                    afterJson.add(bundleJson(afterId, before.amount(), before.slot()));
                    String tx = UUID.randomUUID().toString();
                    try (var journal = connection.prepareStatement(
                            "INSERT INTO medal_inventory_transactions(transaction_id,player_uuid,operation,before_bundle_json,after_bundle_json,container_snapshot_json,status,created_at,updated_at) " +
                                    "VALUES(?,?,'RUNTIME_FAULT_MEDAL',?,?,?,'LEDGER_COMMITTED',?,?)")) {
                        journal.setString(1, tx);
                        journal.setString(2, target.getUniqueId().toString());
                        journal.setString(3, beforeJson.toString());
                        journal.setString(4, afterJson.toString());
                        journal.setString(5, "{\"container\":\"PLAYER_INVENTORY\",\"faultMode\":\"" + mode + "\"}");
                        journal.setLong(6, now);
                        journal.setLong(7, now);
                        journal.executeUpdate();
                    }
                    connection.commit();
                    injected = new Injected(tx, before, afterId);
                } catch (Exception failure) {
                    connection.rollback();
                    throw failure;
                }
            } catch (Exception failure) {
                String message = "PIRI_FAULT_FAILED " + failure.getMessage();
                Bukkit.getScheduler().runTask(helper, () -> sender.sendMessage(message));
                return;
            }

            Bukkit.getScheduler().runTask(helper, () -> applyInventorySide(sender, target, production, injected, mode));
        });
        return true;
    }

    private void applyInventorySide(CommandSender sender, Player target, PiriJugglerPlugin production, Injected injected, String mode) {
        MedalToken.Value current = MedalToken.read(target.getInventory().getItem(injected.before().slot()));
        if (current == null || !current.bundleId().equals(injected.before().bundleId()) || current.amount() != injected.before().amount()) {
            sender.sendMessage("PIRI_FAULT_INVENTORY_CHANGED; injection left as LEDGER_COMMITTED. Do not spend medals; relog will classify the actual state.");
            return;
        }

        if (mode.equals("after")) {
            target.getInventory().setItem(injected.before().slot(),
                    MedalToken.create(injected.afterId(), injected.before().amount()));
            target.updateInventory();
        }

        sender.sendMessage("PIRI_FAULT_ARMED mode=" + mode + " tx=" + injected.transactionId());
        sender.sendMessage(mode.equals("before")
                ? "Now disconnect/reconnect. Expected /pirifault status: ROLLED_BACK. Medal amount must stay unchanged."
                : "Now disconnect/reconnect. Expected /pirifault status: APPLIED. Medal amount must stay unchanged.");
    }

    private void showStatus(CommandSender sender, Player target, PiriJugglerPlugin production) {
        var dbPath = production.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(helper, () -> {
            String message;
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                 var query = connection.prepareStatement(
                         "SELECT transaction_id,status,container_snapshot_json FROM medal_inventory_transactions " +
                                 "WHERE player_uuid=? AND operation='RUNTIME_FAULT_MEDAL' ORDER BY created_at DESC LIMIT 1")) {
                query.setString(1, target.getUniqueId().toString());
                try (var rows = query.executeQuery()) {
                    message = rows.next()
                            ? "PIRI_FAULT_STATUS tx=" + rows.getString("transaction_id") + " status=" + rows.getString("status") + " " + rows.getString("container_snapshot_json")
                            : "PIRI_FAULT_STATUS none";
                }
            } catch (Exception failure) {
                message = "PIRI_FAULT_STATUS_FAILED " + failure.getMessage();
            }
            String finalMessage = message;
            Bukkit.getScheduler().runTask(helper, () -> sender.sendMessage(finalMessage));
        });
    }

    private static boolean hasPendingFault(Connection connection, UUID player) throws Exception {
        try (var query = connection.prepareStatement(
                "SELECT 1 FROM medal_inventory_transactions WHERE player_uuid=? AND status='LEDGER_COMMITTED' LIMIT 1")) {
            query.setString(1, player.toString());
            try (var rows = query.executeQuery()) { return rows.next(); }
        }
    }

    private static void validateActive(Connection connection, Held held) throws Exception {
        int matches = matchingActive(connection, "medal_tokens", held) + matchingActive(connection, UNLIMITED_TABLE, held);
        if (matches != 1) throw new IllegalStateException("INVALID_ITEM");
    }

    private static int matchingActive(Connection connection, String table, Held held) throws Exception {
        try (var query = connection.prepareStatement("SELECT amount,state FROM " + table + " WHERE bundle_id=?")) {
            query.setString(1, held.bundleId().toString());
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return 0;
                return rows.getInt("amount") == held.amount() && "ACTIVE".equals(rows.getString("state")) ? 1 : 0;
            }
        }
    }

    private static void retire(Connection connection, UUID id, long now) throws Exception {
        int changed = updateState(connection, "medal_tokens", id, "ACTIVE", "RETIRED", now)
                + updateState(connection, UNLIMITED_TABLE, id, "ACTIVE", "RETIRED", now);
        if (changed != 1) throw new IllegalStateException("INVALID_ITEM");
    }

    private static int updateState(Connection connection, String table, UUID id, String from, String to, long now) throws Exception {
        try (var update = connection.prepareStatement(
                "UPDATE " + table + " SET state=?,updated_at=? WHERE bundle_id=? AND state=?")) {
            update.setString(1, to);
            update.setLong(2, now);
            update.setString(3, id.toString());
            update.setString(4, from);
            return update.executeUpdate();
        }
    }

    private static JsonObject bundleJson(UUID id, int amount, int slot) {
        JsonObject json = new JsonObject();
        json.addProperty("bundleId", id.toString());
        json.addProperty("amount", amount);
        json.addProperty("slot", slot);
        return json;
    }

    private static void ensureUnlimitedTable(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + UNLIMITED_TABLE + " (" +
                    "bundle_id TEXT PRIMARY KEY," +
                    "amount INTEGER NOT NULL CHECK(amount>=1)," +
                    "state TEXT NOT NULL CHECK(state IN ('PENDING_DELIVERY','ACTIVE','RETIRED'))," +
                    "source_transaction_id TEXT," +
                    "created_at INTEGER NOT NULL," +
                    "updated_at INTEGER NOT NULL)");
        }
    }
}
