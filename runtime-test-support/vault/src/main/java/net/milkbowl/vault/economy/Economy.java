package net.milkbowl.vault.economy;

import org.bukkit.OfflinePlayer;

/** Minimal runtime-acceptance-only Vault Economy contract used by PiriJuggler reflection. */
public interface Economy {
    double getBalance(OfflinePlayer player);
    EconomyResponse withdrawPlayer(OfflinePlayer player, double amount);
    EconomyResponse depositPlayer(OfflinePlayer player, double amount);
}
