package jp.pirijuggler.runtime.paper;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.DriverManager;

/**
 * Runtime-test-only funding command. This class is never packaged in the production Paper JAR.
 * It exists only to make manual Minecraft acceptance testing possible before Phase07 economy is implemented.
 */
public final class DevFundCommand implements CommandExecutor {
    private final JavaPlugin helper;

    public DevFundCommand(JavaPlugin helper) {
        this.helper = helper;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("NOT_OP");
            return true;
        }
        if (args.length < 1 || args.length > 2 || !args[0].equalsIgnoreCase("fund")) {
            sender.sendMessage("Usage: /piritest fund [player]");
            return true;
        }

        Player target = args.length == 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage("PLAYER_REQUIRED");
            return true;
        }

        var production = (PiriJugglerPlugin) Bukkit.getPluginManager().getPlugin("PiriJuggler");
        if (production == null || !production.isEnabled() || !production.machines().ready()) {
            sender.sendMessage("PIRI_NOT_READY");
            return true;
        }

        var session = production.machines().snapshot().session(target.getUniqueId());
        if (session == null) {
            sender.sendMessage("Open a registered Piri machine once, then run the command again.");
            return true;
        }
        if (!session.ready()) {
            sender.sendMessage("Finish or reset the current game before test funding.");
            return true;
        }

        String sessionId = session.id().toString();
        var dbPath = production.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        sender.sendMessage("Funding test session...");

        Bukkit.getScheduler().runTaskAsynchronously(helper, () -> {
            String result;
            try {
                Class.forName("org.sqlite.JDBC");
                try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                    try (var pragma = connection.createStatement()) {
                        pragma.execute("PRAGMA busy_timeout=5000");
                    }
                    try (var statement = connection.prepareStatement("UPDATE player_sessions SET credit=50,held_medals=800 WHERE session_id=?")) {
                        statement.setString(1, sessionId);
                        if (statement.executeUpdate() != 1) throw new IllegalStateException("Session row missing");
                    }
                }
                result = "TEST_FUNDED credit=50 held=800. Close the slot screen and right-click the same machine again to refresh.";
            } catch (Exception error) {
                result = "TEST_FUND_FAILED " + error;
            }
            String message = result;
            Bukkit.getScheduler().runTask(helper, () -> {
                sender.sendMessage(message);
                if (!sender.equals(target)) target.sendMessage(message);
            });
        });
        return true;
    }
}
