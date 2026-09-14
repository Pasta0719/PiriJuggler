package jp.pirijuggler.runtime.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** Test-only Vault fault injection without replacing the real economy provider. */
public final class VaultFaultCommand implements CommandExecutor {
    private static final String FAIL_PROPERTY = "piri.runtime.failVaultDeposit";
    private static final String THROW_PROPERTY = "piri.runtime.throwVaultDeposit";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) { sender.sendMessage("NOT_OP"); return true; }

        // Backward-compatible Phase08 rollback toggle.
        if (args.length == 1 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("status"))) {
            return handle(sender, FAIL_PROPERTY, "PIRI_VAULT_FAULT", args[0]);
        }

        // Unknown-outcome path: leaves the transaction in CALL_STARTED for startup reconciliation testing.
        if (args.length == 2 && args[0].equalsIgnoreCase("uncertain")
                && (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("status"))) {
            return handle(sender, THROW_PROPERTY, "PIRI_VAULT_UNCERTAIN_FAULT", args[1]);
        }
        return false;
    }

    private static boolean handle(CommandSender sender, String property, String prefix, String action) {
        if (action.equalsIgnoreCase("status")) {
            sender.sendMessage(prefix + " " + Boolean.getBoolean(property));
            return true;
        }
        boolean enabled = action.equalsIgnoreCase("on");
        System.setProperty(property, Boolean.toString(enabled));
        sender.sendMessage(prefix + " " + enabled);
        return true;
    }
}
