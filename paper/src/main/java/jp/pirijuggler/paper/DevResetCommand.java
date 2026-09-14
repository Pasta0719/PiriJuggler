package jp.pirijuggler.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.DriverManager;

/**
 * OP-only development command that discards the in-progress game snapshot while preserving
 * CREDIT and held medals. This exists to recover rapidly during development when builds change.
 */
public final class DevResetCommand implements CommandExecutor {
    private final PiriJugglerPlugin plugin;

    public DevResetCommand(PiriJugglerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("NOT_OP");
            return true;
        }
        if (args.length > 1) return false;

        Player target = args.length == 1 ? Bukkit.getPlayerExact(args[0]) : sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage("PLAYER_REQUIRED");
            return true;
        }
        if (plugin.machines() == null || !plugin.machines().ready()) {
            sender.sendMessage("PIRI_NOT_READY");
            return true;
        }

        var session = plugin.machines().snapshot().session(target.getUniqueId());
        if (session == null) {
            sender.sendMessage("NO_PIRI_SESSION");
            return true;
        }

        String sessionId = session.id().toString();
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        sender.sendMessage("Resetting game snapshot; CREDIT and held medals will be preserved...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            try {
                Class.forName("org.sqlite.JDBC");
                try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                    try (var pragma = connection.createStatement()) {
                        pragma.execute("PRAGMA busy_timeout=5000");
                    }
                    String sql = "UPDATE player_sessions SET " +
                            "game_state='SEATED_READY',lifecycle='ACTIVE',spin_id=NULL,internal_role=NULL,premium_type=NULL," +
                            "notice_state='NONE',lamp_on=0,bonus_type=NULL,bonus_payout_count=0,current_bet=0,pay_display=0," +
                            "stopped_mask=0,phase_left=0,phase_center=0,phase_right=0,motion_profile=NULL,lock_expires_at=NULL,last_activity=? " +
                            "WHERE session_id=?";
                    try (var statement = connection.prepareStatement(sql)) {
                        statement.setLong(1, System.currentTimeMillis());
                        statement.setString(2, sessionId);
                        if (statement.executeUpdate() != 1) throw new IllegalStateException("Session row missing");
                    }
                }
                result = "PIRI_GAME_RESET. CREDIT/held preserved. Close the slot screen and right-click the machine again.";
            } catch (Exception error) {
                result = "PIRI_GAME_RESET_FAILED " + error;
            }
            String message = result;
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(message);
                if (!sender.equals(target)) target.sendMessage(message);
            });
        });
        return true;
    }
}
