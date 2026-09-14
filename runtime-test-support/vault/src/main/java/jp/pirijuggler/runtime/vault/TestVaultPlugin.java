package jp.pirijuggler.runtime.vault;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Isolated runtime-acceptance Vault/Economy provider. Never included in production jars. */
public final class TestVaultPlugin extends JavaPlugin {
    private final TestEconomyHandler economy = new TestEconomyHandler();

    @Override @SuppressWarnings({"unchecked", "rawtypes"})
    public void onEnable() {
        try {
            Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin("Vault");
            if (vaultPlugin == null || !vaultPlugin.isEnabled()) {
                throw new IllegalStateException("Vault plugin is required for PiriRuntimeVault");
            }
            ClassLoader vaultLoader = vaultPlugin.getClass().getClassLoader();
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy", true, vaultLoader);
            Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse", true, vaultLoader);
            Class<?> responseTypeClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse$ResponseType", true, vaultLoader);
            economy.bindResponse(responseClass, responseTypeClass);
            Object provider = Proxy.newProxyInstance(vaultLoader, new Class<?>[]{economyClass}, economy);
            Bukkit.getServicesManager().register((Class) economyClass, provider, this, ServicePriority.Highest);
            getLogger().info("PIRI_TEST_VAULT_READY priority=HIGHEST apiLoader=" + economyClass.getClassLoader());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            getLogger().severe("PIRI_TEST_VAULT_START_FAILED " + failure);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { sender.sendMessage("NOT_OP"); return true; }
        if (args.length < 1 || args.length > 3) return false;
        String action = args[0].toLowerCase();
        if (action.equals("faildeposit")) {
            if (args.length != 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) return false;
            economy.failDeposit = args[1].equalsIgnoreCase("on");
            sender.sendMessage("TEST_VAULT_FAIL_DEPOSIT " + economy.failDeposit);
            return true;
        }
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

    private static final class TestEconomyHandler implements InvocationHandler {
        private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
        private volatile boolean failDeposit;
        private Constructor<?> responseConstructor;
        private Class<? extends Enum> responseTypeClass;

        @SuppressWarnings("unchecked")
        void bindResponse(Class<?> responseClass, Class<?> responseTypeClass) throws NoSuchMethodException {
            this.responseTypeClass = (Class<? extends Enum>) responseTypeClass;
            this.responseConstructor = responseClass.getConstructor(double.class, double.class, responseTypeClass, String.class);
        }

        void set(OfflinePlayer player, double amount) { balances.put(player.getUniqueId(), amount); }
        double getBalance(OfflinePlayer player) { return balances.getOrDefault(player.getUniqueId(), 0.0); }

        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("toString")) return "PiriRuntimeVaultEconomy";
            if (name.equals("hashCode")) return System.identityHashCode(proxy);
            if (name.equals("equals")) return proxy == (args == null ? null : args[0]);

            if (name.equals("isEnabled")) return true;
            if (name.equals("getName")) return "PiriRuntimeVault";
            if (name.equals("hasBankSupport")) return false;
            if (name.equals("fractionalDigits")) return 2;
            if (name.equals("format")) return String.format("%.2f", ((Number) args[0]).doubleValue());
            if (name.equals("currencyNamePlural")) return "Piri Test Credits";
            if (name.equals("currencyNameSingular")) return "Piri Test Credit";
            if (name.equals("getBanks")) return Collections.emptyList();

            if ((name.equals("hasAccount") || name.equals("createPlayerAccount")) && args != null && args.length >= 1 && args[0] instanceof OfflinePlayer) {
                return true;
            }
            if (name.equals("getBalance") && args != null && args.length >= 1 && args[0] instanceof OfflinePlayer player) {
                return getBalance(player);
            }
            if (name.equals("withdrawPlayer") && args != null && args.length >= 2 && args[0] instanceof OfflinePlayer player && args[1] instanceof Number number) {
                double amount = number.doubleValue();
                double before = getBalance(player);
                if (!Double.isFinite(amount) || amount < 0 || before < amount) return response(false, 0, before, "insufficient funds");
                double after = before - amount;
                balances.put(player.getUniqueId(), after);
                return response(true, amount, after, null);
            }
            if (name.equals("depositPlayer") && args != null && args.length >= 2 && args[0] instanceof OfflinePlayer player && args[1] instanceof Number number) {
                double amount = number.doubleValue();
                double before = getBalance(player);
                if (failDeposit) return response(false, 0, before, "simulated deposit failure");
                if (!Double.isFinite(amount) || amount < 0) return response(false, 0, before, "invalid amount");
                double after = before + amount;
                balances.put(player.getUniqueId(), after);
                return response(true, amount, after, null);
            }

            if (name.startsWith("bank" ) || name.equals("createBank") || name.equals("deleteBank")) {
                return response(false, 0, 0, "bank unsupported");
            }

            Class<?> type = method.getReturnType();
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == double.class) return 0.0;
            return null;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Object response(boolean success, double amount, double balance, String error) throws ReflectiveOperationException {
            Enum type = Enum.valueOf((Class) responseTypeClass, success ? "SUCCESS" : "FAILURE");
            return responseConstructor.newInstance(amount, balance, type, error);
        }
    }
}
