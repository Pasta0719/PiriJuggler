package jp.pirijuggler.paper.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.pirijuggler.paper.session.Session;
import java.util.UUID;

/**
 * Restore the public HYPER_DOG reel information from its authoritative
 * physical state for world spectators; never include the hidden drawn role.
 */
public final class HyperDogRemoteSnapshot {
    private HyperDogRemoteSnapshot() {}

    public static void correct(Machine machine, Session session, JsonObject body) {
        if (machine.type() != MachineType.HYPER_DOG || session == null) return;
        JsonObject saved = session.machineState();
        if (saved == null || !saved.has("reel")) {
            body.addProperty("spinning", false);
            body.addProperty("stoppedMask", 7);
            return;
        }
        JsonObject reel = saved.getAsJsonObject("reel");
        if (reel == null) {
            body.addProperty("spinning", false);
            body.addProperty("stoppedMask", 7);
            return;
        }
        JsonArray stops = reel.getAsJsonArray("stops");
        JsonArray startPhases = reel.getAsJsonArray("startPhases");
        if (stops == null || stops.size() != 3 || startPhases == null || startPhases.size() != 3) {
            body.addProperty("spinning", false);
            body.addProperty("stoppedMask", 7);
            return;
        }
        int mask = 0;
        JsonObject displayStops = body.getAsJsonObject("displayStops");
        if (displayStops == null) return;
        String[] names = {"left", "center", "right"};
        JsonObject phase = new JsonObject();
        for (int i = 0; i < 3; i++) {
            int stop = stops.get(i).getAsInt();
            if (stop < -1 || stop >= 21) {
                body.addProperty("spinning", false);
                body.addProperty("stoppedMask", 7);
                return;
            }
            double start = startPhases.get(i).getAsDouble();
            if (!Double.isFinite(start) || start < 0 || start >= 21) {
                body.addProperty("spinning", false);
                body.addProperty("stoppedMask", 7);
                return;
            }
            if (stop >= 0) {
                mask |= 1 << i;
                displayStops.addProperty(names[i], stop);
            }
            phase.addProperty(names[i], start);
        }
        body.addProperty("stoppedMask", mask);
        boolean spinning = mask != 7;
        body.addProperty("spinning", spinning);
        if (!spinning) return;
        String spinId = reel.get("spinId").getAsString();
        UUID.fromString(spinId);
        body.addProperty("spinId", spinId);
        body.addProperty("animation", "RESUME_NORMAL");
        body.add("startPhase", phase);
    }
}
