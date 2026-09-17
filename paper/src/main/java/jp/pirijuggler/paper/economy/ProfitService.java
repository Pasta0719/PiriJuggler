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
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Admin-only realized profit reporting, Vault crediting and physical button bindings. */
public final class ProfitService implements Listener {
    private enum Kind { SESSION, TOTAL, DEPOSIT }
    private record Binding(Kind kind, double amount) { }
    private record WithdrawalPlan(String transactionId, double amount, double availableBefore) { }

    private final PiriJugglerPlugin plugin;
    private final long startedAt;
    private final VaultBridge vault;
    private final File bindingsFile;
    private final Map<String, Binding> bindings = new HashMap<>();
    private final Set<UUID> pendingDeposits = new HashSet<>();

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
            case DEPOSIT -> "利益引出 " + money(binding.amount());
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
        plugin.executors().database(() -> queryProfit(cutoff, false), (profit, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Profit query failed", error);
                sender.sendMessage(Component.text("利益を取得できませんでした。"));
                return;
            }
            sender.sendMessage(Component.text((sessionOnly ? "再起動後の利益残高: " : "累計利益残高: ") + money(profit)));
        });
    }

    /**
     * Profit balance = successful loans - successful prize payouts - already/reserved profit withdrawals.
     * PREPARED/CALL_STARTED withdrawals are included when reservePending is true so two clicks cannot spend the same profit.
     */
    private double queryProfit(long cutoff, boolean reservePending) throws Exception {
        Class.forName("org.sqlite.JDBC");
        var dbFile = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            return queryProfit(db, cutoff, reservePending);
        }
    }

    private double queryProfit(Connection db, long cutoff, boolean reservePending) throws Exception {
        String withdrawalStatuses = reservePending ? "('PREPARED','CALL_STARTED','APPLIED')" : "('APPLIED')";
        String sql = "SELECT COALESCE(SUM(CASE " +
                "WHEN operation='LOAN' AND status='APPLIED' THEN vault_amount " +
                "WHEN operation='PRIZE_TO_VAULT' AND status='APPLIED' THEN -vault_amount " +
                "WHEN operation='PROFIT_WITHDRAW' AND status IN " + withdrawalStatuses + " THEN -vault_amount " +
                "ELSE 0 END),0) FROM economy_transactions" + (cutoff > 0 ? " WHERE updated_at>=?" : "");
        try (var ps = db.prepareStatement(sql)) {
            if (cutoff > 0) ps.setLong(1, cutoff);
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getDouble(1) : 0.0; }
        }
    }

    private void deposit(Player player, double amount) {
        if (vault == null) { player.sendMessage("Vault経済が利用できません。"); return; }
        UUID owner = player.getUniqueId();
        if (!pendingDeposits.add(owner)) { player.sendMessage("利益の処理中です。少し待ってからもう一度お試しください。"); return; }

        final double vaultBalance;
        try { vaultBalance = vault.balance(player); }
        catch (RuntimeException error) {
            pendingDeposits.remove(owner);
            plugin.getLogger().log(Level.SEVERE, "Could not read Vault balance before profit withdrawal", error);
            player.sendMessage("Vault経済を確認できませんでした。");
            return;
        }

        plugin.executors().database(() -> prepareWithdrawal(owner, amount, vaultBalance), (plan, error) -> {
            if (error != null) {
                pendingDeposits.remove(owner);
                plugin.getLogger().log(Level.SEVERE, "Profit withdrawal prepare failed", error);
                player.sendMessage("利益の処理に失敗しました。");
                return;
            }
            if (plan == null) {
                pendingDeposits.remove(owner);
                plugin.executors().database(() -> queryProfit(0L, true), (available, queryError) -> {
                    if (queryError != null) player.sendMessage("利益が不足しています。");
                    else player.sendMessage("利益が不足しています。現在の利益残高: " + money(available));
                });
                return;
            }
            markCallStartedAndDeposit(player, plan);
        });
    }

    private WithdrawalPlan prepareWithdrawal(UUID owner, double amount, double vaultBalance) throws Exception {
        Class.forName("org.sqlite.JDBC");
        var dbFile = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            db.setAutoCommit(false);
            try {
                try (var review = db.prepareStatement("SELECT 1 FROM economy_transactions WHERE operation='PROFIT_WITHDRAW' AND status='REVIEW_REQUIRED' LIMIT 1");
                     var rs = review.executeQuery()) {
                    if (rs.next()) throw new IllegalStateException("Profit withdrawal requires manual review");
                }
                double available = queryProfit(db, 0L, true);
                if (available + 0.000001 < amount) { db.rollback(); return null; }
                String tx = "PROFIT_WITHDRAW:" + UUID.randomUUID();
                long now = System.currentTimeMillis();
                try (var insert = db.prepareStatement("INSERT INTO economy_transactions(transaction_id,player_uuid,operation,vault_amount,item_snapshot_json,balance_before,status,created_at,updated_at) VALUES(?,?, 'PROFIT_WITHDRAW', ?,NULL,?,'PREPARED',?,?)")) {
                    insert.setString(1, tx);
                    insert.setString(2, owner.toString());
                    insert.setDouble(3, amount);
                    insert.setDouble(4, vaultBalance);
                    insert.setLong(5, now);
                    insert.setLong(6, now);
                    insert.executeUpdate();
                }
                db.commit();
                return new WithdrawalPlan(tx, amount, available);
            } catch (Exception error) {
                db.rollback();
                throw error;
            } finally {
                db.setAutoCommit(true);
            }
        }
    }

    private void markCallStartedAndDeposit(Player player, WithdrawalPlan plan) {
        plugin.executors().database(() -> {
            updateWithdrawalStatus(plan.transactionId(), "PREPARED", "CALL_STARTED");
            return Boolean.TRUE;
        }, (ignored, startError) -> {
            if (startError != null) {
                pendingDeposits.remove(player.getUniqueId());
                plugin.getLogger().log(Level.SEVERE, "Profit withdrawal could not enter CALL_STARTED", startError);
                player.sendMessage("利益の処理に失敗しました。");
                return;
            }

            final boolean deposited;
            try { deposited = vault.deposit(player, plan.amount()); }
            catch (RuntimeException uncertain) {
                plugin.getLogger().log(Level.SEVERE, "Profit Vault deposit outcome uncertain", uncertain);
                plugin.executors().database(() -> {
                    forceWithdrawalStatus(plan.transactionId(), "REVIEW_REQUIRED");
                    return Boolean.TRUE;
                }, (done, finishError) -> {
                    pendingDeposits.remove(player.getUniqueId());
                    player.sendMessage("Vault入金結果を確定できませんでした。管理者確認が必要です。");
                });
                return;
            }

            String finalStatus = deposited ? "APPLIED" : "ROLLED_BACK";
            plugin.executors().database(() -> {
                updateWithdrawalStatus(plan.transactionId(), "CALL_STARTED", finalStatus);
                return Boolean.TRUE;
            }, (done, finishError) -> {
                pendingDeposits.remove(player.getUniqueId());
                if (finishError != null) {
                    plugin.getLogger().log(Level.SEVERE, "Profit withdrawal finalization failed", finishError);
                    player.sendMessage("利益の記録に失敗しました。管理者確認が必要です。");
                    return;
                }
                if (!deposited) {
                    player.sendMessage("Vaultへの入金に失敗しました。利益は消費されていません。");
                    return;
                }
                player.sendMessage("利益から " + money(plan.amount()) + " をVaultへ移しました。現在残高: " + money(vault.balance(player)));
            });
        });
    }

    private void updateWithdrawalStatus(String transactionId, String expected, String next) throws Exception {
        Class.forName("org.sqlite.JDBC");
        var dbFile = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             var ps = db.prepareStatement("UPDATE economy_transactions SET status=?,updated_at=? WHERE transaction_id=? AND status=?")) {
            ps.setString(1, next);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, transactionId);
            ps.setString(4, expected);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Profit withdrawal state changed unexpectedly");
        }
    }

    private void forceWithdrawalStatus(String transactionId, String next) throws Exception {
        Class.forName("org.sqlite.JDBC");
        var dbFile = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             var ps = db.prepareStatement("UPDATE economy_transactions SET status=?,updated_at=? WHERE transaction_id=?")) {
            ps.setString(1, next);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, transactionId);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Profit withdrawal transaction missing");
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
        if (Math.abs(value - Math.rint(value)) < 0.000001) return String.format(Locale.JAPAN, "%,.0fピリ", value);
        return String.format(Locale.JAPAN, "%,.2fピリ", value);
    }
}
