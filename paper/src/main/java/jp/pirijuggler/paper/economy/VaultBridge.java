package jp.pirijuggler.paper.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import java.lang.reflect.Method;

/**
 * Runtime-only bridge to Vault. Piri remains buildable without bundling Vault or an economy provider.
 * All methods are called on the Paper main thread by economy services.
 */
public final class VaultBridge {
    private final Object provider;
    private final Method getBalance;
    private final Method withdrawPlayer;
    private final Method depositPlayer;
    private final Method transactionSuccess;

    private VaultBridge(Object provider, Method getBalance, Method withdrawPlayer, Method depositPlayer, Method transactionSuccess) {
        this.provider = provider;
        this.getBalance = getBalance;
        this.withdrawPlayer = withdrawPlayer;
        this.depositPlayer = depositPlayer;
        this.transactionSuccess = transactionSuccess;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static VaultBridge discover() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object provider = Bukkit.getServicesManager().load((Class) economyClass);
            if (provider == null) return null;
            Method getBalance = economyClass.getMethod("getBalance", OfflinePlayer.class);
            Method withdrawPlayer = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            Method depositPlayer = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            Method transactionSuccess = responseClass.getMethod("transactionSuccess");
            return new VaultBridge(provider, getBalance, withdrawPlayer, depositPlayer, transactionSuccess);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    public double balance(OfflinePlayer player) {
        try {
            Object value = getBalance.invoke(provider, player);
            if (!(value instanceof Number number)) throw new IllegalStateException("Vault getBalance returned non-number");
            double result = number.doubleValue();
            if (!Double.isFinite(result) || result < 0) throw new IllegalStateException("Vault returned invalid balance");
            return result;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Vault getBalance failed", error);
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        return invokeTransaction(withdrawPlayer, player, amount, "withdraw");
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return invokeTransaction(depositPlayer, player, amount, "deposit");
    }

    private boolean invokeTransaction(Method method, OfflinePlayer player, double amount, String operation) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("amount");
        try {
            Object response = method.invoke(provider, player, amount);
            return Boolean.TRUE.equals(transactionSuccess.invoke(response));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Vault " + operation + " failed", error);
        }
    }
}
