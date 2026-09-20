package jp.pirijuggler.fabric.network;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.reel.ReelMotion;

import java.util.*;
import java.util.function.LongSupplier;

/**
 * Client-side cache of public remote-machine state.
 * Phase12 stores/validates state only; Phase13 renders this cache.
 */
public final class RemoteMachineRegistry {
    private static final Set<PacketType> TYPES = EnumSet.of(
            PacketType.REMOTE_MACHINE_SNAPSHOT,
            PacketType.REMOTE_MACHINE_SPIN,
            PacketType.REMOTE_MACHINE_STOP,
            PacketType.REMOTE_MACHINE_NOTICE,
            PacketType.REMOTE_MACHINE_BONUS,
            PacketType.REMOTE_MACHINE_REMOVE,
            PacketType.REMOTE_MACHINE_SOUND
    );
    private final Map<Integer, JsonObject> machines = new HashMap<>();
    private final Map<Integer, RemoteMachineViewState> views = new HashMap<>();
    private final LongSupplier time;

    public RemoteMachineRegistry() {
        this(System::nanoTime);
    }

    RemoteMachineRegistry(LongSupplier time) {
        this.time = Objects.requireNonNull(time);
    }

    public static boolean isRemote(PacketType type) {
        return TYPES.contains(type);
    }

    /**
     * Malformed remote traffic is isolated to the referenced machine and never
     * invalidates the gameplay handshake/session.
     */
    public void receive(Envelope envelope) {
        if (!isRemote(envelope.packetType())) return;
        JsonObject body = envelope.payload();
        Integer machineId = safeMachineId(body);
        try {
            if (machineId == null || machineId <= 0) throw new IllegalArgumentException("machineId");
            switch (envelope.packetType()) {
                case REMOTE_MACHINE_SNAPSHOT -> {
                    applySnapshot(machineId, body);
                    views.put(machineId, RemoteMachineViewState.fromSnapshot(machineId, body, time.getAsLong()));
                }
                case REMOTE_MACHINE_SPIN -> {
                    mutate(machineId, current -> {
                    requireString(body, "spinId");
                    UUID.fromString(body.get("spinId").getAsString());
                    requireString(body, "animation");
                    ReelMotion.Profile.valueOf(body.get("animation").getAsString());
                    JsonObject phase = requireObject(body, "startPhase");
                    requireNumber(phase, "left"); requireNumber(phase, "center"); requireNumber(phase, "right");
                    current.addProperty("spinning", true);
                    current.addProperty("spinId", body.get("spinId").getAsString());
                    current.addProperty("animation", body.get("animation").getAsString());
                    current.add("startPhase", phase.deepCopy());
                    int stoppedMask = body.has("stoppedMask") && body.get("stoppedMask").isJsonPrimitive()
                            && body.getAsJsonPrimitive("stoppedMask").isNumber()
                            ? body.get("stoppedMask").getAsInt() : 0;
                    if (stoppedMask < 0 || stoppedMask > 7) throw new IllegalArgumentException("stoppedMask");
                    current.addProperty("stoppedMask", stoppedMask);
                    if (body.has("displayStops") && body.get("displayStops").isJsonObject()) {
                        JsonObject displayStops = body.getAsJsonObject("displayStops");
                        validateStop(displayStops, "left"); validateStop(displayStops, "center"); validateStop(displayStops, "right");
                        current.add("displayStops", displayStops.deepCopy());
                    }
                    });
                    RemoteMachineViewState view = views.get(machineId);
                    if (view != null) view.applySpin(body, time.getAsLong());
                }
                case REMOTE_MACHINE_STOP -> {
                    mutate(machineId, current -> {
                    requireString(body, "spinId");
                    UUID.fromString(body.get("spinId").getAsString());
                    requireString(body, "reel");
                    requireNumber(body, "stopIndex"); requireNumber(body, "durationMs");
                    String reel = body.get("reel").getAsString();
                    if (!Set.of("LEFT","CENTER","RIGHT").contains(reel)) throw new IllegalArgumentException("reel");
                    int stop = body.get("stopIndex").getAsInt();
                    if (stop < 0 || stop >= 21) throw new IllegalArgumentException("stopIndex");
                    if (body.get("durationMs").getAsInt() < 0) throw new IllegalArgumentException("durationMs");
                    // Validate the packet completely before treating it as a harmless stale event.
                    if (!current.has("spinId") || !body.get("spinId").getAsString().equals(current.get("spinId").getAsString()))
                        return; // valid but stale stop from an older spin
                    JsonObject stops = requireObject(current, "displayStops");
                    String key = reel.toLowerCase(Locale.ROOT);
                    stops.addProperty(key, stop);
                    int bit = switch (reel) { case "LEFT" -> 1; case "CENTER" -> 2; case "RIGHT" -> 4; default -> 0; };
                    int mask = current.has("stoppedMask") ? current.get("stoppedMask").getAsInt() : 0;
                    current.addProperty("stoppedMask", mask | bit);
                    current.addProperty("lastStopReel", reel);
                    current.addProperty("lastStopDurationMs", body.get("durationMs").getAsInt());
                    if ((mask | bit) == 7) current.addProperty("spinning", false);
                    });
                    RemoteMachineViewState view = views.get(machineId);
                    if (view != null) view.applyStop(body, time.getAsLong());
                }
                case REMOTE_MACHINE_NOTICE -> {
                    mutate(machineId, current -> {
                    requireString(body, "lamp"); requireString(body, "pattern");
                    if (!Set.of("ON","OFF").contains(body.get("lamp").getAsString())) throw new IllegalArgumentException("lamp");
                    if (!Set.of("STEADY","FAST_BLINK_1S").contains(body.get("pattern").getAsString())) throw new IllegalArgumentException("pattern");
                    if (body.has("spinId") && current.has("spinId")
                            && !body.get("spinId").getAsString().equals(current.get("spinId").getAsString())) return;
                    current.addProperty("lampOn", "ON".equals(body.get("lamp").getAsString()));
                    current.addProperty("lampPattern", body.get("pattern").getAsString());
                    });
                    RemoteMachineViewState view = views.get(machineId);
                    if (view != null) view.applyNotice(body, time.getAsLong());
                }
                case REMOTE_MACHINE_BONUS -> {
                    mutate(machineId, current -> {
                    requireBoolean(body, "active");
                    boolean active = body.get("active").getAsBoolean();
                    if (active) {
                        requireString(body, "bonusType");
                        String type = body.get("bonusType").getAsString();
                        if (!Set.of("BIG","REG").contains(type)) throw new IllegalArgumentException("bonusType");
                        current.addProperty("bonusMode", type);
                        if (body.has("count")) {
                            requireNumber(body, "count");
                            long count = body.get("count").getAsLong();
                            if (count < 0) throw new IllegalArgumentException("count");
                            current.addProperty("bonusCount", count);
                        }
                    } else {
                        current.addProperty("bonusMode", "NONE");
                        if (body.has("finalCount")) current.addProperty("bonusCount", body.get("finalCount").getAsLong());
                    }
                    });
                    RemoteMachineViewState view = views.get(machineId);
                    if (view != null) view.applyBonus(body);
                }
                case REMOTE_MACHINE_REMOVE -> {
                    machines.remove(machineId);
                    views.remove(machineId);
                }
                case REMOTE_MACHINE_SOUND -> {
                    // Reserved for Phase14. Validate the identity only and do not play audio yet.
                }
                default -> { }
            }
        } catch (RuntimeException malformed) {
            if (machineId != null) {
                machines.remove(machineId);
                views.remove(machineId);
            }
        }
    }

    public JsonObject machine(int machineId) {
        JsonObject value = machines.get(machineId);
        return value == null ? null : value.deepCopy();
    }

    public Map<Integer, JsonObject> snapshot() {
        Map<Integer, JsonObject> copy = new LinkedHashMap<>();
        machines.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> copy.put(entry.getKey(), entry.getValue().deepCopy()));
        return Collections.unmodifiableMap(copy);
    }

    public List<RemoteMachineViewState> viewSnapshot() {
        return List.copyOf(views.values());
    }

    public RemoteMachineViewState view(int machineId) {
        return views.get(machineId);
    }

    public void reset() {
        machines.clear();
        views.clear();
    }

    private void applySnapshot(int machineId, JsonObject body) {
        requireString(body, "world"); UUID.fromString(body.get("world").getAsString());
        requireString(body, "worldName");
        requireNumber(body, "x"); requireNumber(body, "y"); requireNumber(body, "z");
        requireString(body, "facing");
        if (!Set.of("NORTH","SOUTH","EAST","WEST","UP","DOWN").contains(body.get("facing").getAsString()))
            throw new IllegalArgumentException("facing");
        if (!body.has("machineType")) body.addProperty("machineType", "JUGGLER");
        requireString(body, "machineType");
        if (!Set.of("JUGGLER","OKIDOKI","GOD","DISC").contains(body.get("machineType").getAsString()))
            throw new IllegalArgumentException("machineType");
        requireBoolean(body, "enabled"); requireBoolean(body, "occupied");
        requireString(body, "gameState");
        JsonObject stops = requireObject(body, "displayStops");
        validateStop(stops, "left"); validateStop(stops, "center"); validateStop(stops, "right");
        requireNumber(body, "stoppedMask");
        int stoppedMask = body.get("stoppedMask").getAsInt();
        if (stoppedMask < 0 || stoppedMask > 7) throw new IllegalArgumentException("stoppedMask");
        requireBoolean(body, "lampOn");
        requireNumber(body, "credit"); requireNumber(body, "pay"); requireNumber(body, "bonusCount");
        requireNumber(body, "totalGames"); requireNumber(body, "bigCount"); requireNumber(body, "regCount");
        if (body.get("totalGames").getAsLong() < 0 || body.get("bigCount").getAsLong() < 0 || body.get("regCount").getAsLong() < 0)
            throw new IllegalArgumentException("dataLamp");
        requireString(body, "bonusMode");
        if (!Set.of("NONE","BIG","REG").contains(body.get("bonusMode").getAsString()))
            throw new IllegalArgumentException("bonusMode");
        requireBoolean(body, "spinning");
        if (body.get("spinning").getAsBoolean()) {
            requireString(body, "spinId"); UUID.fromString(body.get("spinId").getAsString());
            requireString(body, "animation");
            ReelMotion.Profile.valueOf(body.get("animation").getAsString());
            JsonObject phase = requireObject(body, "startPhase");
            requireNumber(phase, "left"); requireNumber(phase, "center"); requireNumber(phase, "right");
        }
        machines.put(machineId, body.deepCopy());
    }

    private void mutate(int machineId, java.util.function.Consumer<JsonObject> mutation) {
        JsonObject current = machines.get(machineId);
        if (current == null) return; // Wait for the next authoritative snapshot.
        mutation.accept(current);
    }

    private static Integer safeMachineId(JsonObject body) {
        try {
            return body.has("machineId") && body.get("machineId").isJsonPrimitive()
                    && body.getAsJsonPrimitive("machineId").isNumber()
                    ? body.get("machineId").getAsBigDecimal().intValueExact() : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static void validateStop(JsonObject object, String name) {
        requireNumber(object, name);
        int value = object.get(name).getAsInt();
        if (value < 0 || value >= 21) throw new IllegalArgumentException(name);
    }

    private static JsonObject requireObject(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonObject()) throw new IllegalArgumentException(name);
        return object.getAsJsonObject(name);
    }
    private static void requireString(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive() || !object.getAsJsonPrimitive(name).isString())
            throw new IllegalArgumentException(name);
    }
    private static void requireNumber(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive() || !object.getAsJsonPrimitive(name).isNumber())
            throw new IllegalArgumentException(name);
    }
    private static void requireBoolean(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive() || !object.getAsJsonPrimitive(name).isBoolean())
            throw new IllegalArgumentException(name);
    }
}
