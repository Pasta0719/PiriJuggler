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
        boolean releasingStock,
        String forcedRole,
        long godPresentationStartMs,
        int hotRemaining,
        int roleStreak,
        String lastTriggerDebug,
        String lastWinSource
) {
    public enum Mode { NORMAL, HIGH, ULTRA, HEAVEN, GOD_CHAIN }

    public JugglerGodRuntime {
        if (mode == null) throw new IllegalArgumentException("mode");
        if (heavenTarget < 0 || heavenTarget > 32) throw new IllegalArgumentException("heavenTarget");
        if (heavenProgress < 0 || heavenProgress > 32) throw new IllegalArgumentException("heavenProgress");
        if (guaranteedRemaining < 0) throw new IllegalArgumentException("guaranteedRemaining");
        if (godBigCount < 0) throw new IllegalArgumentException("godBigCount");
        if (additionalBigStock < 0 || additionalRegStock < 0) throw new IllegalArgumentException("additionalStock");
        if (suspendedBonusPayoutCount < 0) throw new IllegalArgumentException("suspendedBonusPayoutCount");
        if (godPresentationStartMs < 0) throw new IllegalArgumentException("godPresentationStartMs");
        if (hotRemaining < 0 || hotRemaining > 20 || roleStreak < 0 || roleStreak > 15) throw new IllegalArgumentException("hotMode");
        if (bonusOrigin == null) bonusOrigin = "NONE";
        if (lastEvent == null) lastEvent = "NONE";
        if (pendingBonusHit == null) pendingBonusHit = "NONE";
        if (suspendedBonusType == null) suspendedBonusType = "NONE";
        if (forcedRole == null) forcedRole = "NONE";
        if (lastTriggerDebug == null) lastTriggerDebug = "NONE";
        if (lastWinSource == null) lastWinSource = "NONE";
    }

    /** Old 21-argument shape retained for existing machine/recovery/test call sites. */
    public JugglerGodRuntime(Mode mode,int heavenTarget,int heavenProgress,int guaranteedRemaining,
                             boolean forceChainBig,boolean countNextChainGame,String bonusOrigin,
                             int godBigCount,boolean godFreeze,String lastEvent,
                             int additionalBigStock,int additionalRegStock,String pendingBonusHit,
                             String suspendedBonusType,int suspendedBonusPayoutCount,
                             boolean suspendedBonusEnded,boolean releasingStock,String forcedRole,
                             long godPresentationStartMs,int hotRemaining,int roleStreak) {
        this(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,lastEvent,additionalBigStock,additionalRegStock,
                pendingBonusHit,suspendedBonusType,suspendedBonusPayoutCount,suspendedBonusEnded,
                releasingStock,forcedRole,godPresentationStartMs,hotRemaining,roleStreak,"NONE","NONE");
    }

    /** Backward-compatible constructor for existing session/test call sites. */
    public JugglerGodRuntime(Mode mode,int heavenTarget,int heavenProgress,int guaranteedRemaining,
                             boolean forceChainBig,boolean countNextChainGame,String bonusOrigin,
                             int godBigCount,boolean godFreeze,String lastEvent,
                             int additionalBigStock,int additionalRegStock,String pendingBonusHit,
                             String suspendedBonusType,int suspendedBonusPayoutCount,
                             boolean suspendedBonusEnded,boolean releasingStock,String forcedRole,
                             long godPresentationStartMs) {
        this(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
             bonusOrigin,godBigCount,godFreeze,lastEvent,additionalBigStock,additionalRegStock,
             pendingBonusHit,suspendedBonusType,suspendedBonusPayoutCount,suspendedBonusEnded,
             releasingStock,forcedRole,godPresentationStartMs,0,0);
    }

    public JugglerGodRuntime(Mode mode,int heavenTarget,int heavenProgress,int guaranteedRemaining,
                             boolean forceChainBig,boolean countNextChainGame,String bonusOrigin,
                             int godBigCount,boolean godFreeze,String lastEvent) {
        this(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,lastEvent,0,0,"NONE","NONE",0,false,false,"NONE",0);
    }

    /** Backward-compatible constructor for existing runtime/test call sites. */
    public JugglerGodRuntime(Mode mode,int heavenTarget,int heavenProgress,int guaranteedRemaining,
                             boolean forceChainBig,boolean countNextChainGame,String bonusOrigin,
                             int godBigCount,boolean godFreeze,String lastEvent,
                             int additionalBigStock,int additionalRegStock,String pendingBonusHit,
                             String suspendedBonusType,int suspendedBonusPayoutCount,
                             boolean suspendedBonusEnded,boolean releasingStock) {
        this(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,lastEvent,additionalBigStock,additionalRegStock,
                pendingBonusHit,suspendedBonusType,suspendedBonusPayoutCount,suspendedBonusEnded,
                releasingStock,"NONE",0);
    }

    /** Backward-compatible constructor including a forced role but no presentation timestamp. */
    public JugglerGodRuntime(Mode mode,int heavenTarget,int heavenProgress,int guaranteedRemaining,
                             boolean forceChainBig,boolean countNextChainGame,String bonusOrigin,
                             int godBigCount,boolean godFreeze,String lastEvent,
                             int additionalBigStock,int additionalRegStock,String pendingBonusHit,
                             String suspendedBonusType,int suspendedBonusPayoutCount,
                             boolean suspendedBonusEnded,boolean releasingStock,String forcedRole) {
        this(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,lastEvent,additionalBigStock,additionalRegStock,
                pendingBonusHit,suspendedBonusType,suspendedBonusPayoutCount,suspendedBonusEnded,
                releasingStock,forcedRole,0);
    }

    public static JugglerGodRuntime initial() {
        return new JugglerGodRuntime(Mode.NORMAL,0,0,0,false,false,"NONE",0,false,"NONE",
                0,0,"NONE","NONE",0,false,false,"NONE",0);
    }

    public boolean stockLampOn(){return additionalBigStock>0||additionalRegStock>0;}

    public JugglerGodRuntime core(Mode nextMode,int nextHeavenTarget,int nextHeavenProgress,int nextGuaranteedRemaining,
                                  boolean nextForceChainBig,boolean nextCountNextChainGame,String nextBonusOrigin,
                                  int nextGodBigCount,boolean nextGodFreeze,String nextLastEvent) {
        return new JugglerGodRuntime(nextMode,nextHeavenTarget,nextHeavenProgress,nextGuaranteedRemaining,
                nextForceChainBig,nextCountNextChainGame,nextBonusOrigin,nextGodBigCount,nextGodFreeze,nextLastEvent,
                additionalBigStock,additionalRegStock,pendingBonusHit,suspendedBonusType,
                suspendedBonusPayoutCount,suspendedBonusEnded,releasingStock,forcedRole,godPresentationStartMs,hotRemaining,roleStreak,lastTriggerDebug,lastWinSource);
    }

    /** Immutable update stored in the existing machine JSON; no new display state. */
    public JugglerGodRuntime withHot(Mode nextMode,int remaining,int streak,String event) {
        if (nextMode!=Mode.HIGH && nextMode!=Mode.ULTRA) remaining=0;
        return new JugglerGodRuntime(nextMode,heavenTarget,heavenProgress,guaranteedRemaining,
                forceChainBig,countNextChainGame,bonusOrigin,godBigCount,godFreeze,event,
                additionalBigStock,additionalRegStock,pendingBonusHit,suspendedBonusType,
                suspendedBonusPayoutCount,suspendedBonusEnded,releasingStock,forcedRole,
                godPresentationStartMs,remaining,streak,lastTriggerDebug,lastWinSource);
    }

    public JugglerGodRuntime clearHot() {
        Mode next=mode==Mode.HIGH||mode==Mode.ULTRA?Mode.NORMAL:mode;
        return withHot(next,0,0,lastEvent);
    }

    public JugglerGodRuntime stock(int big,int reg,String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,event,big,reg,pendingBonusHit,suspendedBonusType,
                suspendedBonusPayoutCount,suspendedBonusEnded,releasingStock,forcedRole,godPresentationStartMs,hotRemaining,roleStreak,lastTriggerDebug,lastWinSource);
    }

    public JugglerGodRuntime interrupt(String hit,String currentType,int payoutCount,boolean ended,String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,event,additionalBigStock,additionalRegStock,
                hit,currentType,payoutCount,ended,releasingStock,forcedRole,godPresentationStartMs,hotRemaining,roleStreak,lastTriggerDebug,lastWinSource);
    }

    public JugglerGodRuntime clearInterrupt(String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,false,event,additionalBigStock,additionalRegStock,
                "NONE","NONE",0,false,releasingStock,forcedRole,godPresentationStartMs,hotRemaining,roleStreak,lastTriggerDebug,lastWinSource);
    }

    /** Clear only the acquired overlay hit while keeping the interrupted bonus available for later resume. */
    public JugglerGodRuntime consumePendingHit(String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,event,additionalBigStock,additionalRegStock,
                "NONE",suspendedBonusType,suspendedBonusPayoutCount,suspendedBonusEnded,
                releasingStock,forcedRole,godPresentationStartMs,hotRemaining,roleStreak,lastTriggerDebug,lastWinSource);
    }

    public JugglerGodRuntime release(int big,int reg,String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,false,event,big,reg,"NONE","NONE",0,false,true,forcedRole,godPresentationStartMs,hotRemaining,roleStreak,lastTriggerDebug,lastWinSource);
    }

    public JugglerGodRuntime stopReleasing(String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,false,event,additionalBigStock,additionalRegStock,
                "NONE","NONE",0,false,false,forcedRole,godPresentationStartMs,hotRemaining,roleStreak,lastTriggerDebug,lastWinSource);
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
                    bool(j,"releasingStock",false),
                    text(j,"forcedRole","NONE"),
                    longValue(j,"godPresentationStartMs",0),
                    value(j,"hotRemaining",0),
                    value(j,"roleStreak",0),
                    text(j,"lastTriggerDebug","NONE"),
                    text(j,"lastWinSource","NONE")
            );
        } catch (RuntimeException invalid) {
            return initial();
        }
    }

    public JugglerGodRuntime startPresentation(long startMs,String event) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,event,additionalBigStock,additionalRegStock,pendingBonusHit,
                suspendedBonusType,suspendedBonusPayoutCount,suspendedBonusEnded,releasingStock,forcedRole,startMs,hotRemaining,roleStreak,lastTriggerDebug,lastWinSource);
    }

    public JugglerGodRuntime clearPresentation(String event) {
        return startPresentation(0,event);
    }

    /** Debug metadata is written after an actual lever; never changes any draw. */
    public JugglerGodRuntime withBonusDebug(String triggerDebug,String winSource) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,
                forceChainBig,countNextChainGame,bonusOrigin,godBigCount,godFreeze,lastEvent,
                additionalBigStock,additionalRegStock,pendingBonusHit,suspendedBonusType,
                suspendedBonusPayoutCount,suspendedBonusEnded,releasingStock,forcedRole,
                godPresentationStartMs,hotRemaining,roleStreak,
                triggerDebug==null?lastTriggerDebug:triggerDebug,
                winSource==null?lastWinSource:winSource);
    }

    /** HIGH/ULTRA and role streak reset for a new JVM business period; GOD rights survive. */
    public JugglerGodRuntime resetNewSessionPrecursors() {
        Mode nextMode=(mode==Mode.HIGH||mode==Mode.ULTRA)?Mode.NORMAL:mode;
        return new JugglerGodRuntime(nextMode,heavenTarget,heavenProgress,guaranteedRemaining,
                forceChainBig,countNextChainGame,bonusOrigin,godBigCount,godFreeze,lastEvent,
                additionalBigStock,additionalRegStock,pendingBonusHit,suspendedBonusType,
                suspendedBonusPayoutCount,suspendedBonusEnded,releasingStock,"NONE",
                godPresentationStartMs,0,0,"NONE","NONE");
    }

    public JugglerGodRuntime forceRole(String role) {
        return new JugglerGodRuntime(mode,heavenTarget,heavenProgress,guaranteedRemaining,forceChainBig,countNextChainGame,
                bonusOrigin,godBigCount,godFreeze,lastEvent,additionalBigStock,additionalRegStock,pendingBonusHit,
                suspendedBonusType,suspendedBonusPayoutCount,suspendedBonusEnded,releasingStock,role==null?"NONE":role,godPresentationStartMs,hotRemaining,roleStreak,lastTriggerDebug,lastWinSource);
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
        j.addProperty("forcedRole",forcedRole);
        j.addProperty("godPresentationStartMs",godPresentationStartMs);
        j.addProperty("hotRemaining",hotRemaining);
        j.addProperty("roleStreak",roleStreak);
        j.addProperty("lastTriggerDebug",lastTriggerDebug);
        j.addProperty("lastWinSource",lastWinSource);
        return j;
    }

    public String toJsonString(){return toJson().toString();}

    private static int value(JsonObject j,String key,int fallback){return j.has(key)?j.get(key).getAsInt():fallback;}
    private static long longValue(JsonObject j,String key,long fallback){return j.has(key)?j.get(key).getAsLong():fallback;}
    private static boolean bool(JsonObject j,String key,boolean fallback){return j.has(key)?j.get(key).getAsBoolean():fallback;}
    private static String text(JsonObject j,String key,String fallback){return j.has(key)?j.get(key).getAsString():fallback;}
}
