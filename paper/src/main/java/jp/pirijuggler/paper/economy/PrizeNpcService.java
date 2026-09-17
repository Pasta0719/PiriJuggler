package jp.pirijuggler.paper.economy;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.Comparator;

/** Persistent villager interaction point for player-facing prize exchange. */
public final class PrizeNpcService implements Listener {
    private final PiriJugglerPlugin plugin;
    private final PrizeService prizes;
    private final NamespacedKey marker;

    public PrizeNpcService(PiriJugglerPlugin plugin, PrizeService prizes) {
        this.plugin = plugin;
        this.prizes = prizes;
        this.marker = new NamespacedKey(plugin, "prize_exchange_npc");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("prize-npc")) return false;
        if (!sender.isOp()) { sender.sendMessage(Component.text("NOT_OP")); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("PLAYER_REQUIRED")); return true; }
        if (args.length != 2) { sender.sendMessage(Component.text("Usage: /piri prize-npc <create|remove>")); return true; }

        if (args[1].equalsIgnoreCase("create")) {
            Villager npc = player.getWorld().spawn(player.getLocation(), Villager.class, villager -> {
                villager.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
                villager.customName(Component.text("景品交換"));
                villager.setCustomNameVisible(true);
                villager.setAI(false);
                villager.setInvulnerable(true);
                villager.setSilent(true);
                villager.setCollidable(false);
                villager.setRemoveWhenFarAway(false);
                villager.setProfession(Villager.Profession.NONE);
            });
            sender.sendMessage(Component.text("PRIZE_NPC_CREATED " + npc.getUniqueId()));
            return true;
        }

        if (args[1].equalsIgnoreCase("remove")) {
            Villager npc = player.getNearbyEntities(8, 8, 8).stream()
                    .filter(Villager.class::isInstance).map(Villager.class::cast)
                    .filter(this::isPrizeNpc)
                    .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                    .orElse(null);
            if (npc == null) { sender.sendMessage(Component.text("PRIZE_NPC_NOT_FOUND")); return true; }
            npc.remove();
            sender.sendMessage(Component.text("PRIZE_NPC_REMOVED"));
            return true;
        }

        sender.sendMessage(Component.text("Usage: /piri prize-npc <create|remove>"));
        return true;
    }

    @EventHandler
    public void interact(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager npc) || !isPrizeNpc(npc)) return;
        event.setCancelled(true);
        prizes.handle(event.getPlayer(), new String[]{"prizes"});
    }

    private boolean isPrizeNpc(Villager villager) {
        return Byte.valueOf((byte) 1).equals(villager.getPersistentDataContainer().get(marker, PersistentDataType.BYTE));
    }
}
