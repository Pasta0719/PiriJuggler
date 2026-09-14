package jp.pirijuggler.paper.economy;

import jp.pirijuggler.paper.PiriJugglerPlugin;

import java.sql.DriverManager;

/**
 * Startup reconciliation for Vault operations whose external-call outcome is unknown.
 * CALL_STARTED must never be retried automatically because Vault may already have applied it.
 */
public final class EconomyStartupRecovery {
    private EconomyStartupRecovery() { }

    public static void reconcile(PiriJugglerPlugin plugin) {
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        try {
            Class.forName("org.sqlite.JDBC");
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                connection.setAutoCommit(false);
                try {
                    long now = System.currentTimeMillis();
                    int changed;
                    try (var update = connection.prepareStatement(
                            "UPDATE economy_transactions SET status='REVIEW_REQUIRED',updated_at=? WHERE status='CALL_STARTED'")) {
                        update.setLong(1, now);
                        changed = update.executeUpdate();
                    }
                    connection.commit();
                    if (changed > 0) {
                        plugin.getLogger().severe("PIRI_VAULT_STARTUP_REVIEW_REQUIRED count=" + changed
                                + " reason=INTERRUPTED_CALL_STARTED; automatic Vault retry intentionally suppressed");
                    }
                } catch (Exception failure) {
                    connection.rollback();
                    throw failure;
                }
            }
        } catch (Exception failure) {
            plugin.getLogger().severe("PIRI_VAULT_STARTUP_RECOVERY_FAILED " + failure);
        }
    }
}
