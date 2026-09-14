package jp.pirijuggler.paper.machine;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.session.AdminSessions;
import jp.pirijuggler.paper.session.Session;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import java.lang.management.ManagementFactory;
import jp.pirijuggler.paper.game.*;
import jp.pirijuggler.paper.database.GameStore;
import jp.pirijuggler.paper.threading.PaperMainThread;
import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/** All live state and operation reservations are owned by the Paper main thread. */
public final class MachineService implements Listener, CommandExecutor {
    private static final NamespacedKey ITEM_TYPE = new NamespacedKey("piri", "item_type");
    private static final NamespacedKey ITEM_VERSION = new NamespacedKey("piri", "item_version");
    private final PiriJugglerPlugin plugin;
    private final AdminSessions admins = new AdminSessions();
    private final Set<UUID> pendingPlayers = new HashSet<>();
    private final Set<Integer> pendingMachines = new HashSet<>();
    private final Map<UUID, Runnable> deferredClose = new HashMap<>();
    private final Set<UUID> deferredDisconnect = new HashSet<>();
    private final long graceMs;
    private PiriDatabase database;
    private PiriDatabase.State state;
    private boolean stopped;
    private boolean expiring, simulating;
    private final RandomStreams random;
    private final RoleWeights weights;
    private final NormalGame games;
    private record Saved<T>(T value, PiriDatabase.State state) {}

    public MachineService(PiriJugglerPlugin plugin, Map<String, Object> config) {
        this.plugin = plugin;
        random=RandomStreams.production();weights=new RoleWeights(config);
        games=new NormalGame(weights,random,plugin.reels().solver(),new PaperMainThread(plugin));
        graceMs = ((Number) jp.pirijuggler.paper.database.StartupProfile.map(config.get("game")).get("disconnect_grace_seconds")).longValue() * 1000;
        long jvm = ManagementFactory.getRuntimeMXBean().getStartTime();
        long now = System.currentTimeMillis();
        plugin.executors().database(() -> {
            database = new PiriDatabase(plugin.getDataFolder().toPath().resolve("piri.db"));
            return database.open(jvm, now, config, random.eventAllocation(), plugin.getLogger()::warning);
        }, (loaded, error) -> {
            if (error != null) plugin.getLogger().log(Level.SEVERE, "Gameplay disabled: database initialization failed", error);
            else { state = loaded; plugin.getLogger().info("PIRI_DATABASE_READY schema=4 period=" + state.period() + " machines=" + state.machines().size()); }
        });
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("piri")).setExecutor(this);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }
    private void main() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Machine state requires Paper main thread"); }
    public boolean ready() { main(); return !stopped && state != null; }
    public PiriDatabase.State snapshot() { main(); return state; }
    private boolean busy(int id) { return pendingMachines.contains(id) || state.busy(id); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        main();
        if (!sender.isOp()) { tell(sender, "NOT_OP"); return true; }
        if (!ready()) { tell(sender, "DB_ERROR"); return true; }
        try {
            if (args.length==3 && args[0].equalsIgnoreCase("simulator")) {
                int setting=Integer.parseInt(args[1]);long count=Long.parseLong(args[2]);
                if(setting<1||setting>6||count<1||count>100_000_000L)throw new DomainException("INVALID_STATE");
                if(simulating)throw new DomainException("BUSY");
                simulating=true;var rng=random.runtimeSimulation();tell(sender,"SIMULATOR_STARTED");
                plugin.executors().simulator(()->Simulator.run(weights,setting,count,rng),(result,error)->{
                    try {if(!stopped){if(error!=null)failure(sender,error);else tell(sender,"PIRI_SIMULATOR "+new Gson().toJson(result));}}
                    finally {simulating=false;}
                });return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("give") && args.length <= 3) {
                Player target = args.length == 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player player ? player : null;
                if (target == null) throw new DomainException("PLAYER_REQUIRED");
                if (target.getInventory().firstEmpty() < 0) throw new DomainException("INVENTORY_FULL");
                target.getInventory().addItem(machineKey()); tell(sender, "KEY_GIVEN " + target.getName()); return true;
            }
            if (args.length < 2 || !args[0].equalsIgnoreCase("machine")) throw new DomainException("Usage: /piri machine create|redefine <id>|remove <id>|list|info <id>, /piri key give [player]");
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("list") && args.length == 2) {
                tell(sender, "MACHINES " + state.machines().stream().filter(m -> !m.deleted()).map(m -> Integer.toString(m.id())).toList()); return true;
            }
            if (action.equals("create") && args.length == 2) {
                Machine.Location target = target(sender);
                submit(sender, null, 0, () -> database.create(target, System.currentTimeMillis()), id -> tell(sender, "MACHINE_CREATED " + id)); return true;
            }
            if (args.length != 3) throw new DomainException("INVALID_STATE");
            int id = Integer.parseInt(args[2]); Machine machine = state.machine(id);
            if (machine == null) throw new DomainException("INVALID_STATE");
            switch (action) {
                case "info" -> tell(sender, "MACHINE_INFO id=" + id + " world=" + machine.location().worldName() + " xyz=" + machine.location().x() + "," + machine.location().y() + "," + machine.location().z()
                        + " setting=" + machine.setting() + " enabled=" + machine.enabled() + " autoSetting=" + machine.autoSetting() + " busy=" + busy(id));
                case "redefine" -> {
                    if (busy(id)) throw new DomainException("MACHINE_OCCUPIED");
                    Machine.Location target = target(sender);
                    submit(sender, null, id, () -> { database.redefine(id, target, System.currentTimeMillis()); return id; }, done -> tell(sender, "MACHINE_REDEFINED " + done));
                }
                case "remove" -> {
                    if (busy(id)) throw new DomainException("MACHINE_OCCUPIED");
                    submit(sender, null, id, () -> { database.remove(id, System.currentTimeMillis()); return id; }, done -> tell(sender, "MACHINE_REMOVED " + done));
                }
                default -> throw new DomainException("INVALID_STATE");
            }
        } catch (DomainException error) { tell(sender, error.getMessage()); }
        catch (IllegalArgumentException error) { tell(sender, "INVALID_STATE"); }
        return true;
    }
    private Machine.Location target(CommandSender sender) {
        if (!(sender instanceof Player player)) throw new DomainException("PLAYER_REQUIRED");
        var hit = player.rayTraceBlocks(5.0, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null || !Tag.BUTTONS.isTagged(hit.getHitBlock().getType())) throw new DomainException("BUTTON_REQUIRED");
        return location(hit.getHitBlock());
    }
    private static Machine.Location location(Block block) {
        String facing = block.getBlockData() instanceof Directional directional ? directional.getFacing().name() : "UP";
        return new Machine.Location(block.getWorld().getUID(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), facing);
    }
    public static ItemStack machineKey() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        var meta = key.getItemMeta(); meta.displayName(Component.text("Piri 台鍵"));
        meta.getPersistentDataContainer().set(ITEM_TYPE, PersistentDataType.STRING, "machine_key");
        meta.getPersistentDataContainer().set(ITEM_VERSION, PersistentDataType.INTEGER, 1); key.setItemMeta(meta); return key;
    }
    public static boolean validKey(ItemStack item) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return "machine_key".equals(pdc.get(ITEM_TYPE, PersistentDataType.STRING)) && Integer.valueOf(1).equals(pdc.get(ITEM_VERSION, PersistentDataType.INTEGER));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void interact(PlayerInteractEvent event) {
        main();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || state == null) return;
        var location = location(event.getClickedBlock());
        Machine machine = state.machines().stream().filter(m -> !m.deleted() && m.location().sameBlock(location)).findFirst().orElse(null);
        if (machine == null) return;
        boolean blockedByOther = event.useInteractedBlock() == Event.Result.DENY;
        event.setUseInteractedBlock(Event.Result.DENY); event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND || blockedByOther || !ready()) return;
        Player player = event.getPlayer(); UUID owner = player.getUniqueId();
        if (!plugin.canUseSlot(owner)) { tell(player, "Piri Juggler Client Mod 1.0.0 が必要です"); return; }
        if (player.isOp() && validKey(event.getItem())) {
            // Read access deliberately does not take the machine mutation reservation.
            plugin.executors().database(() -> database.adminState(machine.id()), (json, error) -> {
                if (stopped || !player.isOnline()) return;
                if (!player.isOp()) { error(player, "NOT_OP"); return; }
                if (error != null) { failure(player, error); return; }
                var session = admins.open(owner, machine.id(), System.currentTimeMillis());
                json.addProperty("adminSessionId", session.id().toString());
                json.addProperty("busy", busy(machine.id())); send(player, PacketType.ADMIN_STATE, json);
            }); return;
        }
        if (!machine.enabled()) { error(player, "MACHINE_DISABLED"); return; }
        Session session = state.session(owner);
        if (session != null && session.machine() != machine.id()) { tell(player, "RECOVERY_REQUIRED"); return; }
        if (pendingMachines.contains(machine.id()) || (state.busy(machine.id()) && (session == null || !session.ownsLock()))) {
            error(player, "MACHINE_OCCUPIED"); return;
        }
        submit(player, owner, machine.id(), () -> database.seat(owner, machine.id(), System.currentTimeMillis()), seated -> {
            send(player, PacketType.OPEN_MACHINE, seated.openPacket()); send(player, PacketType.PUBLIC_STATE, seated.publicState());
            games.resume(seated,System.nanoTime()).ifPresent(packet->send(player,packet));
        });
    }
    public void receive(Player player, Envelope envelope) {
        main(); if (!ready() || envelope.packetType() == PacketType.HELLO) return;
        try {
            JsonObject body = envelope.payload();
            if (!body.keySet().equals(Set.of("sessionId","machineId","clientSequence"))) throw new IllegalArgumentException();
            UUID id = UUID.fromString(body.get("sessionId").getAsString());
            int machine = body.get("machineId").getAsBigDecimal().intValueExact();
            long sequence = body.get("clientSequence").getAsBigDecimal().longValueExact();
            if(envelope.packetType()==PacketType.CLOSE_REQUEST)closeRequest(player,id,machine,sequence);
            else gameAction(player,id,machine,sequence,envelope.packetType());
        } catch (RuntimeException invalid) { error(player, "SESSION_MISMATCH"); }
    }
    private void gameAction(Player player,UUID id,int machine,long sequence,PacketType action) {
        Session session=state.session(player.getUniqueId());
        if(session==null||!session.id().equals(id)||session.machine()!=machine||session.lifecycle()!=Session.Lifecycle.ACTIVE){reject(player,sequence,"SESSION_MISMATCH");return;}
        if(sequence<=session.sequence()){reject(player,sequence,"SEQUENCE_OLD");return;}
        if(pendingPlayers.contains(player.getUniqueId())||pendingMachines.contains(machine)){reject(player,sequence,"BUSY");return;}
        try {
            var transition=games.plan(session,action,sequence,state.machine(machine).setting(),System.currentTimeMillis(),System.nanoTime(),player.getPing());
            submit(player,player.getUniqueId(),machine,()->new GameStore(database).commit(transition),saved->{
                for(var packet:games.committed(transition,System.nanoTime()))send(player,packet);
            });
        } catch(DomainException error){reject(player,sequence,error.getMessage());}
        catch(ArithmeticException overflow){reject(player,sequence,"INVALID_STATE");}
    }
    private void closeRequest(Player player, UUID id, int machine, long sequence) {
        UUID owner = player.getUniqueId();
        if (pendingPlayers.contains(owner)) {
            if (deferredClose.putIfAbsent(owner, () -> closeRequest(player, id, machine, sequence)) != null)
                reject(player, sequence, "BUSY");
            return;
        }
        Session session = state.session(owner);
        if (session == null || !session.id().equals(id) || session.machine() != machine) { reject(player, sequence, "SESSION_MISMATCH"); return; }
        if (sequence <= session.sequence()) { reject(player, sequence, "SEQUENCE_OLD"); return; }
        Session motion=games.capture(session,System.nanoTime());
        submit(player, owner, machine, () -> database.closeSession(owner, id, machine, sequence, System.currentTimeMillis(), graceMs, motion), reason -> {
            games.forget(session.id());
            JsonObject body = session.identity();
            if (reason.equals("SESSION_END")) { body.remove("machineId"); send(player, PacketType.SESSION_END, body); }
            else { body.addProperty("reason", reason); send(player, PacketType.SESSION_SUSPENDED, body); }
        });
    }
    public void disconnect(UUID player) {
        main(); admins.close(player);
        if (!ready()) return;
        if (pendingPlayers.contains(player)) { deferredDisconnect.add(player); return; }
        Session session = state.session(player); if (session == null) return;
        Session motion=games.capture(session,System.nanoTime());
        submit(null, player, session.machine(), () -> { database.disconnect(player, System.currentTimeMillis(), graceMs, motion); return null; }, unused -> games.forget(session.id()));
    }
    private <T> void submit(CommandSender sender, UUID player, int machine, Callable<T> operation, Consumer<T> success) {
        main();
        if ((player != null && pendingPlayers.contains(player)) || (machine != 0 && pendingMachines.contains(machine))) { if (sender != null) error(sender, "BUSY"); return; }
        if (player != null) pendingPlayers.add(player); if (machine != 0) pendingMachines.add(machine);
        plugin.executors().database(() -> new Saved<>(operation.call(), database.state()), (saved, error) -> {
            if (saved != null) state = saved.state;
            try { if (!stopped) { if (error != null) failure(sender, error); else success.accept(saved.value); } }
            finally {
                if (player != null) pendingPlayers.remove(player); if (machine != 0) pendingMachines.remove(machine);
                if (player != null && !stopped) {
                    Runnable close = deferredClose.remove(player); if (close != null) close.run();
                    if (deferredDisconnect.remove(player)) disconnect(player);
                }
            }
        });
    }
    private void tick() {
        main(); admins.expire(System.currentTimeMillis());
        if (!ready() || expiring || !pendingPlayers.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (state.sessions().stream().noneMatch(s -> s.lifecycle() == Session.Lifecycle.SUSPENDED_GRACE && s.number("lock_expires_at") <= now)) return;
        expiring = true;
        plugin.executors().database(() -> { database.expire(now); return database.state(); }, (saved, error) -> {
            expiring = false; if (saved != null) state = saved;
            if (error != null) failure(null, error);
        });
    }
    private void failure(CommandSender sender, Throwable failure) {
        String code = failure instanceof DomainException ? failure.getMessage() : "DB_ERROR";
        if (!(failure instanceof DomainException)) plugin.getLogger().log(Level.SEVERE, "Database operation failed; transaction rolled back", failure);
        if (sender != null) error(sender, code);
    }
    private static void tell(CommandSender sender, String message) { sender.sendMessage(Component.text(message)); }
    private void error(CommandSender sender, String code) {
        tell(sender, code);
        if (sender instanceof Player player) try { send(player, ErrorPackets.error(ErrorCode.valueOf(code))); } catch (IllegalArgumentException commandOnlyCode) { /* Command-only errors have no wire ID. */ }
    }
    private void reject(Player player, long sequence, String code) { send(player, ErrorPackets.rejected(sequence, ErrorCode.valueOf(code))); }
    private void send(Player player, PacketType type, JsonObject body) { send(player, new Envelope(Protocol.VERSION, type, body)); }
    private void send(Player player, Envelope packet) { if (player.isOnline() && !stopped) player.sendPluginMessage(plugin, Protocol.CHANNEL, EnvelopeCodec.encode(packet)); }
    public void shutdown() {
        main(); stopped = true; admins.clear();
        try { plugin.executors().databaseBarrier(() -> { if (database != null) database.shutdown(System.currentTimeMillis(), graceMs); return null; }).get(30, TimeUnit.SECONDS); }
        catch (Exception failure) { plugin.getLogger().log(Level.SEVERE, "Database shutdown flush failed", failure); }
    }
}
