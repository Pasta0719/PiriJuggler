package jp.pirijuggler.paper.game.god;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Objects;

/** Persisted visible/active gameplay state for Piri GOD. */
public record GodSessionState(
        GodPhase phase,
        int ggGamesRemaining,
        int queuedGgStocks,
        GodLoopType loopType,
        int gZoneGamesRemaining,
        int sggGamesRemaining,
        int sggContinuationStocks,
        int sggSetNumber,
        int zZoneGamesRemaining,
        int zYellowStreak,
        int zYellowCount,
        int zGameStocks,
        long totalGodGames,
        String lastEvent,
        String lastRole
) {
    private static final Gson GSON = new Gson();

    public GodSessionState {
        Objects.requireNonNull(phase);
        if (ggGamesRemaining < 0 || queuedGgStocks < 0 || gZoneGamesRemaining < 0 ||
                sggGamesRemaining < 0 || sggContinuationStocks < 0 || sggSetNumber < 0 ||
                zZoneGamesRemaining < 0 || zYellowStreak < 0 || zYellowCount < 0 || zGameStocks < 0 || totalGodGames < 0)
            throw new IllegalArgumentException("negative GOD state counter");
        if (lastEvent == null) lastEvent = "READY";
        if (lastRole == null) lastRole = "NONE";
    }

    public static GodSessionState initial() {
        return new GodSessionState(
                GodPhase.NORMAL, 0, 0, null,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                "READY", "NONE"
        );
    }

    /** Backward-compatible constructor for snapshots/code written before zYellowCount existed. */
    public GodSessionState(
            GodPhase phase,int ggGamesRemaining,int queuedGgStocks,GodLoopType loopType,
            int gZoneGamesRemaining,int sggGamesRemaining,int sggContinuationStocks,int sggSetNumber,
            int zZoneGamesRemaining,int zYellowStreak,int zGameStocks,long totalGodGames,
            String lastEvent,String lastRole
    ){
        this(phase,ggGamesRemaining,queuedGgStocks,loopType,gZoneGamesRemaining,sggGamesRemaining,
                sggContinuationStocks,sggSetNumber,zZoneGamesRemaining,zYellowStreak,0,zGameStocks,
                totalGodGames,lastEvent,lastRole);
    }

    public JsonObject toJson() { return GSON.toJsonTree(this).getAsJsonObject(); }
    public String toJsonString() { return GSON.toJson(this); }

    public static GodSessionState fromJson(JsonObject json) {
        if (json == null) return initial();
        GodSessionState decoded = GSON.fromJson(json, GodSessionState.class);
        return decoded == null ? initial() : decoded;
    }
}
