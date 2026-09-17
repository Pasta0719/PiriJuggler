package jp.pirijuggler.paper.economy;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** Admin-only realized profit reporting, Vault crediting and physical button bindings. */
public final class ProfitService implements Listener {
    private enum Kind { SESSION, TOTAL, DEPOSIT }
    private record Binding(Kind kind, double amount) { }

    private final PiriJugglerPlugin plugin;
    private final long startedAt;
    private final VaultBridge vault;
    private final File bindingsFile;
    private final Map<String, Binding> bindings = new HashMap<>();

    public ProfitService(PiriJugglerPlugin plugin) {
        this.plugin = plugin;
        this.startedAt = System.currentTimeMillis();
        this.vault = VaultBridge.discover();
        this.bindingsFile = new File(plugin.getDataFolder(), "profit-buttons.yml");
        loadBindings();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("profit")) return false;
        if (!sender.isOp()) return true;
        if (args.length < 2) {
            sender.sendMessage("使い方: /piri profit session | total | deposit <金額>");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("session")) {
            if (args.length == 3 && args[2].equalsIgnoreCase("bind")) return bind(sender, new Binding(Kind.SESSION, 0));
            if (args.length != 2) { sender.sendMessage("使い方: /piri profit session [bind]"); return true; }
            showProfit(sender, true);
            return true;
        }
        if (action.equals("total")) {
            if (args.length == 3 && args[2].equalsIgnoreCase("bind")) return bind(sender, new Binding(Kind.TOTAL, 0));
            if (args.length != 2) { sender.sendMessage("使い方: /piri profit total [bind]"); return true; }
            showProfit(sender, false);
            return true;
        }
        if (action.equals("deposit")) {
            if (args.length != 3 && args.length != 4) {
                sender.sendMessage("使い方: /piri profit deposit <金額> [bind]");
                return true;
            }
            double amount;
            try { amount = Double.parseDouble(args[2]); }
            catch (NumberFormatException invalid) { sender.sendMessage("金額が正しくありません。"); return true; }
            if (!Double.isFinite(amount) || amount <= 0) { sender.sendMessage("金額は0より大きい数値を指定してください。"); return true; }
            if (args.length == 4) {
                if (!args[3].equalsIgnoreCase("bind")) { sender.sendMessage("使い方: /piri profit deposit <金額> [bind]"); return true; }
                return bind(sender, new Binding(Kind.DEPOSIT, amount));
            }
            if (!(sender instanceof Player player)) { sender.sendMessage("Vaultへの入金はゲーム内から実行してください。"); return true; }
            deposit(player, amount);
            return true;
        }

        sender.sendMessage("使い方: /piri profit session | total | deposit <金額>");
        return true;
    }

    private boolean bind(CommandSender sender, Binding binding) {
        if (!(sender instanceof Player player)) { sender.sendMessage("ボタンへの割り当てはゲーム内から実行してください。"); return true; }
        Block block = player.getTargetBlockExact(6);
        if (block == null || !isButton(block.getType())) {
            sender.sendMessage("6ブロック以内のボタンに照準を合わせて実行してください。");
            return true;
        }
        bindings.put(key(block), binding);
        saveBindings();
        String name = switch (binding.kind()) {
            case SESSION -> "再起動後利益";
            case TOTAL -> "累計利益";
            case DEPOSIT -> "Vault入金 " + money(binding.amount());
        };
        sender.sendMessage("ボタンに「" + name + "」を割り当てました。");
        return true;
    }

    @EventHandler
    public void onButton(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !isButton(block.getType())) return;
        Binding binding = bindings.get(key(block));
        if (binding == null) return;
        Player player = event.getPlayer();
        if (!player.isOp()) return;

        switch (binding.kind()) {
            case SESSION -> showProfit(player, true);
            case TOTAL -> showProfit(player, false);
            case DEPOSIT -> deposit(player, binding.amount());
        }
    }

    private void showProfit(CommandSender sender, boolean sessionOnly) {
        long cutoff = sessionOnly ? startedAt : 0L;
        plugin.executors().database(() -> queryProfit(cutoff), (profit, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Profit query failed", error);
                sender.sendMessage(Component.text("利益を取得できませんでした。"));
                return;
            }
            sender.sendMessage(Component.text((sessionOnly ? "再起動後の利益: " : "累計利益: ") + money(profit)));
        });
    }

    private double queryProfit(long cutoff) throws Exception {
        Class.forName("org.sqlite.JDBC");
        var dbFile = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            String sql = "SELECT COALESCE(SUM(CASE WHEN operation='LOAN' THEN vault_amount WHEN operation='PRIZE_TO_VAULT' THEN -vault_amount ELSE 0 END),0) " +
                    "FROM economy_transactions WHERE status='APPLIED'" + (cutoff > 0 ? " AND updated_at>=?" : "");
            try (var ps = db.prepareStatement(sql)) {
                if (cutoff > 0) ps.setLong(1, cutoff);
                try (var rs = ps.executeQuery()) { return rs.next() ? rs.getDouble(1) : 0.0; }
            }
        }
    }

    private void deposit(Player player, double amount) {
        if (vault == null) { player.sendMessage("Vault経済が利用できません。"); return; }
        try {
            if (!vault.deposit(player, amount)) { player.sendMessage("Vaultへの入金に失敗しました。"); return; }
            player.sendMessage("Vaultに " + money(amount) + " 入金しました。現在残高: " + money(vault.balance(player)));
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE, "Manual profit Vault deposit failed", error);
            player.sendMessage("Vaultへの入金に失敗しました。");
        }
    }

    private void loadBindings() {
        if (!bindingsFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(bindingsFile);
        var section = yaml.getConfigurationSection("bindings");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String kindRaw = section.getString(id + ".kind");
            if (kindRaw == null) continue;
            try {
                Kind kind = Kind.valueOf(kindRaw);
                double amount = section.getDouble(id + ".amount", 0.0);
                bindings.put(id, new Binding(kind, amount));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignored invalid profit button binding: " + id);
            }
        }
    }

    private void saveBindings() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : bindings.entrySet()) {
            yaml.set("bindings." + entry.getKey() + ".kind", entry.getValue().kind().name());
            yaml.set("bindings." + entry.getKey() + ".amount", entry.getValue().amount());
        }
        try { yaml.save(bindingsFile); }
        catch (IOException error) { plugin.getLogger().log(Level.SEVERE, "Could not save profit button bindings", error); }
    }

    private static String key(Block block) {
        String raw = block.getWorld().getName() + "\n" + block.getX() + "\n" + block.getY() + "\n" + block.getZ();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isButton(Material material) { return material.name().endsWith("_BUTTON"); }

    private static String money(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001) return String.format(Locale.JAPAN, "%,.0f円", value);
        return String.format(Locale.JAPAN, "%,.2f円", value);
    }
}
