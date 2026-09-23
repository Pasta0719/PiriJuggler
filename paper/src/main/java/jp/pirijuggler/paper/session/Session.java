package jp.pirijuggler.paper.session;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.pirijuggler.common.protocol.PublicGameState;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Immutable copy of every persisted column, including nullable game snapshots. */
public record Session(Map<String, Object> snapshot) {
    public enum GameState { SEATED_READY, NORMAL_BETTED, NORMAL_SPINNING, REPLAY_READY,
        BONUS_PENDING_BIG, BONUS_PENDING_REG, BONUS_ENTRY_BETTED_BIG, BONUS_ENTRY_BETTED_REG,
        BONUS_ENTRY_SPINNING_BIG, BONUS_ENTRY_SPINNING_REG, BIG_READY, BIG_BETTED, BIG_SPINNING,
        REG_READY, REG_BETTED, REG_SPINNING }
    public enum Lifecycle { ACTIVE, SUSPENDED_GRACE, SUSPENDED_SAFE }
    public Session { snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(snapshot)); }
    public String text(String key) { return (String) snapshot.get(key); }
    public long number(String key) { return ((Number) snapshot.get(key)).longValue(); }
    public UUID id() { return UUID.fromString(text("session_id")); }
    public UUID player() { return UUID.fromString(text("player_uuid")); }
    public int machine() { return (int) number("machine_id"); }
    public GameState state() { return GameState.valueOf(text("game_state")); }
    public Lifecycle lifecycle() { return Lifecycle.valueOf(text("lifecycle")); }
    public boolean ownsLock() { return lifecycle() != Lifecycle.SUSPENDED_SAFE; }
    public long sequence() { return number("last_client_sequence"); }
    public boolean ready() { return state() == GameState.SEATED_READY; }
    public JsonObject machineState() {
        Object raw = snapshot.get("machine_state_json");
        if (!(raw instanceof String json) || json.isBlank()) return null;
        return JsonParser.parseString(json).getAsJsonObject();
    }
    public PublicGameState publicGameState() {
        return switch (state()) {
            case SEATED_READY -> PublicGameState.SEATED_READY;
            case NORMAL_BETTED -> PublicGameState.NORMAL_BETTED;
            case NORMAL_SPINNING -> PublicGameState.NORMAL_SPINNING;
            case REPLAY_READY -> PublicGameState.REPLAY_READY;
            case BONUS_PENDING_BIG, BONUS_PENDING_REG -> PublicGameState.BONUS_PENDING;
            case BONUS_ENTRY_BETTED_BIG, BONUS_ENTRY_BETTED_REG -> PublicGameState.BONUS_ENTRY_BETTED;
            case BONUS_ENTRY_SPINNING_BIG, BONUS_ENTRY_SPINNING_REG -> PublicGameState.BONUS_ENTRY_SPINNING;
            case BIG_READY -> PublicGameState.BIG_READY;
            case BIG_BETTED -> PublicGameState.BIG_BETTED;
            case BIG_SPINNING -> PublicGameState.BIG_SPINNING;
            case REG_READY -> PublicGameState.REG_READY;
            case REG_BETTED -> PublicGameState.REG_BETTED;
            case REG_SPINNING -> PublicGameState.REG_SPINNING;
        };
    }
    public JsonObject identity() {
        JsonObject json = new JsonObject();
        json.addProperty("sessionId", id().toString()); json.addProperty("machineId", machine());
        return json;
    }
    public JsonObject openPacket() {
        JsonObject json = identity(); json.addProperty("expectedNextClientSequence", sequence() + 1); return json;
    }
    private static void copy(JsonObject from,JsonObject to,String source,String target){
        if(from.has(source))to.add(target,from.get(source).deepCopy());
    }
    public JsonObject publicState() {
        JsonObject json = openPacket();
        json.addProperty("gameState", publicGameState().name()); json.addProperty("lifecycle", lifecycle().name());
        json.addProperty("credit", number("credit")); json.addProperty("heldMedals", number("held_medals"));
        json.addProperty("bet", number("current_bet")); json.addProperty("pay", number("pay_display"));
        json.addProperty("bonusCount", number("bonus_payout_count")); json.addProperty("lampOn", number("lamp_on") != 0);
        JsonObject stops = new JsonObject();
        stops.addProperty("left", number("display_left_stop")); stops.addProperty("center", number("display_center_stop"));
        stops.addProperty("right", number("display_right_stop")); json.add("displayStops", stops);
        json.addProperty("stoppedMask", number("stopped_mask"));
        JsonObject ms=machineState();
        if(ms!=null&&ms.has("godFreeze"))json.addProperty("godFreeze",ms.get("godFreeze").getAsBoolean());
        if(ms!=null&&ms.has("jgMode")){
            json.addProperty("godPresentationStartMs",ms.has("godPresentationStartMs")?ms.get("godPresentationStartMs").getAsLong():0L);
            boolean godFirstBigAudio=ms.has("bonusOrigin")&&"GOD_CHAIN".equals(ms.get("bonusOrigin").getAsString())
                    &&ms.has("godBigCount")&&ms.get("godBigCount").getAsInt()==1
                    &&publicGameState().name().startsWith("BIG_");
            json.addProperty("godFirstBigAudio",godFirstBigAudio);
        }
        if(ms!=null&&ms.has("godBigCount"))json.addProperty("godChainBigCount",ms.get("godBigCount").getAsInt());
        if(ms!=null&&ms.has("additionalBigStock")&&ms.has("additionalRegStock"))
            json.addProperty("stockLampOn",ms.get("additionalBigStock").getAsInt()>0||ms.get("additionalRegStock").getAsInt()>0);
        if(ms!=null&&ms.has("phase")){
            json.addProperty("godPhase",ms.get("phase").getAsString());
            copy(ms,json,"ggGamesRemaining","godGgRemaining");
            copy(ms,json,"queuedGgStocks","godStocks");
            copy(ms,json,"gZoneGamesRemaining","godGZoneRemaining");
            copy(ms,json,"sggGamesRemaining","godSggRemaining");
            copy(ms,json,"zZoneGamesRemaining","godZZoneRemaining");
            copy(ms,json,"lastEvent","godLastEvent");
            copy(ms,json,"lastRole","godLastRole");
        }
        return json;
    }
}
