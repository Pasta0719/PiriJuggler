package jp.pirijuggler.paper.economy;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.session.Session;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Keeps each player's Piri Medal inventory represented by at most one physical token. */
public final class MedalAutoMergeService implements Listener {
    private final PiriJugglerPlugin plugin;
    private final MedalMergeCommand merger;
    private final PluginCommand mergeCommand;
    private final Set<UUID> pending = new HashSet<>();

    public MedalAutoMergeService(PiriJugglerPlugin plugin, MedalMergeCommand merger) {
        this.plugin = plugin;
        this.merger = merger;
        this.mergeCommand = plugin.getCommand("pirimerge");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::scanOnline, 40L, 40L);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { schedule(event.getPlayer(), 20L); }
    @EventHandler public void onClose(InventoryCloseEvent event) { if (event.getPlayer() instanceof Player player) schedule(player, 1L); }
    @EventHandler public void onPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player) schedule(player, 1L); }

    private void scanOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) mergeIfNeeded(player);
    }

    private void schedule(Player player, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> mergeIfNeeded(player), delay);
    }

    private void mergeIfNeeded(Player player) {
        if (!player.isOnline() || pending.contains(player.getUniqueId()) || medalCount(player) < 2) return;
        if (plugin.machines() != null && plugin.machines().ready()) {
            Session session = plugin.machines().snapshot().session(player.getUniqueId());
            if (session != null && session.lifecycle() == Session.Lifecycle.ACTIVE) return;
        }
        pending.add(player.getUniqueId());
        try {
            merger.onCommand(player, mergeCommand, "pirimerge", new String[0]);
        } finally {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> pending.remove(player.getUniqueId()), 40L);
        }
    }

    private static int medalCount(Player player) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (MedalToken.read(player.getInventory().getItem(slot)) != null && ++count >= 2) return count;
        }
        return count;
    }
}
