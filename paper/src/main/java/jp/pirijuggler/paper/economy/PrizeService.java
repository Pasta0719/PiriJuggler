package jp.pirijuggler.paper.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.database.StartupProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Phase08 prize GUI, medal-to-prize exchange and prize-to-Vault exchange. */
public final class PrizeService implements Listener {
    private static final String UNLIMITED_TABLE = "medal_tokens_unlimited";
    private static final int GUI_SIZE = 54;
    private static final int SMALL_SLOT = 20;
    private static final int MEDIUM_SLOT = 22;
    private static final int LARGE_SLOT = 24;
    private static final int MAX_SLOT = 31;
    private static final int CLOSE_SLOT = 49;

    private record PrizeDef(int medalCost, double vaultValue) { }
    private record Held(int slot, UUID bundleId, int amount) { }
    private record PrizePlan(int small, int medium, int large) {
        int totalCost(Map<PrizeItem.Type, PrizeDef> defs) {
            return Math.addExact(Math.addExact(Math.multiplyExact(small, defs.get(PrizeItem.Type.SMALL).medalCost()),
                            Math.multiplyExact(medium, defs.get(PrizeItem.Type.MEDIUM).medalCost())),
                    Math.multiplyExact(large, defs.get(PrizeItem.Type.LARGE).medalCost()));
        }
        int count(PrizeItem.Type type) { return switch (type) { case SMALL -> small; case MEDIUM -> medium; case LARGE -> large; }; }
        boolean empty() { return small == 0 && medium == 0 && large == 0; }
    }
    private record PrizeCapacity(int emptySlots, int smallRoom, int mediumRoom, int largeRoom) {
        int room(PrizeItem.Type type) {
            return switch (type) {
                case SMALL -> smallRoom;
                case MEDIUM -> mediumRoom;
                case LARGE -> largeRoom;
            };
        }

        int maxCount(PrizeItem.Type type) {
            return Math.addExact(room(type), Math.multiplyExact(emptySlots, 64));
        }

        boolean fits(PrizePlan plan) {
            int slots = slotsNeeded(plan.small(), smallRoom)
                    + slotsNeeded(plan.medium(), mediumRoom)
                    + slotsNeeded(plan.large(), largeRoom);
            return slots <= emptySlots;
        }

        private static int slotsNeeded(int count, int existingRoom) {
            int overflow = Math.max(0, count - existingRoom);
            return (overflow + 63) / 64;
        }
    }
    private record MedalMutation(String transactionId, List<Held> before, UUID remainderId, int remainderAmount,
                                 int destinationSlot, PrizePlan prizes, int cost) { }
    private record PrizeRemoval(PrizeItem.Type type, int count, double vaultAmount) { }

    private static final class PrizeHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final PiriJugglerPlugin plugin;
    private final Map<PrizeItem.Type, PrizeDef> defs = new EnumMap<>(PrizeItem.Type.class);
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final VaultBridge vault;

    public PrizeService(PiriJugglerPlugin plugin, Map<String, Object> config) {
        this.plugin = plugin;
        Map<String, Object> prizes = StartupProfile.map(config.get("prizes"));
        loadDef(prizes, PrizeItem.Type.SMALL);
        loadDef(prizes, PrizeItem.Type.MEDIUM);
        loadDef(prizes, PrizeItem.Type.LARGE);
        this.vault = VaultBridge.discover();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadDef(Map<String, Object> prizes, PrizeItem.Type type) {
        Map<String, Object> row = StartupProfile.map(prizes.get(type.id()));
        int cost = ((Number) row.get("medal_cost")).intValue();
        double value = ((Number) row.get("vault_value")).doubleValue();
        defs.put(type, new PrizeDef(cost, value));
    }

    /** Returns true when this is a Phase08 root /piri subcommand. */
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("prizes")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("PLAYER_REQUIRED"); return true; }
            open(player);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("exchange")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("PLAYER_REQUIRED"); return true; }
            exchangeCommand(player, args);
            return true;
        }
        return false;
    }

    private void open(Player player) {
        Inventory gui = Bukkit.createInventory(new PrizeHolder(), GUI_SIZE, "Piri Prize Exchange");
        gui.setItem(SMALL_SLOT, guiPrize(PrizeItem.Type.SMALL));
        gui.setItem(MEDIUM_SLOT, guiPrize(PrizeItem.Type.MEDIUM));
        gui.setItem(LARGE_SLOT, guiPrize(PrizeItem.Type.LARGE));
        ItemStack max = new ItemStack(Material.EMERALD);
        var maxMeta = max.getItemMeta();
        maxMeta.displayName(Component.text("最大交換", NamedTextColor.GREEN).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        maxMeta.lore(List.of(Component.text("所持メダルで消費枚数が最大になる組合せ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        max.setItemMeta(maxMeta);
        gui.setItem(MAX_SLOT, max);
        ItemStack close = new ItemStack(Material.BARRIER);
        var closeMeta = close.getItemMeta();
        closeMeta.displayName(Component.text("閉じる", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(closeMeta);
        gui.setItem(CLOSE_SLOT, close);
        player.openInventory(gui);
    }

    private ItemStack guiPrize(PrizeItem.Type type) {
        ItemStack item = PrizeItem.create(type, 1);
        var meta = item.getItemMeta();
        PrizeDef def = defs.get(type);
        meta.lore(List.of(
                Component.text("必要メダル: ", NamedTextColor.GRAY)
                        .append(Component.text(def.medalCost(), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("クリック: 1個 / Shift: 最大個数", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PrizeHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) { player.closeInventory(); return; }
        if (slot == MAX_SLOT) { requestPrizeExchange(player, null, true, false); return; }
        PrizeItem.Type type = switch (slot) {
            case SMALL_SLOT -> PrizeItem.Type.SMALL;
            case MEDIUM_SLOT -> PrizeItem.Type.MEDIUM;
            case LARGE_SLOT -> PrizeItem.Type.LARGE;
            default -> null;
        };
        if (type != null) requestPrizeExchange(player, type, event.isShiftClick(), false);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PrizeHolder) event.setCancelled(true);
    }

    private void requestPrizeExchange(Player player, PrizeItem.Type requested, boolean max, boolean commandPath) {
        UUID owner = player.getUniqueId();
        if (!pending.add(owner)) { player.sendMessage("BUSY"); return; }
        List<Held> held = heldMedals(player);
        if (held.isEmpty()) { pending.remove(owner); player.sendMessage("NOT_ENOUGH_MEDALS"); return; }
        long totalLong = 0;
        try { for (Held item : held) totalLong = Math.addExact(totalLong, item.amount()); }
        catch (ArithmeticException overflow) { pending.remove(owner); player.sendMessage("INVALID_ITEM"); return; }
        if (totalLong > Integer.MAX_VALUE) { pending.remove(owner); player.sendMessage("INVALID_ITEM"); return; }
        int total = (int) totalLong;
        PrizePlan plan;
        if (requested == null) {
            plan = maximumPlan(player, held, total);
        } else if (max) {
            int cost = defs.get(requested).medalCost();
            int affordable = total / cost;
            int exactRemainder = total - affordable * cost;
            PrizeCapacity capacity = prizeCapacity(player, held, exactRemainder > 0);
            int maximum = Math.min(affordable, capacity.maxCount(requested));
            if (maximum < affordable && exactRemainder == 0) {
                capacity = prizeCapacity(player, held, true);
                maximum = Math.min(affordable, capacity.maxCount(requested));
            }
            plan = singlePlan(requested, maximum);
        } else {
            plan = singlePlan(requested, 1);
        }
        if (plan.empty()) { pending.remove(owner); player.sendMessage("NOT_ENOUGH_MEDALS"); return; }
        int cost = plan.totalCost(defs);
        if (cost > total) { pending.remove(owner); player.sendMessage("NOT_ENOUGH_MEDALS"); return; }
        if (!fitsAfterMedals(player, held, plan, total - cost)) { pending.remove(owner); player.sendMessage("INVENTORY_FULL"); return; }
        prepareMedalMutation(player, held, plan, cost, commandPath);
    }

    private PrizePlan singlePlan(PrizeItem.Type type, int count) {
        return switch (type) {
            case SMALL -> new PrizePlan(count, 0, 0);
            case MEDIUM -> new PrizePlan(0, count, 0);
            case LARGE -> new PrizePlan(0, 0, count);
        };
    }

    /** Exact search over bounded inventory capacity. Tie order: large, then medium, then small. */
    private PrizePlan maximumPlan(Player player, List<Held> held, int medals) {
        PrizePlan best = new PrizePlan(0, 0, 0);
        int bestCost = 0;
        int largeCost = defs.get(PrizeItem.Type.LARGE).medalCost();
        int mediumCost = defs.get(PrizeItem.Type.MEDIUM).medalCost();
        int smallCost = defs.get(PrizeItem.Type.SMALL).medalCost();
        PrizeCapacity withRemainder = prizeCapacity(player, held, true);
        PrizeCapacity withoutRemainder = prizeCapacity(player, held, false);
        int maxLarge = Math.min(medals / largeCost, withoutRemainder.maxCount(PrizeItem.Type.LARGE));
        for (int large = maxLarge; large >= 0; large--) {
            int remainingAfterLarge = medals - large * largeCost;
            int maxMedium = Math.min(remainingAfterLarge / mediumCost, withoutRemainder.maxCount(PrizeItem.Type.MEDIUM));
            for (int medium = maxMedium; medium >= 0; medium--) {
                int rest = remainingAfterLarge - medium * mediumCost;
                int small = Math.min(rest / smallCost, withoutRemainder.maxCount(PrizeItem.Type.SMALL));
                int cost = large * largeCost + medium * mediumCost + small * smallCost;
                if (cost < bestCost) continue;
                PrizePlan candidate = new PrizePlan(small, medium, large);
                PrizeCapacity capacity = cost == medals ? withoutRemainder : withRemainder;
                if (!capacity.fits(candidate)) continue;
                if (cost > bestCost || betterTie(candidate, best)) {
                    best = candidate;
                    bestCost = cost;
                }
            }
        }
        return best;
    }

    private static boolean betterTie(PrizePlan a, PrizePlan b) {
        if (a.large() != b.large()) return a.large() > b.large();
        if (a.medium() != b.medium()) return a.medium() > b.medium();
        return a.small() > b.small();
    }

    private PrizeCapacity prizeCapacity(Player player, List<Held> held, boolean hasRemainder) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean[] medalSlots = new boolean[contents.length];
        for (Held item : held) medalSlots[item.slot()] = true;
        int remainderSlot = hasRemainder ? held.getFirst().slot() : -1;
        int emptySlots = 0;
        int smallRoom = 0;
        int mediumRoom = 0;
        int largeRoom = 0;
        for (int i = 0; i < contents.length; i++) {
            if (medalSlots[i]) {
                if (i != remainderSlot) emptySlots++;
                continue;
            }
            ItemStack item = contents[i];
            if (item == null || item.isEmpty()) {
                emptySlots++;
                continue;
            }
            PrizeItem.Type type = PrizeItem.read(item);
            if (type == null) continue;
            int room = Math.max(0, 64 - item.getAmount());
            switch (type) {
                case SMALL -> smallRoom += room;
                case MEDIUM -> mediumRoom += room;
                case LARGE -> largeRoom += room;
            }
        }
        return new PrizeCapacity(emptySlots, smallRoom, mediumRoom, largeRoom);
    }

    private boolean fitsAfterMedals(Player player, List<Held> held, PrizePlan plan, int remainder) {
        ItemStack[] simulated = cloneContents(player.getInventory().getStorageContents());
        for (Held item : held) simulated[item.slot()] = null;
        if (remainder > 0) simulated[held.getFirst().slot()] = MedalToken.create(UUID.randomUUID(), remainder);
        return canAdd(simulated, PrizeItem.Type.SMALL, plan.small())
                && canAdd(simulated, PrizeItem.Type.MEDIUM, plan.medium())
                && canAdd(simulated, PrizeItem.Type.LARGE, plan.large());
    }

    private static boolean canAdd(ItemStack[] contents, PrizeItem.Type type, int count) {
        int remaining = count;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (PrizeItem.read(item) == type) {
                int room = Math.max(0, 64 - item.getAmount());
                int add = Math.min(room, remaining);
                if (add > 0) {
                    contents[i] = PrizeItem.create(type, item.getAmount() + add);
                    remaining -= add;
                }
            }
        }
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (contents[i] == null || contents[i].isEmpty()) {
                int add = Math.min(64, remaining);
                contents[i] = PrizeItem.create(type, add);
                remaining -= add;
            }
        }
        return remaining == 0;
    }

    private void prepareMedalMutation(Player player, List<Held> held, PrizePlan prizes, int cost, boolean commandPath) {
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        UUID owner = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            MedalMutation plan;
            try (Connection connection = open(dbPath.toString())) {
                ensureUnlimitedTable(connection);
                connection.setAutoCommit(false);
                try {
                    ensureNoReview(connection, owner);
                    long total = 0;
                    JsonArray beforeJson = new JsonArray();
                    for (Held item : held) {
                        validateActive(connection, item);
                        total = Math.addExact(total, item.amount());
                        JsonObject json = bundleJson(item.bundleId(), item.amount(), item.slot());
                        beforeJson.add(json);
                    }
                    if (cost < 1 || cost > total) throw new IllegalStateException("NOT_ENOUGH_MEDALS");
                    int remainder = Math.toIntExact(total - cost);
                    long now = System.currentTimeMillis();
                    for (Held item : held) retire(connection, item.bundleId(), now);
                    UUID remainderId = null;
                    JsonArray afterJson = new JsonArray();
                    if (remainder > 0) {
                        remainderId = UUID.randomUUID();
                        insertUnlimited(connection, remainderId, remainder, now);
                        afterJson.add(bundleJson(remainderId, remainder, held.getFirst().slot()));
                    }
                    String tx = UUID.randomUUID().toString();
                    try (var journal = connection.prepareStatement(
                            "INSERT INTO medal_inventory_transactions(transaction_id,player_uuid,operation,before_bundle_json,after_bundle_json,container_snapshot_json,status,created_at,updated_at) " +
                                    "VALUES(?,?,'PRIZE_EXCHANGE',?,?,?,'LEDGER_COMMITTED',?,?)")) {
                        journal.setString(1, tx);
                        journal.setString(2, owner.toString());
                        journal.setString(3, beforeJson.toString());
                        journal.setString(4, afterJson.toString());
                        journal.setString(5, "{\"container\":\"PLAYER_INVENTORY\"}");
                        journal.setLong(6, now);
                        journal.setLong(7, now);
                        journal.executeUpdate();
                    }
                    connection.commit();
                    plan = new MedalMutation(tx, List.copyOf(held), remainderId, remainder, held.getFirst().slot(), prizes, cost);
                } catch (Exception failure) {
                    connection.rollback();
                    throw failure;
                }
            } catch (Exception failure) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pending.remove(owner);
                    player.sendMessage(normalizeFailure(failure));
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> applyMedalMutation(player, plan));
        });
    }

    private void applyMedalMutation(Player player, MedalMutation plan) {
        UUID owner = player.getUniqueId();
        if (!player.isOnline()) { rollbackMedalAsync(plan, owner, null); return; }
        for (Held expected : plan.before()) {
            MedalToken.Value current = MedalToken.read(player.getInventory().getItem(expected.slot()));
            if (current == null || !current.bundleId().equals(expected.bundleId()) || current.amount() != expected.amount()) {
                rollbackMedalAsync(plan, owner, player);
                return;
            }
        }
        ItemStack[] backup = cloneContents(player.getInventory().getStorageContents());
        try {
            for (Held item : plan.before()) player.getInventory().setItem(item.slot(), null);
            if (plan.remainderAmount() > 0) player.getInventory().setItem(plan.destinationSlot(), MedalToken.create(plan.remainderId(), plan.remainderAmount()));
            addPrizeStacks(player, PrizeItem.Type.SMALL, plan.prizes().small());
            addPrizeStacks(player, PrizeItem.Type.MEDIUM, plan.prizes().medium());
            addPrizeStacks(player, PrizeItem.Type.LARGE, plan.prizes().large());
            player.updateInventory();
        } catch (RuntimeException failure) {
            player.getInventory().setStorageContents(backup);
            player.updateInventory();
            rollbackMedalAsync(plan, owner, player);
            return;
        }
        finishMedalAsync(plan, owner, player);
    }

    private static void addPrizeStacks(Player player, PrizeItem.Type type, int count) {
        int remaining = count;
        while (remaining > 0) {
            int stack = Math.min(64, remaining);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(PrizeItem.create(type, stack));
            if (!leftovers.isEmpty()) throw new IllegalStateException("INVENTORY_FULL");
            remaining -= stack;
        }
    }

    private void finishMedalAsync(MedalMutation plan, UUID owner, Player player) {
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = open(dbPath.toString());
                 var update = connection.prepareStatement("UPDATE medal_inventory_transactions SET status='APPLIED',updated_at=? WHERE transaction_id=? AND status='LEDGER_COMMITTED'")) {
                update.setLong(1, System.currentTimeMillis());
                update.setString(2, plan.transactionId());
                if (update.executeUpdate() != 1) throw new IllegalStateException("JOURNAL_CHANGED");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pending.remove(owner);
                    player.sendMessage("PIRI_PRIZE_EXCHANGED medals=" + plan.cost() + " small=" + plan.prizes().small() + " medium=" + plan.prizes().medium() + " large=" + plan.prizes().large());
                });
            } catch (Exception failure) {
                plugin.getLogger().severe("PIRI_PRIZE_APPLY_MARK_FAILED tx=" + plan.transactionId() + " " + failure);
                Bukkit.getScheduler().runTask(plugin, () -> pending.remove(owner));
            }
        });
    }

    private void rollbackMedalAsync(MedalMutation plan, UUID owner, Player player) {
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = open(dbPath.toString())) {
                ensureUnlimitedTable(connection);
                connection.setAutoCommit(false);
                try {
                    long now = System.currentTimeMillis();
                    if (plan.remainderId() != null) setStateAny(connection, plan.remainderId(), "RETIRED", now);
                    for (Held item : plan.before()) setStateAny(connection, item.bundleId(), "ACTIVE", now);
                    try (var update = connection.prepareStatement("UPDATE medal_inventory_transactions SET status='ROLLED_BACK',updated_at=? WHERE transaction_id=?")) {
                        update.setLong(1, now); update.setString(2, plan.transactionId()); update.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pending.remove(owner);
                    if (player != null) player.sendMessage("PIRI_PRIZE_EXCHANGE_RETRY");
                });
            } catch (Exception failure) {
                plugin.getLogger().severe("PIRI_PRIZE_ROLLBACK_FAILED tx=" + plan.transactionId() + " " + failure);
                Bukkit.getScheduler().runTask(plugin, () -> pending.remove(owner));
            }
        });
    }

    private void exchangeCommand(Player player, String[] args) {
        if (args.length < 2 || args.length > 3) { player.sendMessage("Usage: /piri exchange <small|medium|large> [count] | /piri exchange all"); return; }
        UUID owner = player.getUniqueId();
        if (!pending.add(owner)) { player.sendMessage("BUSY"); return; }
        if (vault == null) { pending.remove(owner); player.sendMessage("ECONOMY_UNAVAILABLE"); return; }
        String kind = args[1].toLowerCase(Locale.ROOT);
        if (kind.equals("all")) {
            if (args.length != 2) { pending.remove(owner); player.sendMessage("INVALID_STATE"); return; }
            int small = countPrizes(player, PrizeItem.Type.SMALL);
            int medium = countPrizes(player, PrizeItem.Type.MEDIUM);
            int large = countPrizes(player, PrizeItem.Type.LARGE);
            if (small + medium + large == 0) { pending.remove(owner); player.sendMessage("NO_PRIZES"); return; }
            double amount = small * defs.get(PrizeItem.Type.SMALL).vaultValue()
                    + medium * defs.get(PrizeItem.Type.MEDIUM).vaultValue()
                    + large * defs.get(PrizeItem.Type.LARGE).vaultValue();
            prepareVaultExchange(player, null, -1, amount);
            return;
        }
        PrizeItem.Type type = PrizeItem.Type.parse(kind);
        if (type == null) { pending.remove(owner); player.sendMessage("INVALID_STATE"); return; }
        int count = 1;
        if (args.length == 3) {
            try { count = Integer.parseInt(args[2]); }
            catch (NumberFormatException invalid) { pending.remove(owner); player.sendMessage("INVALID_STATE"); return; }
        }
        if (count < 1) { pending.remove(owner); player.sendMessage("INVALID_STATE"); return; }
        if (countPrizes(player, type) < count) { pending.remove(owner); player.sendMessage("NOT_ENOUGH_PRIZES"); return; }
        prepareVaultExchange(player, type, count, count * defs.get(type).vaultValue());
    }

    private void prepareVaultExchange(Player player, PrizeItem.Type type, int count, double vaultAmount) {
        UUID owner = player.getUniqueId();
        ItemStack[] backup = cloneContents(player.getInventory().getStorageContents());
        JsonObject snapshot = prizeSnapshot(backup, type, count);
        double balanceBefore;
        try { balanceBefore = vault.balance(player); }
        catch (RuntimeException failure) { pending.remove(owner); player.sendMessage("VAULT_ERROR"); return; }
        String tx = UUID.randomUUID().toString();
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = open(dbPath.toString())) {
                connection.setAutoCommit(false);
                try {
                    ensureNoEconomyReview(connection, owner);
                    long now = System.currentTimeMillis();
                    try (var insert = connection.prepareStatement(
                            "INSERT INTO economy_transactions(transaction_id,player_uuid,operation,vault_amount,item_snapshot_json,balance_before,status,created_at,updated_at) " +
                                    "VALUES(?,?,'PRIZE_TO_VAULT',?,?,?,'PREPARED',?,?)")) {
                        insert.setString(1, tx); insert.setString(2, owner.toString()); insert.setDouble(3, vaultAmount);
                        insert.setString(4, snapshot.toString()); insert.setDouble(5, balanceBefore); insert.setLong(6, now); insert.setLong(7, now); insert.executeUpdate();
                    }
                    try (var update = connection.prepareStatement("UPDATE economy_transactions SET status='CALL_STARTED',updated_at=? WHERE transaction_id=? AND status='PREPARED'")) {
                        update.setLong(1, now); update.setString(2, tx); if (update.executeUpdate() != 1) throw new IllegalStateException("JOURNAL_CHANGED");
                    }
                    connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
            } catch (Exception failure) {
                Bukkit.getScheduler().runTask(plugin, () -> { pending.remove(owner); player.sendMessage(normalizeFailure(failure)); });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> performVaultDeposit(player, type, count, vaultAmount, backup, tx));
        });
    }

    private void performVaultDeposit(Player player, PrizeItem.Type type, int count, double vaultAmount, ItemStack[] backup, String tx) {
        UUID owner = player.getUniqueId();
        if (!player.isOnline() || !samePrizeState(player.getInventory().getStorageContents(), backup)) {
            markEconomyReview(tx, owner, "INVENTORY_CHANGED_BEFORE_VAULT");
            return;
        }
        if (!removePrizes(player, type, count)) {
            player.getInventory().setStorageContents(backup); player.updateInventory();
            rollbackEconomy(tx, owner, player, "NOT_ENOUGH_PRIZES");
            return;
        }
        final boolean deposited;
        try { deposited = vault.deposit(player, vaultAmount); }
        catch (RuntimeException uncertain) {
            plugin.getLogger().severe("PIRI_VAULT_REVIEW_REQUIRED transactionId=" + tx + " operation=PRIZE_TO_VAULT expected=" + vaultAmount + " reason=CALL_EXCEPTION " + uncertain);
            pending.remove(owner);
            player.sendMessage("VAULT_REVIEW_REQUIRED");
            return;
        }
        if (!deposited) {
            player.getInventory().setStorageContents(backup); player.updateInventory();
            rollbackEconomy(tx, owner, player, "VAULT_ERROR");
            return;
        }
        markEconomyApplied(tx, owner, player, vaultAmount);
    }

    /** all uses the required small -> medium -> large removal order. */
    private static boolean removePrizes(Player player, PrizeItem.Type type, int count) {
        if (type != null) return removeType(player, type, count);
        int[] amounts = { countPrizes(player, PrizeItem.Type.SMALL), countPrizes(player, PrizeItem.Type.MEDIUM), countPrizes(player, PrizeItem.Type.LARGE) };
        return removeType(player, PrizeItem.Type.SMALL, amounts[0])
                && removeType(player, PrizeItem.Type.MEDIUM, amounts[1])
                && removeType(player, PrizeItem.Type.LARGE, amounts[2]);
    }

    private static boolean removeType(Player player, PrizeItem.Type type, int count) {
        int remaining = count;
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (PrizeItem.read(item) != type) continue;
            int take = Math.min(remaining, item.getAmount());
            int left = item.getAmount() - take;
            player.getInventory().setItem(slot, left == 0 ? null : PrizeItem.create(type, left));
            remaining -= take;
        }
        player.updateInventory();
        return remaining == 0;
    }

    private void markEconomyApplied(String tx, UUID owner, Player player, double amount) {
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = open(dbPath.toString());
                 var update = connection.prepareStatement("UPDATE economy_transactions SET status='APPLIED',updated_at=? WHERE transaction_id=? AND status='CALL_STARTED'")) {
                update.setLong(1, System.currentTimeMillis()); update.setString(2, tx);
                if (update.executeUpdate() != 1) throw new IllegalStateException("JOURNAL_CHANGED");
                Bukkit.getScheduler().runTask(plugin, () -> { pending.remove(owner); player.sendMessage("PIRI_VAULT_EXCHANGED amount=" + amount); });
            } catch (Exception failure) {
                plugin.getLogger().severe("PIRI_VAULT_APPLY_MARK_FAILED tx=" + tx + " " + failure);
                Bukkit.getScheduler().runTask(plugin, () -> pending.remove(owner));
            }
        });
    }

    private void rollbackEconomy(String tx, UUID owner, Player player, String message) {
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = open(dbPath.toString());
                 var update = connection.prepareStatement("UPDATE economy_transactions SET status='ROLLED_BACK',updated_at=? WHERE transaction_id=? AND status IN ('PREPARED','CALL_STARTED')")) {
                update.setLong(1, System.currentTimeMillis()); update.setString(2, tx); update.executeUpdate();
            } catch (Exception failure) { plugin.getLogger().severe("PIRI_VAULT_ROLLBACK_MARK_FAILED tx=" + tx + " " + failure); }
            Bukkit.getScheduler().runTask(plugin, () -> { pending.remove(owner); player.sendMessage(message); });
        });
    }

    private void markEconomyReview(String tx, UUID owner, String reason) {
        var dbPath = plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = open(dbPath.toString());
                 var update = connection.prepareStatement("UPDATE economy_transactions SET status='REVIEW_REQUIRED',updated_at=? WHERE transaction_id=? AND status='CALL_STARTED'")) {
                update.setLong(1, System.currentTimeMillis()); update.setString(2, tx); update.executeUpdate();
                plugin.getLogger().severe("PIRI_VAULT_REVIEW_REQUIRED transactionId=" + tx + " operation=PRIZE_TO_VAULT reason=" + reason);
            } catch (Exception failure) { plugin.getLogger().severe("PIRI_VAULT_REVIEW_MARK_FAILED tx=" + tx + " " + failure); }
            Bukkit.getScheduler().runTask(plugin, () -> pending.remove(owner));
        });
    }

    private static JsonObject prizeSnapshot(ItemStack[] contents, PrizeItem.Type requested, int count) {
        JsonObject root = new JsonObject();
        JsonArray slots = new JsonArray();
        for (int i = 0; i < contents.length; i++) {
            PrizeItem.Type type = PrizeItem.read(contents[i]);
            if (type == null) continue;
            JsonObject row = new JsonObject(); row.addProperty("slot", i); row.addProperty("type", type.id()); row.addProperty("count", contents[i].getAmount()); slots.add(row);
        }
        root.add("slots", slots);
        root.addProperty("requested", requested == null ? "all" : requested.id());
        root.addProperty("count", count);
        return root;
    }

    private static boolean samePrizeState(ItemStack[] current, ItemStack[] before) {
        if (current.length != before.length) return false;
        for (int i = 0; i < current.length; i++) {
            PrizeItem.Type a = PrizeItem.read(current[i]);
            PrizeItem.Type b = PrizeItem.read(before[i]);
            int ac = a == null ? 0 : current[i].getAmount();
            int bc = b == null ? 0 : before[i].getAmount();
            if (a != b || ac != bc) return false;
        }
        return true;
    }

    private static int countPrizes(Player player, PrizeItem.Type type) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (PrizeItem.read(item) == type) total = Math.addExact(total, item.getAmount());
        }
        return total;
    }

    private static List<Held> heldMedals(Player player) {
        List<Held> result = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            MedalToken.Value value = MedalToken.read(player.getInventory().getItem(slot));
            if (value != null) result.add(new Held(slot, value.bundleId(), value.amount()));
        }
        return result;
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) clone[i] = source[i] == null ? null : source[i].clone();
        return clone;
    }

    private static Connection open(String path) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (var pragma = connection.createStatement()) {
            pragma.execute("PRAGMA busy_timeout=5000");
            pragma.execute("PRAGMA foreign_keys=ON");
        }
        return connection;
    }

    private static void ensureUnlimitedTable(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + UNLIMITED_TABLE + " (bundle_id TEXT PRIMARY KEY,amount INTEGER NOT NULL CHECK(amount>=1),state TEXT NOT NULL CHECK(state IN ('PENDING_DELIVERY','ACTIVE','RETIRED')),source_transaction_id TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        }
    }

    private static void ensureNoReview(Connection connection, UUID player) throws Exception {
        try (var query = connection.prepareStatement("SELECT 1 FROM medal_inventory_transactions WHERE player_uuid=? AND status='REVIEW_REQUIRED' LIMIT 1")) {
            query.setString(1, player.toString());
            try (var rows = query.executeQuery()) { if (rows.next()) throw new IllegalStateException("TOKEN_REVIEW_REQUIRED"); }
        }
    }

    private static void ensureNoEconomyReview(Connection connection, UUID player) throws Exception {
        try (var query = connection.prepareStatement("SELECT 1 FROM economy_transactions WHERE player_uuid=? AND status='REVIEW_REQUIRED' LIMIT 1")) {
            query.setString(1, player.toString());
            try (var rows = query.executeQuery()) { if (rows.next()) throw new IllegalStateException("VAULT_REVIEW_REQUIRED"); }
        }
    }

    private static void validateActive(Connection connection, Held item) throws Exception {
        String needle = "%" + item.bundleId() + "%";
        try (var review = connection.prepareStatement("SELECT 1 FROM medal_inventory_transactions WHERE status='REVIEW_REQUIRED' AND (before_bundle_json LIKE ? OR after_bundle_json LIKE ?) LIMIT 1")) {
            review.setString(1, needle); review.setString(2, needle);
            try (var rows = review.executeQuery()) { if (rows.next()) throw new IllegalStateException("TOKEN_REVIEW_REQUIRED"); }
        }
        if (matches(connection, UNLIMITED_TABLE, item) || matches(connection, "medal_tokens", item)) return;
        throw new IllegalStateException("INVALID_ITEM");
    }

    private static boolean matches(Connection connection, String table, Held item) throws Exception {
        try (var query = connection.prepareStatement("SELECT amount,state FROM " + table + " WHERE bundle_id=?")) {
            query.setString(1, item.bundleId().toString());
            try (var rows = query.executeQuery()) { return rows.next() && rows.getInt("amount") == item.amount() && "ACTIVE".equals(rows.getString("state")); }
        }
    }

    private static void retire(Connection connection, UUID id, long now) throws Exception {
        int changed = updateState(connection, UNLIMITED_TABLE, id, "ACTIVE", "RETIRED", now);
        if (changed == 0) changed = updateState(connection, "medal_tokens", id, "ACTIVE", "RETIRED", now);
        if (changed != 1) throw new IllegalStateException("INVALID_ITEM");
    }

    private static void insertUnlimited(Connection connection, UUID id, int amount, long now) throws Exception {
        try (var insert = connection.prepareStatement("INSERT INTO " + UNLIMITED_TABLE + "(bundle_id,amount,state,source_transaction_id,created_at,updated_at) VALUES(?,?,'ACTIVE',NULL,?,?)")) {
            insert.setString(1, id.toString()); insert.setInt(2, amount); insert.setLong(3, now); insert.setLong(4, now); insert.executeUpdate();
        }
    }

    private static int updateState(Connection connection, String table, UUID id, String from, String to, long now) throws Exception {
        try (var update = connection.prepareStatement("UPDATE " + table + " SET state=?,updated_at=? WHERE bundle_id=? AND state=?")) {
            update.setString(1, to); update.setLong(2, now); update.setString(3, id.toString()); update.setString(4, from); return update.executeUpdate();
        }
    }

    private static void setStateAny(Connection connection, UUID id, String state, long now) throws Exception {
        int changed;
        try (var update = connection.prepareStatement("UPDATE " + UNLIMITED_TABLE + " SET state=?,updated_at=? WHERE bundle_id=?")) {
            update.setString(1, state); update.setLong(2, now); update.setString(3, id.toString()); changed = update.executeUpdate();
        }
        if (changed == 0) {
            try (var update = connection.prepareStatement("UPDATE medal_tokens SET state=?,updated_at=? WHERE bundle_id=?")) {
                update.setString(1, state); update.setLong(2, now); update.setString(3, id.toString()); changed = update.executeUpdate();
            }
        }
        if (changed != 1) throw new IllegalStateException("INVALID_ITEM");
    }

    private static JsonObject bundleJson(UUID id, int amount, int slot) {
        JsonObject json = new JsonObject();
        json.addProperty("bundleId", id.toString()); json.addProperty("amount", amount); json.addProperty("slot", slot); return json;
    }

    private static String normalizeFailure(Throwable failure) {
        String message = failure.getMessage();
        if (message == null) return "DB_ERROR";
        if (Set.of("TOKEN_REVIEW_REQUIRED", "VAULT_REVIEW_REQUIRED", "INVALID_ITEM", "NOT_ENOUGH_MEDALS").contains(message)) return message;
        return "DB_ERROR";
    }
}
