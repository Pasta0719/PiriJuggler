package jp.pirijuggler.paper.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/** Canonical Piri prize item representation. */
public final class PrizeItem {
    public static final NamespacedKey ITEM_TYPE = new NamespacedKey("piri", "item_type");
    public static final NamespacedKey ITEM_VERSION = new NamespacedKey("piri", "item_version");

    public enum Type {
        SMALL("small", Material.GOLD_NUGGET, "Piri Prize - Small", "prize_small"),
        MEDIUM("medium", Material.GOLD_INGOT, "Piri Prize - Medium", "prize_medium"),
        LARGE("large", Material.GOLD_BLOCK, "Piri Prize - Large", "prize_large");

        private final String id;
        private final Material material;
        private final String displayName;
        private final String itemType;

        Type(String id, Material material, String displayName, String itemType) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
            this.itemType = itemType;
        }

        public String id() { return id; }
        public Material material() { return material; }
        public String displayName() { return displayName; }
        public String itemType() { return itemType; }

        public static Type parse(String value) {
            if (value == null) return null;
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "small" -> SMALL;
                case "medium" -> MEDIUM;
                case "large" -> LARGE;
                default -> null;
            };
        }
    }

    public static ItemStack create(Type type, int amount) {
        if (type == null || amount < 1 || amount > 64) throw new IllegalArgumentException("Invalid prize stack");
        ItemStack item = new ItemStack(type.material(), amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(type.displayName(), NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("PIRI JUGGLER 景品", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("/piri exchange " + type.id() + " でVaultへ交換", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        var pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_TYPE, PersistentDataType.STRING, type.itemType());
        pdc.set(ITEM_VERSION, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    public static Type read(ItemStack item) {
        if (item == null || item.getAmount() < 1 || !item.hasItemMeta()) return null;
        var meta = item.getItemMeta();
        if (!Integer.valueOf(1).equals(meta.getPersistentDataContainer().get(ITEM_VERSION, PersistentDataType.INTEGER))) return null;
        String raw = meta.getPersistentDataContainer().get(ITEM_TYPE, PersistentDataType.STRING);
        for (Type type : Type.values()) {
            if (type.itemType().equals(raw) && item.getType() == type.material()) return type;
        }
        return null;
    }

    private PrizeItem() { }
}
