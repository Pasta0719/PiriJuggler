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
        String lastEvent,
        int additionalBigStock,
        int additionalRegStock,
        String pendingBonusHit,
        String suspendedBonusType,
        int suspendedBonusPayoutCount,
        boolean suspendedBonusEnded,
        boolean releasingStock
) {
    public enum Mode { NORMAL, HEAVEN, GOD_CHAIN }

    public JugglerGodRuntime {
        if (mode == null) throw new IllegalArgumentException("mode");
        if (heavenTarget < 0 || heavenTarget > 32) throw new IllegalArgumentException("heavenTarget");
        if (heavenProgress < 0 || heavenProgress > 32) throw new IllegalArgumentException("heavenProgress");
        if (guaranteedRemaining < 0) throw new IllegalArgumentException("guaranteedRemaining");
        if (godBigCount < 0) throw new IllegalArgumentException("godBigCount");
        if (additionalBigStock < 0 || additionalRegStock < 0) throw new IllegalArgumentException("additionalStock");
        if (suspendedBonusPayoutCount < 0) throw new IllegalArgumentException("suspendedBonusPayoutCount");
        if (bonusOrigin == null) bonusOrigin = "NONE";
        if (lastEvent == null) lastEvent = "NONE";
        if (pendingBonusHit == null) pendingBonusHit = "NONE";
        if (suspendedBonusType == null) suspendedBonusType = "NONE";
    }

    public JugglerGodRuntime(Mode mode,int heavenTarget,int heavenProgress,int guaranteedRemaining,
                             boolean forceChainBig,boolean countNextChainGame,String bonusOrigin,
                             int godBigCount,boolean godFreeze,String lastEvent) {
        this(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,lastEvent,0,0,"NONE","NONE",0,false,false);
    }

    public static JugglerGodRuntime initial() {
        return new JugglerGodRuntime(Mode.NORMAL,0,0,0,false,false,"NONE",0,false,"NONE",
                0,0,"NONE","NONE",0,false,false);
    }

    public boolean stockLampOn(){return additionalBigStock>0||additionalRegStock>0;}

    public JugglerGodRuntime core(Mode nextMode,int nextHeavenTarget,int nextHeavenProgress,int nextGuaranteedRemaining,
                                  boolean nextForceChainBig,boolean nextCountNextChainGame,String nextBonusOrigin,
                                  int nextGodBigCount,boolean nextGodFreeze,String nextLastEvent) {
        return new JugglerGodRuntime(nextMode,nextHeavenTarget,nextHeavenProgress,nextGuaranteedRemaining,
                nextForceChainBig,nextCountNextChainGame,nextBonusOrigin,nextGodBigCount,nextGodFreeze,nextLastEvent,
                additionalBigStock,additionalRegStock,pendingBonusHit,suspendedBonusType,
                suspendedBonusPayoutCount,suspendedBonusEnded,releasingStock);
    }

    public JugglerGodRuntime stock(int big,int reg,String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,event,big,reg,pendingBonusHit,suspendedBonusType,
                suspendedBonusPayoutCount,suspendedBonusEnded,releasingStock);
    }

    public JugglerGodRuntime interrupt(String hit,String currentType,int payoutCount,boolean ended,String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,event,additionalBigStock,additionalRegStock,
                hit,currentType,payoutCount,ended,false);
    }

    public JugglerGodRuntime clearInterrupt(String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,false,event,additionalBigStock,additionalRegStock,
                "NONE","NONE",0,false,releasingStock);
    }

    public JugglerGodRuntime release(int big,int reg,String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,false,event,big,reg,"NONE","NONE",0,false,true);
    }

    public JugglerGodRuntime stopReleasing(String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,false,event,additionalBigStock,additionalRegStock,
                "NONE","NONE",0,false,false);
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
                    text(j,"lastEvent","NONE"),
                    value(j,"additionalBigStock",0),
                    value(j,"additionalRegStock",0),
                    text(j,"pendingBonusHit","NONE"),
                    text(j,"suspendedBonusType","NONE"),
                    value(j,"suspendedBonusPayoutCount",0),
                    bool(j,"suspendedBonusEnded",false),
                    bool(j,"releasingStock",false)
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
        j.addProperty("additionalBigStock",additionalBigStock);
        j.addProperty("additionalRegStock",additionalRegStock);
        j.addProperty("pendingBonusHit",pendingBonusHit);
        j.addProperty("suspendedBonusType",suspendedBonusType);
        j.addProperty("suspendedBonusPayoutCount",suspendedBonusPayoutCount);
        j.addProperty("suspendedBonusEnded",suspendedBonusEnded);
        j.addProperty("releasingStock",releasingStock);
        return j;
    }

    public String toJsonString(){return toJson().toString();}

    private static int value(JsonObject j,String key,int fallback){return j.has(key)?j.get(key).getAsInt():fallback;}
    private static boolean bool(JsonObject j,String key,boolean fallback){return j.has(key)?j.get(key).getAsBoolean():fallback;}
    private static String text(JsonObject j,String key,String fallback){return j.has(key)?j.get(key).getAsString():fallback;}
}
