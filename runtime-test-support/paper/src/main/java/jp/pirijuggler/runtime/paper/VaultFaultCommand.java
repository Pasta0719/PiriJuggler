package jp.pirijuggler.runtime.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** Test-only toggle for simulating a failed Vault deposit without replacing the real economy provider. */
public final class VaultFaultCommand implements CommandExecutor {
    private static final String PROPERTY = "piri.runtime.failVaultDeposit";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) { sender.sendMessage("NOT_OP"); return true; }
        if (args.length != 1 || !(args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("status"))) {
            return false;
        }
        if (args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("PIRI_VAULT_FAULT " + Boolean.getBoolean(PROPERTY));
            return true;
        }
        boolean enabled = args[0].equalsIgnoreCase("on");
        System.setProperty(PROPERTY, Boolean.toString(enabled));
        sender.sendMessage("PIRI_VAULT_FAULT " + enabled);
        return true;
    }
}
