package jp.pirijuggler.paper.game.god;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * Persisted machine-specific state for one seated Piri GOD player.
 *
 * The legacy session game_state remains the coarse transaction/lifecycle state used
 * by shared Juggler infrastructure. This object owns GOD-specific progression.
 */
public record GodSessionState(
        GodPhase phase,
        GodFrontMode frontMode,
        int normalGamesSinceGg,
        int ggGamesRemaining,
        int guaranteedGgStocks,
        GodLoopType loopType,
        int gZoneGamesRemaining,
        int sggGamesRemaining,
        int sggContinuationStocks,
        int zZoneGamesRemaining,
        int zYellowStreak,
        int zGameStocks,
        long totalGodGames
) {
    private static final Gson GSON = new Gson();

    public GodSessionState {
        Objects.requireNonNull(phase);
        Objects.requireNonNull(frontMode);
        if (normalGamesSinceGg < 0 || ggGamesRemaining < 0 || guaranteedGgStocks < 0 ||
                gZoneGamesRemaining < 0 || sggGamesRemaining < 0 || sggContinuationStocks < 0 ||
                zZoneGamesRemaining < 0 || zYellowStreak < 0 || zGameStocks < 0 || totalGodGames < 0)
            throw new IllegalArgumentException("negative GOD state counter");
    }

    public static GodSessionState initial() {
        return new GodSessionState(
                GodPhase.NORMAL, GodFrontMode.LOW_A, 0, 0, 0, null,
                0, 0, 0, 0, 0, 0, 0
        );
    }

    public JsonObject toJson() {
        return GSON.toJsonTree(this).getAsJsonObject();
    }

    public String toJsonString() {
        return GSON.toJson(this);
    }

    public static GodSessionState fromJson(JsonObject json) {
        if (json == null) return initial();
        GodSessionState decoded = GSON.fromJson(json, GodSessionState.class);
        return decoded == null ? initial() : decoded;
    }
}
