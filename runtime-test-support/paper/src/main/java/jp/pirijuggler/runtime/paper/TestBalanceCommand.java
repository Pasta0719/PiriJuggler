package jp.pirijuggler.runtime.paper;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.DriverManager;

/** Runtime-test-only exact session balance setter for Phase07 acceptance cases. */
public final class TestBalanceCommand implements CommandExecutor {
    private final JavaPlugin helper;
    public TestBalanceCommand(JavaPlugin helper) { this.helper = helper; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { sender.sendMessage("NOT_OP"); return true; }
        if (args.length < 2 || args.length > 3) return false;
        int credit;
        long held;
        try {
            credit = Integer.parseInt(args[0]); held = Long.parseLong(args[1]);
            if (credit < 0 || credit > 50 || held < 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) { sender.sendMessage("INVALID_BALANCE"); return true; }
        Player target = args.length == 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
        if (target == null) { sender.sendMessage("PLAYER_REQUIRED"); return true; }
        var production = (PiriJugglerPlugin) Bukkit.getPluginManager().getPlugin("PiriJuggler");
        if (production == null || !production.isEnabled() || !production.machines().ready()) { sender.sendMessage("PIRI_NOT_READY"); return true; }
        var session = production.machines().snapshot().session(target.getUniqueId());
        if (session == null) { sender.sendMessage("Open a registered Piri machine once, then run the command again."); return true; }
        if (!session.ready()) { sender.sendMessage("TEST_BALANCE_REQUIRES_SEATED_READY"); return true; }
        String sessionId = session.id().toString();
        var dbPath = production.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        sender.sendMessage("Setting test balance...");
        Bukkit.getScheduler().runTaskAsynchronously(helper, () -> {
            String result;
            try {
                Class.forName("org.sqlite.JDBC");
                try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                    try (var pragma = connection.createStatement()) { pragma.execute("PRAGMA busy_timeout=5000"); }
                    try (var statement = connection.prepareStatement("UPDATE player_sessions SET credit=?,held_medals=? WHERE session_id=?")) {
                        statement.setInt(1, credit); statement.setLong(2, held); statement.setString(3, sessionId);
                        if (statement.executeUpdate() != 1) throw new IllegalStateException("Session row missing");
                    }
                }
                result = "TEST_BALANCE_SET credit=" + credit + " held=" + held + ". Close the slot screen and right-click the same machine again to refresh.";
            } catch (Exception error) { result = "TEST_BALANCE_FAILED " + error; }
            String message = result;
            Bukkit.getScheduler().runTask(helper, () -> {
                sender.sendMessage(message); if (!sender.equals(target)) target.sendMessage(message);
            });
        });
        return true;
    }
}
