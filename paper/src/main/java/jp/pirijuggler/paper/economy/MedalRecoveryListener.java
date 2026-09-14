package jp.pirijuggler.paper.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Reconciles medal inventory transactions that were durably ledger-committed but not marked APPLIED
 * before a hard process crash. Player-inventory transactions are reconciled when the player next joins.
 */
public final class MedalRecoveryListener implements Listener {
    private static final String UNLIMITED_TABLE = "medal_tokens_unlimited";
    private final PiriJugglerPlugin plugin;

    public MedalRecoveryListener(PiriJugglerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Let the login inventory finish loading before taking the authoritative item snapshot.
        Bukkit.getScheduler().runTaskLater(plugin, () -> reconcilePlayer(player), 20L);
    }

    private void reconcilePlayer(Player player) {
        if (!player.isOnline()) return;
        Set<UUID> present = new HashSet<>();
        for (int slot = 0; slot < 36; slot++) {
            MedalToken.Value value = MedalToken.read(player.getInventory().getItem(slot));
            if (value != null) present.add(value.bundleId());
        }

        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        if (!Files.exists(dbPath)) return;
        UUID playerId = player.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                try (var pragma = connection.createStatement()) {
                    pragma.execute("PRAGMA busy_timeout=5000");
                    pragma.execute("PRAGMA foreign_keys=ON");
                }
                ensureUnlimitedTable(connection);
                connection.setAutoCommit(false);
                try {
                    try (var query = connection.prepareStatement(
                            "SELECT transaction_id,before_bundle_json,after_bundle_json FROM medal_inventory_transactions " +
                                    "WHERE player_uuid=? AND status='LEDGER_COMMITTED' ORDER BY created_at,transaction_id")) {
                        query.setString(1, playerId.toString());
                        try (var rows = query.executeQuery()) {
                            while (rows.next()) {
                                String transactionId = rows.getString("transaction_id");
                                Set<UUID> before = ids(rows.getString("before_bundle_json"));
                                Set<UUID> after = ids(rows.getString("after_bundle_json"));
                                reconcileOne(connection, transactionId, before, after, present);
                            }
                        }
                    }
                    connection.commit();
                } catch (Exception failure) {
                    connection.rollback();
                    throw failure;
                }
            } catch (Exception failure) {
                plugin.getLogger().severe("PIRI_MEDAL_RECONCILE_FAILED player=" + playerId + " " + failure);
            }
        });
    }

    private void reconcileOne(Connection connection, String transactionId, Set<UUID> before, Set<UUID> after,
                              Set<UUID> present) throws Exception {
        boolean anyBefore = before.stream().anyMatch(present::contains);
        boolean anyAfter = after.stream().anyMatch(present::contains);
        boolean allBefore = !before.isEmpty() && present.containsAll(before);
        boolean allAfter = !after.isEmpty() && present.containsAll(after);
        boolean ledgerRowsComplete = allTokenRowsExist(connection, before) && allTokenRowsExist(connection, after);
        long now = System.currentTimeMillis();

        if (ledgerRowsComplete && allBefore && !anyAfter) {
            // Inventory still has the pre-mutation representation: restore ledger to match it.
            for (UUID id : before) setState(connection, id, "ACTIVE", now);
            for (UUID id : after) setState(connection, id, "RETIRED", now);
            setJournal(connection, transactionId, "ROLLED_BACK", now);
            plugin.getLogger().info("PIRI_MEDAL_RECONCILED tx=" + transactionId + " result=ROLLED_BACK before-only");
            return;
        }

        if (ledgerRowsComplete && allAfter && !anyBefore) {
            // Inventory already has the post-mutation representation: confirm the committed ledger.
            for (UUID id : before) setState(connection, id, "RETIRED", now);
            for (UUID id : after) setState(connection, id, "ACTIVE", now);
            setJournal(connection, transactionId, "APPLIED", now);
            plugin.getLogger().info("PIRI_MEDAL_RECONCILED tx=" + transactionId + " result=APPLIED after-only");
            return;
        }

        // Both sides, neither side, partial sides, or missing ledger rows are ambiguous. The REVIEW_REQUIRED
        // journal state itself makes every referenced bundle unusable through EconomyStore validation.
        setJournal(connection, transactionId, "REVIEW_REQUIRED", now);
        plugin.getLogger().severe("PIRI_MEDAL_REVIEW_REQUIRED tx=" + transactionId +
                " beforePresent=" + anyBefore + " afterPresent=" + anyAfter +
                " allBefore=" + allBefore + " allAfter=" + allAfter +
                " ledgerRowsComplete=" + ledgerRowsComplete);
    }

    private static Set<UUID> ids(String json) {
        Set<UUID> result = new LinkedHashSet<>();
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (JsonElement element : array) {
            String raw = element.getAsJsonObject().get("bundleId").getAsString();
            result.add(UUID.fromString(raw));
        }
        return result;
    }

    private static boolean allTokenRowsExist(Connection connection, Set<UUID> ids) throws Exception {
        for (UUID id : ids) if (tokenRowCount(connection, id) != 1) return false;
        return true;
    }

    private static int tokenRowCount(Connection connection, UUID id) throws Exception {
        int count = count(connection, "medal_tokens", id);
        count += count(connection, UNLIMITED_TABLE, id);
        return count;
    }

    private static int count(Connection connection, String table, UUID id) throws Exception {
        try (var query = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE bundle_id=?")) {
            query.setString(1, id.toString());
            try (var rows = query.executeQuery()) { return rows.next() ? rows.getInt(1) : 0; }
        }
    }

    private static void setState(Connection connection, UUID id, String state, long now) throws Exception {
        int changed = updateState(connection, "medal_tokens", id, state, now)
                + updateState(connection, UNLIMITED_TABLE, id, state, now);
        if (changed != 1) throw new IllegalStateException("Expected exactly one token row for " + id + ", changed=" + changed);
    }

    private static int updateState(Connection connection, String table, UUID id, String state, long now) throws Exception {
        try (var update = connection.prepareStatement("UPDATE " + table + " SET state=?,updated_at=? WHERE bundle_id=?")) {
            update.setString(1, state);
            update.setLong(2, now);
            update.setString(3, id.toString());
            return update.executeUpdate();
        }
    }

    private static void setJournal(Connection connection, String transactionId, String status, long now) throws Exception {
        try (var update = connection.prepareStatement(
                "UPDATE medal_inventory_transactions SET status=?,updated_at=? WHERE transaction_id=? AND status='LEDGER_COMMITTED'")) {
            update.setString(1, status);
            update.setLong(2, now);
            update.setString(3, transactionId);
            if (update.executeUpdate() != 1) throw new IllegalStateException("Journal state changed concurrently: " + transactionId);
        }
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
