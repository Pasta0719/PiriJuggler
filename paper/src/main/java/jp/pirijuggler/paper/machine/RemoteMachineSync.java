package jp.pirijuggler.paper.machine;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.EnvelopeCodec;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.protocol.Protocol;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.session.Session;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Main-thread-only public spectator synchronization.
 * Paper remains authoritative; this class never exposes settings, internal roles,
 * premium choices, stop hints, RNG state, Vault data, or held medals.
 */
public final class RemoteMachineSync {
    public static final double VISUAL_RADIUS = RemoteInterestPolicy.ENTER_RADIUS;

    private final PiriJugglerPlugin plugin;
    private final Supplier<PiriDatabase.State> stateSupplier;
    private final Predicate<UUID> compatible;
    private final BiFunction<Session, Long, Session> capture;
    private record DataLamp(long totalGames,long bigCount,long regCount) {}
    private final Map<Integer, DataLamp> dataLamp = new HashMap<>();
    private final Map<UUID, Set<Integer>> interests = new HashMap<>();

    public RemoteMachineSync(PiriJugglerPlugin plugin,
                             Supplier<PiriDatabase.State> stateSupplier,
                             Predicate<UUID> compatible,
                             BiFunction<Session, Long, Session> capture) {
        this.plugin = Objects.requireNonNull(plugin);
        this.stateSupplier = Objects.requireNonNull(stateSupplier);
        this.compatible = Objects.requireNonNull(compatible);
        this.capture = Objects.requireNonNull(capture);
    }

    public void viewerReady(Player player) {
        requireMain();
        refreshViewer(player, true);
    }

    public void viewerGone(UUID player) {
        requireMain();
        interests.remove(player);
    }

    public void worldChanged(Player player) {
        requireMain();
        Set<Integer> previous = interests.remove(player.getUniqueId());
        if (previous != null) for (int id : previous) sendRemove(player, id);
        refreshViewer(player, true);
    }

    /** One global 10-tick interest refresh. No per-machine repeating task. */
    public void refreshOnlineViewers() {
        requireMain();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (compatible.test(player.getUniqueId())) refreshViewer(player, false);
            else interests.remove(player.getUniqueId());
        }
        interests.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    /** Create/redefine/remove/enable changes are reflected immediately. */
    public void machineChanged(int machineId) {
        requireMain();
        PiriDatabase.State state = stateSupplier.get();
        Machine machine = state == null ? null : state.machine(machineId);
        if (machine == null) dataLamp.remove(machineId);
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID viewer = player.getUniqueId();
            if (!compatible.test(viewer)) continue;
            Set<Integer> current = interests.computeIfAbsent(viewer, ignored -> new HashSet<>());
            boolean had = current.contains(machineId);
            boolean wants = machine != null && inRange(player, machine);
            if (had && !wants) {
                current.remove(machineId);
                sendRemove(player, machineId);
            } else if (!had && wants) {
                current.add(machineId);
                sendSnapshot(player, machine);
            } else if (had) {
                sendSnapshot(player, machine);
            }
        }
    }

    public void updateDataLamp(JsonObject snapshot) {
        requireMain();
        if (snapshot == null || !snapshot.has("machineId")) return;
        int machineId = snapshot.get("machineId").getAsInt();
        long totalGames = nonNegative(snapshot, "totalGames");
        long bigCount = nonNegative(snapshot, "bigCount");
        long regCount = nonNegative(snapshot, "regCount");
        DataLamp next = new DataLamp(totalGames, bigCount, regCount);
        DataLamp previous = dataLamp.put(machineId, next);
        if (!next.equals(previous)) broadcastSnapshot(machineId);
    }

    /** Sends a fresh public state to current interested spectators only. */
    public void broadcastSnapshot(int machineId) {
        requireMain();
        PiriDatabase.State state = stateSupplier.get();
        Machine machine = state == null ? null : state.machine(machineId);
        if (machine == null) {
            machineChanged(machineId);
            return;
        }
        forEachViewer(machineId, viewer -> sendSnapshot(viewer, machine));
    }

    /**
     * Translate only already-public owner packets. Hidden fields are deliberately
     * omitted rather than forwarding the original envelope.
     */
    public void publishOwnerPacket(int machineId, Envelope ownerPacket) {
        requireMain();
        JsonObject source = ownerPacket.payload();
        switch (ownerPacket.packetType()) {
            case PUBLIC_STATE -> broadcastSnapshot(machineId);
            case SPIN_START -> {
                JsonObject body = base(machineId);
                copyString(source, body, "spinId");
                copyString(source, body, "animation");
                copyObject(source, body, "startPhase");
                PiriDatabase.State state = stateSupplier.get();
                Session current = state == null ? null : state.sessions().stream()
                        .filter(s -> s.machine() == machineId && s.lifecycle() == Session.Lifecycle.ACTIVE)
                        .findFirst().orElse(null);
                if (current != null) {
                    JsonObject publicState = current.publicState();
                    body.addProperty("stoppedMask", publicState.get("stoppedMask").getAsInt());
                    body.add("displayStops", publicState.getAsJsonObject("displayStops").deepCopy());
                } else {
                    body.addProperty("stoppedMask", 0);
                }
                broadcast(machineId, PacketType.REMOTE_MACHINE_SPIN, body);
            }
            case REEL_STOP -> {
                JsonObject body = base(machineId);
                copyString(source, body, "spinId");
                copyString(source, body, "reel");
                copyInt(source, body, "stopIndex");
                copyInt(source, body, "durationMs");
                broadcast(machineId, PacketType.REMOTE_MACHINE_STOP, body);
            }
            case NOTICE -> {
                JsonObject body = base(machineId);
                copyString(source, body, "spinId");
                copyString(source, body, "lamp");
                copyString(source, body, "pattern");
                broadcast(machineId, PacketType.REMOTE_MACHINE_NOTICE, body);
            }
            case BONUS_START -> {
                JsonObject body = base(machineId);
                body.addProperty("active", true);
                copyString(source, body, "bonusType");
                if (source.has("count")) copyInt(source, body, "count");
                broadcast(machineId, PacketType.REMOTE_MACHINE_BONUS, body);
            }
            case BONUS_END -> {
                JsonObject body = base(machineId);
                body.addProperty("active", false);
                copyString(source, body, "bonusType");
                if (source.has("finalCount")) copyInt(source, body, "finalCount");
                broadcast(machineId, PacketType.REMOTE_MACHINE_BONUS, body);
            }
            default -> { }
        }
    }

    public void clear() {
        requireMain();
        interests.clear();
    }

    private void refreshViewer(Player player, boolean forceSnapshot) {
        PiriDatabase.State state = stateSupplier.get();
        if (state == null || !player.isOnline() || !compatible.test(player.getUniqueId())) {
            interests.remove(player.getUniqueId());
            return;
        }
        Set<Integer> current = interests.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
        Set<Integer> desired = new HashSet<>();
        for (Machine machine : state.machines()) {
            if (!machine.deleted() && inRange(player, machine, current.contains(machine.id()))) desired.add(machine.id());
        }
        for (int id : new HashSet<>(current)) {
            if (!desired.contains(id)) {
                current.remove(id);
                sendRemove(player, id);
            }
        }
        for (int id : desired) {
            Machine machine = state.machine(id);
            if (machine == null) continue;
            if (current.add(id) || forceSnapshot) sendSnapshot(player, machine);
        }
    }

    private boolean inRange(Player player, Machine machine) {
        return inRange(player, machine, false);
    }

    private boolean inRange(Player player, Machine machine, boolean currentlyInterested) {
        if (!player.getWorld().getUID().equals(machine.location().world())) return false;
        double dx = player.getLocation().getX() - (machine.location().x() + 0.5);
        double dy = player.getLocation().getY() - (machine.location().y() + 0.5);
        double dz = player.getLocation().getZ() - (machine.location().z() + 0.5);
        return RemoteInterestPolicy.contains(dx * dx + dy * dy + dz * dz, currentlyInterested);
    }

    private void sendSnapshot(Player viewer, Machine machine) {
        PiriDatabase.State state = stateSupplier.get();
        if (state == null || machine.deleted() || !viewer.isOnline()) return;

        Session session = state.sessions().stream()
                .filter(s -> s.machine() == machine.id() && s.ownsLock())
                .findFirst().orElse(null);

        JsonObject body = placement(machine);
        DataLamp stats = dataLamp.getOrDefault(machine.id(), new DataLamp(0, 0, 0));
        body.addProperty("totalGames", stats.totalGames());
        body.addProperty("bigCount", stats.bigCount());
        body.addProperty("regCount", stats.regCount());
        body.addProperty("enabled", machine.enabled());
        body.addProperty("occupied", state.busy(machine.id()));

        if (session == null) {
            // No lock-owning session remains: render the machine row's final authoritative stops.
            body.addProperty("gameState", "IDLE");
            body.add("displayStops", stops(machine.left(), machine.center(), machine.right()));
            body.addProperty("stoppedMask", 7);
            body.addProperty("lampOn", false);
            body.addProperty("credit", 0);
            body.addProperty("pay", 0);
            body.addProperty("bonusCount", 0);
            body.addProperty("bonusMode", "NONE");
            body.addProperty("spinning", false);
        } else {
            // ACTIVE and SUSPENDED_GRACE both retain the public reel state. ESC only closes
            // the owner's UI; it must not visually stop a reel that is still spinning.
            JsonObject publicState = session.publicState();
            String gameState = publicState.get("gameState").getAsString();
            body.addProperty("gameState", gameState);
            body.add("displayStops", publicState.getAsJsonObject("displayStops").deepCopy());
            body.addProperty("stoppedMask", publicState.get("stoppedMask").getAsInt());
            body.addProperty("lampOn", publicState.get("lampOn").getAsBoolean());
            body.addProperty("credit", publicState.get("credit").getAsLong());
            body.addProperty("pay", publicState.get("pay").getAsLong());
            body.addProperty("bonusCount", publicState.get("bonusCount").getAsLong());
            body.addProperty("bonusMode", publicBonusMode(gameState));

            boolean spinning = gameState.endsWith("_SPINNING");
            body.addProperty("spinning", spinning);
            if (spinning) {
                Session sampled;
                try { sampled = capture.apply(session, System.nanoTime()); }
                catch (RuntimeException ignored) { sampled = session; }
                String spinId = sampled.text("spin_id");
                if (spinId != null) body.addProperty("spinId", spinId);
                body.addProperty("animation", "RESUME_NORMAL");
                JsonObject phase = new JsonObject();
                phase.addProperty("left", number(sampled, "phase_left", machine.left()));
                phase.addProperty("center", number(sampled, "phase_center", machine.center()));
                phase.addProperty("right", number(sampled, "phase_right", machine.right()));
                body.add("startPhase", phase);
            }
        }
        send(viewer, PacketType.REMOTE_MACHINE_SNAPSHOT, body);
    }

    private static long nonNegative(JsonObject body, String key) {
        if (!body.has(key) || !body.get(key).isJsonPrimitive() || !body.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException(key);
        long value = body.get(key).getAsLong();
        if (value < 0) throw new IllegalArgumentException(key);
        return value;
    }

    private static double number(Session session, String key, double fallback) {
        Object value = session.snapshot().get(key);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static String publicBonusMode(String gameState) {
        if (gameState.startsWith("BIG_")) return "BIG";
        if (gameState.startsWith("REG_")) return "REG";
        return "NONE";
    }

    private static JsonObject placement(Machine machine) {
        JsonObject body = base(machine.id());
        body.addProperty("world", machine.location().world().toString());
        body.addProperty("worldName", machine.location().worldName());
        org.bukkit.World machineWorld = Bukkit.getWorld(machine.location().world());
        if (machineWorld != null) body.addProperty("dimension", machineWorld.getKey().toString());
        body.addProperty("x", machine.location().x());
        body.addProperty("y", machine.location().y());
        body.addProperty("z", machine.location().z());
        body.addProperty("facing", machine.location().facing());
        return body;
    }

    private static JsonObject stops(int left, int center, int right) {
        JsonObject result = new JsonObject();
        result.addProperty("left", left);
        result.addProperty("center", center);
        result.addProperty("right", right);
        return result;
    }

    private void broadcast(int machineId, PacketType type, JsonObject body) {
        forEachViewer(machineId, viewer -> send(viewer, type, body));
    }

    private void forEachViewer(int machineId, java.util.function.Consumer<Player> action) {
        PiriDatabase.State state = stateSupplier.get();
        for (var entry : interests.entrySet()) {
            if (!entry.getValue().contains(machineId)) continue;
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer == null || !viewer.isOnline() || !compatible.test(entry.getKey())) continue;
            Session own = state == null ? null : state.session(entry.getKey());
            if (own != null && own.machine() == machineId && own.lifecycle() == Session.Lifecycle.ACTIVE) continue;
            action.accept(viewer);
        }
    }

    private void sendRemove(Player viewer, int machineId) {
        send(viewer, PacketType.REMOTE_MACHINE_REMOVE, base(machineId));
    }

    private void send(Player viewer, PacketType type, JsonObject body) {
        if (!viewer.isOnline()) return;
        viewer.sendPluginMessage(plugin, Protocol.CHANNEL,
                EnvelopeCodec.encode(new Envelope(Protocol.VERSION, type, body)));
    }

    private static JsonObject base(int machineId) {
        JsonObject body = new JsonObject();
        body.addProperty("machineId", machineId);
        return body;
    }

    private static void copyString(JsonObject from, JsonObject to, String name) {
        if (from.has(name) && from.get(name).isJsonPrimitive() && from.getAsJsonPrimitive(name).isString())
            to.addProperty(name, from.get(name).getAsString());
    }

    private static void copyInt(JsonObject from, JsonObject to, String name) {
        if (from.has(name) && from.get(name).isJsonPrimitive() && from.getAsJsonPrimitive(name).isNumber())
            to.addProperty(name, from.get(name).getAsInt());
    }

    private static void copyObject(JsonObject from, JsonObject to, String name) {
        if (from.has(name) && from.get(name).isJsonObject()) to.add(name, from.getAsJsonObject(name).deepCopy());
    }

    private static void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Remote machine sync requires Paper main thread");
    }
}
