package jp.pirijuggler.fabric.network;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.reel.ReelMotion;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Typed, public-only remote machine state used by Phase13 world rendering.
 * Mutations are package-private so only the validated remote registry can update it.
 */
public final class RemoteMachineViewState {
    private record StopMotion(double from, double target, int finalStop, long at, long durationNanos) {}

    private final int machineId;
    private UUID world;
    private String worldName;
    private String dimension;
    private int x, y, z;
    private String facing;
    private boolean enabled, occupied;
    private String gameState;
    private final int[] displayStops = new int[3];
    private final double[] startPhases = new double[3];
    private final StopMotion[] stops = new StopMotion[3];
    private int stoppedMask;
    private boolean lampOn, lampBlink;
    private long lampChangedAt;
    private long credit, pay, bonusCount;
    private String bonusMode;
    private boolean spinning;
    private UUID spinId;
    private ReelMotion.Profile profile = ReelMotion.Profile.NORMAL;
    private long spinAt;

    private RemoteMachineViewState(int machineId) {
        this.machineId = machineId;
    }

    static RemoteMachineViewState fromSnapshot(int machineId, JsonObject body, long now) {
        RemoteMachineViewState state = new RemoteMachineViewState(machineId);
        state.world = UUID.fromString(body.get("world").getAsString());
        state.worldName = body.get("worldName").getAsString();
        state.dimension = body.has("dimension") ? body.get("dimension").getAsString() : "";
        state.x = body.get("x").getAsInt();
        state.y = body.get("y").getAsInt();
        state.z = body.get("z").getAsInt();
        state.facing = body.get("facing").getAsString();
        state.enabled = body.get("enabled").getAsBoolean();
        state.occupied = body.get("occupied").getAsBoolean();
        state.gameState = body.get("gameState").getAsString();
        JsonObject ds = body.getAsJsonObject("displayStops");
        state.displayStops[0] = ds.get("left").getAsInt();
        state.displayStops[1] = ds.get("center").getAsInt();
        state.displayStops[2] = ds.get("right").getAsInt();
        state.stoppedMask = body.get("stoppedMask").getAsInt();
        state.lampOn = body.get("lampOn").getAsBoolean();
        state.lampBlink = false;
        state.lampChangedAt = now;
        state.credit = body.get("credit").getAsLong();
        state.pay = body.get("pay").getAsLong();
        state.bonusCount = body.get("bonusCount").getAsLong();
        state.bonusMode = body.get("bonusMode").getAsString();
        state.spinning = body.get("spinning").getAsBoolean();
        if (state.spinning) {
            state.spinId = UUID.fromString(body.get("spinId").getAsString());
            state.profile = ReelMotion.Profile.valueOf(body.get("animation").getAsString());
            JsonObject phase = body.getAsJsonObject("startPhase");
            state.startPhases[0] = phase.get("left").getAsDouble();
            state.startPhases[1] = phase.get("center").getAsDouble();
            state.startPhases[2] = phase.get("right").getAsDouble();
            state.spinAt = now;
            for (int reel = 0; reel < 3; reel++) {
                if ((state.stoppedMask & (1 << reel)) != 0) {
                    state.stops[reel] = new StopMotion(state.displayStops[reel], state.displayStops[reel],
                            state.displayStops[reel], now, 0);
                }
            }
        } else {
            for (int reel = 0; reel < 3; reel++) state.startPhases[reel] = state.displayStops[reel];
        }
        return state;
    }

    void applySpin(JsonObject body, long now) {
        spinId = UUID.fromString(body.get("spinId").getAsString());
        profile = ReelMotion.Profile.valueOf(body.get("animation").getAsString());
        JsonObject phase = body.getAsJsonObject("startPhase");
        startPhases[0] = phase.get("left").getAsDouble();
        startPhases[1] = phase.get("center").getAsDouble();
        startPhases[2] = phase.get("right").getAsDouble();
        stoppedMask = body.has("stoppedMask") ? body.get("stoppedMask").getAsInt() : 0;
        if (body.has("displayStops") && body.get("displayStops").isJsonObject()) {
            JsonObject ds = body.getAsJsonObject("displayStops");
            displayStops[0] = ds.get("left").getAsInt();
            displayStops[1] = ds.get("center").getAsInt();
            displayStops[2] = ds.get("right").getAsInt();
        }
        for (int reel = 0; reel < 3; reel++) {
            stops[reel] = (stoppedMask & (1 << reel)) != 0
                    ? new StopMotion(displayStops[reel], displayStops[reel], displayStops[reel], now, 0)
                    : null;
        }
        spinAt = now;
        spinning = stoppedMask != 7;
    }

    void applyStop(JsonObject body, long now) {
        UUID packetSpin = UUID.fromString(body.get("spinId").getAsString());
        if (spinId == null || !spinId.equals(packetSpin)) return;
        int reel = switch (body.get("reel").getAsString()) {
            case "LEFT" -> 0;
            case "CENTER" -> 1;
            case "RIGHT" -> 2;
            default -> throw new IllegalArgumentException("reel");
        };
        int target = body.get("stopIndex").getAsInt();
        int requestedMs = body.get("durationMs").getAsInt();
        double from = phase(reel, now);
        double endpoint = ReelMotion.normalStopEndpoint(from, target);
        int visualMs = ReelMotion.visualDurationMs(from, endpoint, requestedMs);
        stops[reel] = new StopMotion(from, endpoint, target, now, visualMs * 1_000_000L);
        displayStops[reel] = target;
        stoppedMask |= 1 << reel;
        if (stoppedMask == 7) spinning = false;
    }

    void applyNotice(JsonObject body, long now) {
        if (body.has("spinId") && spinId != null && !spinId.toString().equals(body.get("spinId").getAsString())) return;
        lampOn = "ON".equals(body.get("lamp").getAsString());
        lampBlink = "FAST_BLINK_1S".equals(body.get("pattern").getAsString());
        lampChangedAt = now;
    }

    void applyBonus(JsonObject body) {
        boolean active = body.get("active").getAsBoolean();
        if (active) {
            String type = body.get("bonusType").getAsString();
            if (!Set.of("BIG", "REG").contains(type)) throw new IllegalArgumentException("bonusType");
            bonusMode = type;
            if (body.has("count")) bonusCount = body.get("count").getAsLong();
        } else {
            bonusMode = "NONE";
            if (body.has("finalCount")) bonusCount = body.get("finalCount").getAsLong();
        }
    }

    public double phase(int reel, long now) {
        if (reel < 0 || reel > 2) throw new IllegalArgumentException("reel");
        StopMotion stop = stops[reel];
        if (stop != null) {
            if (stop.durationNanos == 0) return stop.finalStop;
            double t = Math.min(1.0, Math.max(0.0, (now - stop.at) / (double) stop.durationNanos));
            if (t >= 1.0) return stop.finalStop;
            return ReelMotion.wrap(stop.from + (stop.target - stop.from) * t);
        }
        if (!spinning) return displayStops[reel];
        return ReelMotion.wrap(startPhases[reel] + ReelMotion.delta(profile, (now - spinAt) / 1_000_000_000.0));
    }

    public boolean lampVisible(long now) {
        if (!lampOn) return false;
        long elapsed = Math.max(0, now - lampChangedAt);
        return !lampBlink || elapsed >= 1_000_000_000L || (elapsed / 100_000_000L) % 2 == 0;
    }

    public int machineId() { return machineId; }
    public UUID world() { return world; }
    public String worldName() { return worldName; }
    public String dimension() { return dimension; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String facing() { return facing; }
    public boolean enabled() { return enabled; }
    public boolean occupied() { return occupied; }
    public String gameState() { return gameState; }
    public int stoppedMask() { return stoppedMask; }
    public long credit() { return credit; }
    public long pay() { return pay; }
    public long bonusCount() { return bonusCount; }
    public String bonusMode() { return bonusMode; }
    public boolean spinning() { return spinning; }
    public UUID spinId() { return spinId; }

    public int displayStop(int reel) {
        if (reel < 0 || reel > 2) throw new IllegalArgumentException("reel");
        return displayStops[reel];
    }

    @Override public String toString() {
        return "RemoteMachineViewState{" + machineId + " " + worldName + " "
                + x + "," + y + "," + z + " " + facing.toUpperCase(Locale.ROOT) + "}";
    }
}
