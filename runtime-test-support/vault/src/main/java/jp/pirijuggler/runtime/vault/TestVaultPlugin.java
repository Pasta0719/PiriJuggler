package jp.pirijuggler.runtime.vault;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Isolated runtime-acceptance Vault/Economy provider. Never included in production jars. */
public final class TestVaultPlugin extends JavaPlugin {
    private final TestEconomy economy = new TestEconomy();

    @Override public void onEnable() {
        Bukkit.getServicesManager().register(Economy.class, economy, this, ServicePriority.Normal);
        getLogger().info("PIRI_TEST_VAULT_READY");
    }

    @Override public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { sender.sendMessage("NOT_OP"); return true; }
        if (args.length < 1 || args.length > 3) return false;
        String action = args[0].toLowerCase();
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (target == null) { sender.sendMessage("PLAYER_REQUIRED"); return true; }
        switch (action) {
            case "set" -> {
                if (args.length != 3) return false;
                try {
                    double amount = Double.parseDouble(args[2]);
                    if (!Double.isFinite(amount) || amount < 0) throw new NumberFormatException();
                    economy.set(target, amount);
                    sender.sendMessage("TEST_VAULT_SET " + target.getName() + " " + amount);
                } catch (NumberFormatException invalid) { sender.sendMessage("INVALID_AMOUNT"); }
                return true;
            }
            case "get" -> {
                if (args.length != 2 && !(args.length == 1 && sender instanceof Player)) return false;
                sender.sendMessage("TEST_VAULT_BALANCE " + target.getName() + " " + economy.getBalance(target));
                return true;
            }
            default -> { return false; }
        }
    }

    private static final class TestEconomy implements Economy {
        private final Map<UUID, Double> balances = new ConcurrentHashMap<>();

        void set(OfflinePlayer player, double amount) { balances.put(player.getUniqueId(), amount); }

        @Override public double getBalance(OfflinePlayer player) {
            return balances.getOrDefault(player.getUniqueId(), 0.0);
        }

        @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            double before = getBalance(player);
            if (!Double.isFinite(amount) || amount < 0 || before < amount) {
                return new EconomyResponse(0, before, EconomyResponse.ResponseType.FAILURE, "insufficient funds");
            }
            double after = before - amount;
            balances.put(player.getUniqueId(), after);
            return new EconomyResponse(amount, after, EconomyResponse.ResponseType.SUCCESS, null);
        }
    }
}
