package jp.pirijuggler.paper.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.List;
import java.util.UUID;

/** Canonical server-side representation of the physical Piri Medal token. */
public final class MedalToken {
    /** Physical Piri Medal items do not split into 500-medal bundles anymore. */
    public static final int MAX_AMOUNT = Integer.MAX_VALUE;
    public static final NamespacedKey ITEM_TYPE = new NamespacedKey("piri", "item_type");
    public static final NamespacedKey BUNDLE_ID = new NamespacedKey("piri", "bundle_id");
    public static final NamespacedKey MEDAL_AMOUNT = new NamespacedKey("piri", "medal_amount");
    public static final NamespacedKey ITEM_VERSION = new NamespacedKey("piri", "item_version");

    public record Value(UUID bundleId, int amount) {
        public Value {
            if (bundleId == null || amount < 1 || amount > MAX_AMOUNT) throw new IllegalArgumentException("Invalid medal token");
        }
    }

    public static ItemStack create(UUID bundleId, int amount) {
        Value value = new Value(bundleId, amount);
        ItemStack item = new ItemStack(Material.IRON_NUGGET, 1);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Piri Medal", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("SLOT MEDAL", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("残高  ", NamedTextColor.GRAY)
                        .append(Component.text(value.amount(), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                        .append(Component.text(" MEDALS", NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("台のCREDITへ投入できます", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("メダル残高はこの1枚にまとめて保持されます", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setMaxStackSize(1);
        var pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_TYPE, PersistentDataType.STRING, "medal");
        pdc.set(BUNDLE_ID, PersistentDataType.STRING, value.bundleId().toString());
        pdc.set(MEDAL_AMOUNT, PersistentDataType.INTEGER, value.amount());
        pdc.set(ITEM_VERSION, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    public static Value read(ItemStack item) {
        if (item == null || item.getType() != Material.IRON_NUGGET || item.getAmount() != 1 || !item.hasItemMeta()) return null;
        var meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        if (!"medal".equals(pdc.get(ITEM_TYPE, PersistentDataType.STRING))) return null;
        if (!Integer.valueOf(1).equals(pdc.get(ITEM_VERSION, PersistentDataType.INTEGER))) return null;
        String id = pdc.get(BUNDLE_ID, PersistentDataType.STRING);
        Integer amount = pdc.get(MEDAL_AMOUNT, PersistentDataType.INTEGER);
        if (id == null || amount == null || amount < 1 || amount > MAX_AMOUNT) return null;
        try { return new Value(UUID.fromString(id), amount); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private MedalToken() { }
}
