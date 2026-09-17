package jp.pirijuggler.paper.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consolidates all valid Piri Medal items in a player's main inventory into one physical token.
 * The ledger is committed before the inventory mutation, matching the Phase07 crash-safety model.
 */
public final class MedalMergeCommand implements CommandExecutor {
    private record Held(int slot, UUID bundleId, int amount) { }
    private record Plan(String transactionId, UUID mergedId, int amount, List<Held> before, int destinationSlot) { }

    private final PiriJugglerPlugin plugin;

    public MedalMergeCommand(PiriJugglerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("この操作はゲーム内から実行してください。");
            return true;
        }
        if (args.length != 0) return false;

        List<Held> held = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            MedalToken.Value value = MedalToken.read(player.getInventory().getItem(slot));
            if (value != null) held.add(new Held(slot, value.bundleId(), value.amount()));
        }
        if (held.size() < 2) {
            player.sendMessage("メダルはすでに1つにまとまっています。");
            return true;
        }

        player.sendMessage("メダルを1つにまとめています…");
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Plan plan;
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                    try (var pragma = connection.createStatement()) {
                        pragma.execute("PRAGMA busy_timeout=5000");
                        pragma.execute("PRAGMA foreign_keys=ON");
                    }
                    connection.setAutoCommit(false);
                    try {
                        ensureUnlimitedTable(connection);
                        if (activeSession(connection, player.getUniqueId())) throw new IllegalStateException("CLOSE_SLOT_SCREEN_FIRST");

                        long total = 0;
                        JsonArray beforeJson = new JsonArray();
                        for (Held item : held) {
                            validateActive(connection, item);
                            total = Math.addExact(total, item.amount());
                            JsonObject json = new JsonObject();
                            json.addProperty("bundleId", item.bundleId().toString());
                            json.addProperty("amount", item.amount());
                            json.addProperty("slot", item.slot());
                            beforeJson.add(json);
                        }
                        if (total > MedalToken.MAX_AMOUNT) throw new IllegalStateException("MEDAL_AMOUNT_TOO_LARGE");

                        long now = System.currentTimeMillis();
                        UUID mergedId = UUID.randomUUID();
                        for (Held item : held) retire(connection, item.bundleId(), now);
                        try (var insert = connection.prepareStatement("INSERT INTO medal_tokens_unlimited(bundle_id,amount,state,source_transaction_id,created_at,updated_at) VALUES(?,?,'ACTIVE',NULL,?,?)")) {
                            insert.setString(1, mergedId.toString()); insert.setInt(2, (int) total); insert.setLong(3, now); insert.setLong(4, now); insert.executeUpdate();
                        }

                        int destination = held.getFirst().slot();
                        JsonArray afterJson = new JsonArray();
                        JsonObject after = new JsonObject();
                        after.addProperty("bundleId", mergedId.toString()); after.addProperty("amount", total); after.addProperty("slot", destination); afterJson.add(after);
                        String tx = UUID.randomUUID().toString();
                        try (var statement = connection.prepareStatement("INSERT INTO medal_inventory_transactions(transaction_id,player_uuid,operation,before_bundle_json,after_bundle_json,container_snapshot_json,status,created_at,updated_at) VALUES(?,?,'MERGE_MEDALS',?,?,?,'LEDGER_COMMITTED',?,?)")) {
                            statement.setString(1, tx); statement.setString(2, player.getUniqueId().toString()); statement.setString(3, beforeJson.toString()); statement.setString(4, afterJson.toString());
                            statement.setString(5, "{\"container\":\"PLAYER_INVENTORY\"}"); statement.setLong(6, now); statement.setLong(7, now); statement.executeUpdate();
                        }
                        connection.commit();
                        plan = new Plan(tx, mergedId, (int) total, List.copyOf(held), destination);
                    } catch (Exception failure) {
                        connection.rollback(); throw failure;
                    }
                }
            } catch (Exception failure) {
                String message = switch (String.valueOf(failure.getMessage())) {
                    case "CLOSE_SLOT_SCREEN_FIRST" -> "遊技中はメダルをまとめられません。台を離れてからもう一度お試しください。";
                    case "MEDAL_AMOUNT_TOO_LARGE" -> "メダル枚数が上限を超えているため、まとめられませんでした。";
                    case "TOKEN_REVIEW_REQUIRED" -> "メダル情報の確認が必要な状態です。管理者にお問い合わせください。";
                    case "INVALID_ITEM" -> "メダル情報を確認できなかったため、まとめられませんでした。";
                    default -> "メダルをまとめられませんでした。少し待ってからもう一度お試しください。";
                };
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(message));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> apply(player, plan));
        });
        return true;
    }

    private void apply(Player player, Plan plan) {
        if (!player.isOnline()) { rollbackAsync(plan, null); return; }
        for (Held expected : plan.before()) {
            MedalToken.Value current = MedalToken.read(player.getInventory().getItem(expected.slot()));
            if (current == null || !current.bundleId().equals(expected.bundleId()) || current.amount() != expected.amount()) {
                rollbackAsync(plan, player);
                return;
            }
        }
        for (Held item : plan.before()) player.getInventory().setItem(item.slot(), null);
        player.getInventory().setItem(plan.destinationSlot(), MedalToken.create(plan.mergedId(), plan.amount()));
        player.updateInventory();
        finishAsync(plan, player);
    }

    private void finishAsync(Plan plan, Player player) {
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                 var statement = connection.prepareStatement("UPDATE medal_inventory_transactions SET status='APPLIED',updated_at=? WHERE transaction_id=? AND status='LEDGER_COMMITTED'")) {
                statement.setLong(1, System.currentTimeMillis()); statement.setString(2, plan.transactionId()); statement.executeUpdate();
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("メダルを1つにまとめました。合計 " + plan.amount() + "枚です。"));
            } catch (Exception failure) {
                plugin.getLogger().severe("PIRI_MEDAL_MERGE_APPLY_MARK_FAILED tx=" + plan.transactionId() + " " + failure);
            }
        });
    }

    private void rollbackAsync(Plan plan, Player player) {
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                connection.setAutoCommit(false);
                try {
                    long now = System.currentTimeMillis();
                    setStateAny(connection, plan.mergedId(), "RETIRED", now);
                    for (Held item : plan.before()) setStateAny(connection, item.bundleId(), "ACTIVE", now);
                    try (var statement = connection.prepareStatement("UPDATE medal_inventory_transactions SET status='ROLLED_BACK',updated_at=? WHERE transaction_id=?")) {
                        statement.setLong(1, now); statement.setString(2, plan.transactionId()); statement.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
                if (player != null) Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("メダルをまとめる処理を完了できませんでした。もう一度お試しください。"));
            } catch (Exception failure) {
                plugin.getLogger().severe("PIRI_MEDAL_MERGE_ROLLBACK_FAILED tx=" + plan.transactionId() + " " + failure);
            }
        });
    }

    private static boolean activeSession(Connection connection, UUID player) throws Exception {
        try (var statement = connection.prepareStatement("SELECT 1 FROM player_sessions WHERE player_uuid=? AND lifecycle='ACTIVE' LIMIT 1")) {
            statement.setString(1, player.toString());
            try (var rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static void validateActive(Connection connection, Held item) throws Exception {
        String needle = "%" + item.bundleId() + "%";
        try (var review = connection.prepareStatement("SELECT 1 FROM medal_inventory_transactions WHERE status='REVIEW_REQUIRED' AND (before_bundle_json LIKE ? OR after_bundle_json LIKE ?) LIMIT 1")) {
            review.setString(1, needle); review.setString(2, needle);
            try (var rows = review.executeQuery()) { if (rows.next()) throw new IllegalStateException("TOKEN_REVIEW_REQUIRED"); }
        }
        if (matches(connection, "medal_tokens_unlimited", item) || matches(connection, "medal_tokens", item)) return;
        throw new IllegalStateException("INVALID_ITEM");
    }

    private static boolean matches(Connection connection, String table, Held item) throws Exception {
        try (var statement = connection.prepareStatement("SELECT amount,state FROM " + table + " WHERE bundle_id=?")) {
            statement.setString(1, item.bundleId().toString());
            try (var rows = statement.executeQuery()) { return rows.next() && rows.getInt("amount") == item.amount() && "ACTIVE".equals(rows.getString("state")); }
        }
    }

    private static void retire(Connection connection, UUID id, long now) throws Exception {
        int changed = updateState(connection, "medal_tokens_unlimited", id, "ACTIVE", "RETIRED", now);
        if (changed == 0) changed = updateState(connection, "medal_tokens", id, "ACTIVE", "RETIRED", now);
        if (changed != 1) throw new IllegalStateException("INVALID_ITEM");
    }

    private static void setStateAny(Connection connection, UUID id, String state, long now) throws Exception {
        int changed;
        try (var statement = connection.prepareStatement("UPDATE medal_tokens_unlimited SET state=?,updated_at=? WHERE bundle_id=?")) {
            statement.setString(1, state); statement.setLong(2, now); statement.setString(3, id.toString()); changed = statement.executeUpdate();
        }
        if (changed == 0) {
            try (var statement = connection.prepareStatement("UPDATE medal_tokens SET state=?,updated_at=? WHERE bundle_id=?")) {
                statement.setString(1, state); statement.setLong(2, now); statement.setString(3, id.toString()); changed = statement.executeUpdate();
            }
        }
        if (changed != 1) throw new IllegalStateException("INVALID_ITEM");
    }

    private static int updateState(Connection connection, String table, UUID id, String from, String to, long now) throws Exception {
        try (var statement = connection.prepareStatement("UPDATE " + table + " SET state=?,updated_at=? WHERE bundle_id=? AND state=?")) {
            statement.setString(1, to); statement.setLong(2, now); statement.setString(3, id.toString()); statement.setString(4, from); return statement.executeUpdate();
        }
    }

    private static void ensureUnlimitedTable(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS medal_tokens_unlimited (bundle_id TEXT PRIMARY KEY,amount INTEGER NOT NULL CHECK(amount>=1),state TEXT NOT NULL CHECK(state IN ('PENDING_DELIVERY','ACTIVE','RETIRED')),source_transaction_id TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        }
    }
}
