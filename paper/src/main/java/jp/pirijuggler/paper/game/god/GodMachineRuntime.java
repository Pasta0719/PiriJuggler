package jp.pirijuggler.paper.game.god;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Objects;

/** Hidden cabinet-owned runtime. It survives seat changes and server restarts. */
public record GodMachineRuntime(
        GodFrontMode frontMode,
        int normalGamesSinceGg,
        int blue7History,
        int yellow7History,
        int gaiaBellCount,
        long totalNormalGames,
        GodSessionState gameplay
) {
    private static final Gson GSON = new Gson();

    public GodMachineRuntime {
        Objects.requireNonNull(frontMode);
        if (gameplay == null) gameplay = GodSessionState.initial();
        if (normalGamesSinceGg < 0 || blue7History < 0 || yellow7History < 0 || gaiaBellCount < 0 || totalNormalGames < 0)
            throw new IllegalArgumentException("negative GOD runtime counter");
    }

    public static GodMachineRuntime initial() {
        return new GodMachineRuntime(GodFrontMode.LOW_A, 0, 0, 0, 0, 0, GodSessionState.initial());
    }

    public GodMachineRuntime withGameplay(GodSessionState state) {
        return new GodMachineRuntime(frontMode, normalGamesSinceGg, blue7History, yellow7History, gaiaBellCount, totalNormalGames, state);
    }

    public String toJsonString() { return GSON.toJson(this); }
    public JsonObject toJson() { return GSON.toJsonTree(this).getAsJsonObject(); }

    public static GodMachineRuntime fromJson(String json) {
        if (json == null || json.isBlank()) return initial();
        GodMachineRuntime decoded = GSON.fromJson(json, GodMachineRuntime.class);
        return decoded == null ? initial() : decoded;
    }
}
