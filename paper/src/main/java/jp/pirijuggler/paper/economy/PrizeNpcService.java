package jp.pirijuggler.paper.economy;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Comparator;
import java.util.List;

/** Persistent interaction points for medal-to-prize and prize exchange GUIs. */
public final class PrizeNpcService implements Listener {
    private static final int SMALL_SLOT = 20;
    private static final int MEDIUM_SLOT = 22;
    private static final int LARGE_SLOT = 24;
    private static final int ALL_SLOT = 31;
    private static final int CLOSE_SLOT = 49;

    private static final class ExchangeHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final PiriJugglerPlugin plugin;
    private final PrizeService prizes;
    private final NamespacedKey marker;

    public PrizeNpcService(PiriJugglerPlugin plugin, PrizeService prizes) {
        this.plugin = plugin;
        this.prizes = prizes;
        this.marker = new NamespacedKey(plugin, "counter_type");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** OP-only placement/removal: /piri npc <prizes|exchange> <create|remove>. */
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("npc")) return false;
        if (!sender.isOp()) { sender.sendMessage(Component.text("NOT_OP")); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("PLAYER_REQUIRED")); return true; }
        if (args.length != 3) { usage(sender); return true; }

        String type = args[1].toLowerCase();
        if (!type.equals("prizes") && !type.equals("exchange")) { usage(sender); return true; }
        String action = args[2].toLowerCase();

        if (action.equals("create")) {
            Villager npc = player.getWorld().spawn(player.getLocation(), Villager.class, villager -> {
                villager.getPersistentDataContainer().set(marker, PersistentDataType.STRING, type);
                villager.customName(Component.text(type.equals("prizes") ? "景品カウンター" : "交換窓口"));
                villager.setCustomNameVisible(true);
                villager.setAI(false);
                villager.setInvulnerable(true);
                villager.setSilent(true);
                villager.setCollidable(false);
                villager.setRemoveWhenFarAway(false);
                villager.setProfession(Villager.Profession.NONE);
            });
            sender.sendMessage(Component.text("COUNTER_NPC_CREATED " + type + " " + npc.getUniqueId()));
            return true;
        }

        if (action.equals("remove")) {
            Villager npc = player.getNearbyEntities(8, 8, 8).stream()
                    .filter(Villager.class::isInstance).map(Villager.class::cast)
                    .filter(villager -> type.equals(counterType(villager)))
                    .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                    .orElse(null);
            if (npc == null) { sender.sendMessage(Component.text("COUNTER_NPC_NOT_FOUND " + type)); return true; }
            npc.remove();
            sender.sendMessage(Component.text("COUNTER_NPC_REMOVED " + type));
            return true;
        }

        usage(sender);
        return true;
    }

    @EventHandler
    public void interact(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager npc)) return;
        String type = counterType(npc);
        if (type == null) return;
        event.setCancelled(true);
        if (type.equals("prizes")) prizes.handle(event.getPlayer(), new String[]{"prizes"});
        else if (type.equals("exchange")) openExchange(event.getPlayer());
    }

    private void openExchange(Player player) {
        Inventory gui = plugin.getServer().createInventory(new ExchangeHolder(), 54, "景品交換窓口");
        gui.setItem(SMALL_SLOT, exchangeButton(Material.GOLD_NUGGET, "小景品を交換", "1個ずつ交換します"));
        gui.setItem(MEDIUM_SLOT, exchangeButton(Material.GOLD_INGOT, "中景品を交換", "1個ずつ交換します"));
        gui.setItem(LARGE_SLOT, exchangeButton(Material.GOLD_BLOCK, "大景品を交換", "1個ずつ交換します"));
        gui.setItem(ALL_SLOT, exchangeButton(Material.EMERALD, "すべて交換", "所持している景品をまとめて交換します"));
        gui.setItem(CLOSE_SLOT, exchangeButton(Material.BARRIER, "閉じる", ""));
        player.openInventory(gui);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ExchangeHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        switch (event.getRawSlot()) {
            case SMALL_SLOT -> prizes.handle(player, new String[]{"exchange", "small"});
            case MEDIUM_SLOT -> prizes.handle(player, new String[]{"exchange", "medium"});
            case LARGE_SLOT -> prizes.handle(player, new String[]{"exchange", "large"});
            case ALL_SLOT -> prizes.handle(player, new String[]{"exchange", "all"});
            case CLOSE_SLOT -> player.closeInventory();
            default -> { }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ExchangeHolder) event.setCancelled(true);
    }

    private ItemStack exchangeButton(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private String counterType(Villager villager) {
        String type = villager.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
        return type != null && (type.equals("prizes") || type.equals("exchange")) ? type : null;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /piri npc <prizes|exchange> <create|remove>"));
    }
}
