package jp.pirijuggler.paper.game.god;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * Hidden machine-owned Piri GOD runtime.
 *
 * Unlike a seated player's transient session state, this survives player changes
 * because normal-mode/ceiling progression belongs to the cabinet.
 */
public record GodMachineRuntime(
        GodFrontMode frontMode,
        int normalGamesSinceGg,
        int blue7History,
        int yellow7History,
        int gaiaBellCount,
        long totalNormalGames
) {
    private static final Gson GSON = new Gson();

    public GodMachineRuntime {
        Objects.requireNonNull(frontMode);
        if (normalGamesSinceGg < 0 || blue7History < 0 || yellow7History < 0 || gaiaBellCount < 0 || totalNormalGames < 0)
            throw new IllegalArgumentException("negative GOD runtime counter");
    }

    public static GodMachineRuntime initial() {
        return new GodMachineRuntime(GodFrontMode.LOW_A, 0, 0, 0, 0, 0);
    }

    public String toJsonString() {
        return GSON.toJson(this);
    }

    public JsonObject toJson() {
        return GSON.toJsonTree(this).getAsJsonObject();
    }

    public static GodMachineRuntime fromJson(String json) {
        if (json == null || json.isBlank()) return initial();
        GodMachineRuntime decoded = GSON.fromJson(json, GodMachineRuntime.class);
        return decoded == null ? initial() : decoded;
    }
}
