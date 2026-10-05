package jp.pirijuggler.paper.game.pachinko;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Objects;

/**
 * Durable server-owned state for pachinko.
 *
 * Visual clients may reconstruct motion from ballSequenceId/presentation fields,
 * but this state is authoritative for economy and progression.
 */
public record PachinkoRuntime(
        Mode mode,
        long ballsHeld,
        long ballsLoaned,
        long totalFired,
        long totalStarts,
        long ballSequenceId,
        Presentation presentation,
        boolean initialHitCommitted,
        InitialOutcome initialOutcome,
        boolean rushActive,
        long rushWins,
        RightOutcome rightOutcome,
        long currentPayout,
        long cumulativePayout,
        PachinkoStatistics statistics,
        long lastActivity
) {
    public enum Mode { NORMAL, INITIAL_PAYOUT, RUSH, RIGHT_PAYOUT }
    public enum Presentation { IDLE, LEFT_KURUN, RIGHT_KURUN, PAYOUT }
    public enum InitialOutcome { NONE, NORMAL_450, RUSH_1500 }
    public enum RightOutcome { NONE, OUT, WIN_1500, WIN_3000 }

    public PachinkoRuntime {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(presentation);
        Objects.requireNonNull(initialOutcome);
        Objects.requireNonNull(rightOutcome);
        Objects.requireNonNull(statistics);
        if (ballsHeld < 0 || ballsLoaned < 0 || totalFired < 0 || totalStarts < 0 ||
                ballSequenceId < 0 || rushWins < 0 || currentPayout < 0 ||
                cumulativePayout < 0 || lastActivity < 0) {
            throw new IllegalArgumentException("negative pachinko runtime value");
        }
        if (rushActive && mode == Mode.NORMAL) throw new IllegalArgumentException("normal mode cannot have active rush");
        if (!initialHitCommitted && initialOutcome != InitialOutcome.NONE) {
            throw new IllegalArgumentException("uncommitted initial outcome");
        }
    }

    public PachinkoRuntime(Mode mode,long ballsHeld,long ballsLoaned,long totalFired,long totalStarts,long ballSequenceId,Presentation presentation,boolean initialHitCommitted,InitialOutcome initialOutcome,boolean rushActive,long rushWins,RightOutcome rightOutcome,long currentPayout,long cumulativePayout,long lastActivity) {
        this(mode,ballsHeld,ballsLoaned,totalFired,totalStarts,ballSequenceId,presentation,initialHitCommitted,initialOutcome,rushActive,rushWins,rightOutcome,currentPayout,cumulativePayout,PachinkoStatistics.empty(),lastActivity);
    }

    public static PachinkoRuntime initial() {
        return new PachinkoRuntime(
                Mode.NORMAL, 0, 0, 0, 0, 0,
                Presentation.IDLE, false, InitialOutcome.NONE,
                false, 0, RightOutcome.NONE, 0, 0, PachinkoStatistics.empty(), 0
        );
    }

    public static PachinkoRuntime fromJson(String json) {
        if (json == null || json.isBlank()) return initial();
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        return new PachinkoRuntime(
                enumValue(o, "mode", Mode.NORMAL),
                longValue(o, "ballsHeld", 0),
                longValue(o, "ballsLoaned", 0),
                longValue(o, "totalFired", 0),
                longValue(o, "totalStarts", 0),
                longValue(o, "ballSequenceId", 0),
                enumValue(o, "presentation", Presentation.IDLE),
                boolValue(o, "initialHitCommitted", false),
                enumValue(o, "initialOutcome", InitialOutcome.NONE),
                boolValue(o, "rushActive", false),
                longValue(o, "rushWins", 0),
                enumValue(o, "rightOutcome", RightOutcome.NONE),
                longValue(o, "currentPayout", 0),
                longValue(o, "cumulativePayout", 0),
                PachinkoStatistics.fromJson(o.has("statistics") ? o.getAsJsonObject("statistics") : null),
                longValue(o, "lastActivity", 0)
        );
    }

    public String toJsonString() {
        JsonObject o = new JsonObject();
        o.addProperty("mode", mode.name());
        o.addProperty("ballsHeld", ballsHeld);
        o.addProperty("ballsLoaned", ballsLoaned);
        o.addProperty("totalFired", totalFired);
        o.addProperty("totalStarts", totalStarts);
        o.addProperty("ballSequenceId", ballSequenceId);
        o.addProperty("presentation", presentation.name());
        o.addProperty("initialHitCommitted", initialHitCommitted);
        o.addProperty("initialOutcome", initialOutcome.name());
        o.addProperty("rushActive", rushActive);
        o.addProperty("rushWins", rushWins);
        o.addProperty("rightOutcome", rightOutcome.name());
        o.addProperty("currentPayout", currentPayout);
        o.addProperty("cumulativePayout", cumulativePayout);
        o.add("statistics", statistics.toJson());
        o.addProperty("lastActivity", lastActivity);
        return o.toString();
    }

    /** Explicit statistics reset; ordinary runtime transitions never clear production counters. */
    public PachinkoRuntime resetStatistics(long now) {
        return new PachinkoRuntime(mode, ballsHeld, ballsLoaned, 0, 0, ballSequenceId, presentation,
                initialHitCommitted, initialOutcome, rushActive, 0, rightOutcome, currentPayout, 0,
                PachinkoStatistics.empty(), now);
    }

    public double measuredSpinsPer1000Yen() {
        if (totalFired == 0) return 0.0;
        return (double) totalStarts * PachinkoSpec.BALLS_PER_1000_YEN / totalFired;
    }

    private static long longValue(JsonObject o, String key, long fallback) {
        return o.has(key) ? o.get(key).getAsLong() : fallback;
    }

    private static boolean boolValue(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }

    private static <E extends Enum<E>> E enumValue(JsonObject o, String key, E fallback) {
        if (!o.has(key)) return fallback;
        @SuppressWarnings("unchecked")
        Class<E> type = (Class<E>) fallback.getDeclaringClass();
        return Enum.valueOf(type, o.get(key).getAsString());
    }
}
