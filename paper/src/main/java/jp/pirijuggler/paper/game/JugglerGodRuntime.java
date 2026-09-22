package jp.pirijuggler.paper.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Persistent machine-side state for the JUGGLER_GOD successor. */
public record JugglerGodRuntime(
        Mode mode,
        int heavenTarget,
        int heavenProgress,
        int guaranteedRemaining,
        boolean forceChainBig,
        boolean countNextChainGame,
        String bonusOrigin,
        int godBigCount,
        boolean godFreeze,
        String lastEvent
) {
    public enum Mode { NORMAL, HEAVEN, GOD_CHAIN }

    public JugglerGodRuntime {
        if (mode == null) throw new IllegalArgumentException("mode");
        if (heavenTarget < 0 || heavenTarget > 32) throw new IllegalArgumentException("heavenTarget");
        if (heavenProgress < 0 || heavenProgress > 32) throw new IllegalArgumentException("heavenProgress");
        if (guaranteedRemaining < 0) throw new IllegalArgumentException("guaranteedRemaining");
        if (godBigCount < 0) throw new IllegalArgumentException("godBigCount");
        if (bonusOrigin == null) bonusOrigin = "NONE";
        if (lastEvent == null) lastEvent = "NONE";
    }

    public static JugglerGodRuntime initial() {
        return new JugglerGodRuntime(Mode.NORMAL,0,0,0,false,false,"NONE",0,false,"NONE");
    }

    public static JugglerGodRuntime fromJson(String raw) {
        if (raw == null || raw.isBlank()) return initial();
        try {
            JsonObject j = JsonParser.parseString(raw).getAsJsonObject();
            if (!j.has("jgMode")) return initial();
            return new JugglerGodRuntime(
                    Mode.valueOf(j.get("jgMode").getAsString()),
                    value(j,"heavenTarget",0),
                    value(j,"heavenProgress",0),
                    value(j,"guaranteedRemaining",0),
                    bool(j,"forceChainBig",false),
                    bool(j,"countNextChainGame",false),
                    text(j,"bonusOrigin","NONE"),
                    value(j,"godBigCount",0),
                    bool(j,"godFreeze",false),
                    text(j,"lastEvent","NONE")
            );
        } catch (RuntimeException invalid) {
            return initial();
        }
    }

    public JsonObject toJson() {
        JsonObject j=new JsonObject();
        j.addProperty("jgMode",mode.name());
        j.addProperty("heavenTarget",heavenTarget);
        j.addProperty("heavenProgress",heavenProgress);
        j.addProperty("guaranteedRemaining",guaranteedRemaining);
        j.addProperty("forceChainBig",forceChainBig);
        j.addProperty("countNextChainGame",countNextChainGame);
        j.addProperty("bonusOrigin",bonusOrigin);
        j.addProperty("godBigCount",godBigCount);
        j.addProperty("godFreeze",godFreeze);
        j.addProperty("lastEvent",lastEvent);
        return j;
    }

    public String toJsonString(){return toJson().toString();}

    private static int value(JsonObject j,String key,int fallback){return j.has(key)?j.get(key).getAsInt():fallback;}
    private static boolean bool(JsonObject j,String key,boolean fallback){return j.has(key)?j.get(key).getAsBoolean():fallback;}
    private static String text(JsonObject j,String key,String fallback){return j.has(key)?j.get(key).getAsString():fallback;}
}
