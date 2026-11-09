package jp.pirijuggler.fabric.ui;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * HYPER_DOG Phase05: read-only projection of server-authoritative public packets.
 * Does not draw hidden internal role, mode, stock count or RNG.
 * Display events are identified by the server and replay exactly once per
 * screen instance, including after reconnect. Cosmetic times use client clock.
 */
public final class HyperDogCabinetState {
    public enum Lamp { LOWER, UPPER, CZ }
    public record DisplayEvent(long id,String kind,long amount,int durationMs) {
        public DisplayEvent {
            if(id<0||amount<0||(!kind.equals("TOTAL")&&!kind.equals("ADD"))||
                    durationMs!=(kind.equals("TOTAL")?1400:1200))
                throw new IllegalArgumentException("invalid display event");
        }
        public String text(){return kind.equals("TOTAL")?"TOTAL "+amount:"+"+amount;}
    }
    private final LongSupplier clock;
    private UUID sessionId;
    private int machineId;
    private JsonObject publicData,dataLamp;
    private String error="";
    private final Deque<DisplayEvent> pending=new ArrayDeque<>();
    private final Set<Long> seen=new HashSet<>();
    private final Set<Long> seenFlash=new HashSet<>();
    private long flashStartEpochMs=-1,flashAward;
    private String flashColor="";
    private DisplayEvent showing;
    private long showingUntil;
    private long lastUpdateMs;
    private boolean closeRequested;

    public HyperDogCabinetState(LongSupplier millis){clock=Objects.requireNonNull(millis);}
    public void receive(Envelope packet) {
        JsonObject data=packet.payload();
        switch(packet.packetType()){
            case OPEN_MACHINE -> {
                if(!data.has("machineType")||!"HYPER_DOG".equals(data.get("machineType").getAsString()))return;
                reset();
                sessionId=UUID.fromString(data.get("sessionId").getAsString());
                machineId=data.get("machineId").getAsInt();
            }
            case PUBLIC_STATE -> {
                if(!matches(data))return;
                publicData=data.deepCopy();
                lastUpdateMs=clock.getAsLong();
                error="";
                if(data.has("displayEvents")&&!data.get("displayEvents").isJsonNull()){
                    JsonArray events=data.getAsJsonArray("displayEvents");
                    if(events.size()>64)throw new IllegalArgumentException("too many display events");
                    for(JsonElement element:events){
                        JsonObject e=element.getAsJsonObject();
                        String kind=e.get("kind").getAsString();
                        DisplayEvent next=new DisplayEvent(e.get("id").getAsLong(),kind,
                                e.get("amount").getAsLong(),kind.equals("TOTAL")?1400:1200);
                        if(seen.add(next.id()))pending.addLast(next);
                    }
                }
                // Never replay the same server-confirmed flash after a duplicated PUBLIC_STATE.
                if(data.has("flashEventId")&&data.has("flashStartedEpochMs")){
                    long id=data.get("flashEventId").getAsLong();
                    if(id>0&&seenFlash.add(id)){
                        flashStartEpochMs=data.get("flashStartedEpochMs").getAsLong();
                        flashAward=data.get("flashAward").getAsLong();
                        flashColor=data.get("flashColor").getAsString();
                    }
                }
                advance();
            }
            case DATA_LAMP -> {
                if(data.has("machineId")&&data.get("machineId").getAsInt()==machineId)
                    dataLamp=data.deepCopy();
            }
            case ACTION_REJECTED,ERROR -> {
                if(sessionId!=null&&data.has("errorCode"))
                    error=data.get("errorCode").getAsString();
            }
            case SESSION_END,SESSION_SUSPENDED -> {
                if(matches(data))reset();
            }
            default -> {}
        }
    }
    private boolean matches(JsonObject b){
        return sessionId!=null&&b.has("sessionId")&&b.has("machineId")&&
                sessionId.toString().equals(b.get("sessionId").getAsString())&&
                machineId==b.get("machineId").getAsInt();
    }
    public void reset(){
        sessionId=null;machineId=0;publicData=null;dataLamp=null;error="";
        pending.clear();seen.clear();seenFlash.clear();showing=null;showingUntil=0;
        flashColor="";flashAward=0;flashStartEpochMs=-1;
        closeRequested=false;lastUpdateMs=0;
    }
    public UUID sessionId(){return sessionId;}
    public int machineId(){return machineId;}
    public String error(){return error;}
    public JsonObject dataLamp(){return dataLamp==null?null:dataLamp.deepCopy();}
    public String value(String key){
        if(publicData==null||!publicData.has(key)||publicData.get(key).isJsonNull())return "—";
        return publicData.get(key).getAsString();
    }
    public long number(String key){
        if(publicData==null||!publicData.has(key)||publicData.get(key).isJsonNull())return 0;
        return publicData.get(key).getAsLong();
    }
    public boolean flag(String key){
        return publicData!=null&&publicData.has(key)&&!publicData.get(key).isJsonNull()&&
                publicData.get(key).getAsBoolean();
    }
    public boolean announcement(){return flag("lampOn");}
    public boolean closeRequested(){return closeRequested;}
    public void markCloseRequested(){closeRequested=true;}
    public long lastUpdateMs(){return lastUpdateMs;}
    public String stage(){
        if(publicData==null)return "NORMAL";
        return publicData.has("atStage")&&!publicData.get("atStage").isJsonNull()
                ?publicData.get("atStage").getAsString():
                flag("comebackDisplay")?"COMEBACK":"NORMAL";
    }
    public boolean active(Lamp lamp){
        String stage=stage();long elapsed=Math.floorMod(clock.getAsLong(),2000);
        return switch(lamp){
            case LOWER -> stage.startsWith("LOWER_")||("COMEBACK".equals(stage)&&
                (elapsed<200||(elapsed>=400&&elapsed<600)));
            case UPPER -> stage.startsWith("UPPER_")&&!stage.equals("UPPER_CZ");
            case CZ -> stage.equals("UPPER_CZ")||
                (stage.startsWith("LOWER_")&&flag("hasCzTicket")&&elapsed<450);
        };
    }
    /**
     * Read-only expectation indicator: positive award is a required prerequisite,
     * and RED/RGB guarantees must never be contradicted even if an old packet
     * arrives during a state switch.
     */
    public int rightCount(){
        if(publicData!=null&&publicData.has("rightLampActive")&&!flag("rightLampActive"))return 0;
        long award=number("newAward");
        if(!isPresentationPreview()&&award<10)return 0;
        String c=color();
        return c==null?0:Math.min(3,Math.max(0,(int)number("rightLampCount")));
    }
    private boolean isPresentationPreview(){
        return publicData!=null&&publicData.has("rightLampActive")&&flag("rightLampActive")
                &&!flag("awardRevealed");
    }
    private static long minimum(String value){
        return switch(value){
            case "WHITE" -> 10;case "BLUE" -> 20;case "GREEN" -> 30;
            case "YELLOW" -> 50;case "PURPLE" -> 100;case "RED" -> 500;
            case "RAINBOW" -> 2000;default -> Long.MAX_VALUE;
        };
    }
    public String color(){
        String value=value("rightLampColor");
        if(minimum(value)==Long.MAX_VALUE)return null;
        if(isPresentationPreview())return value; // Only the stage permitted by Paper, no unrevealed award.
        long award=number("newAward");
        return award>=minimum(value)&&(!"RAINBOW".equals(value)||award==2000)?value:null;
    }
    /** A flash can only use the actual third-stop award, never a TOTAL or guessed payout. */
    public String flashColor(){
        if(flashStartEpochMs<0||!eligibleFlash()||flashElapsedMs()>=600)return null;
        return flashColor;
    }
    private boolean eligibleFlash(){
        return flashAward>=minimum(flashColor)&&(!"RAINBOW".equals(flashColor)||flashAward==2000);
    }
    public long flashElapsedMs(){
        if(flashStartEpochMs<0)return Long.MAX_VALUE;
        return Math.max(0L,clock.getAsLong()-flashStartEpochMs);
    }
    public int flashAlphaPercent(){
        if(flashColor()==null||flashElapsedMs()%150>=80)return 0;
        return switch(flashColor){
            case "WHITE" -> 26;case "BLUE" -> 28;case "GREEN" -> 30;
            case "YELLOW" -> 33;case "PURPLE" -> 38;case "RED" -> 42;
            case "RAINBOW" -> 35;default -> 0;
        };
    }
    public boolean displayLocked(){
        advance();return showing!=null||!pending.isEmpty();
    }
    public String displayText(){
        advance();
        if(showing!=null)return showing.text();
        String stage=stage();
        if(stage.startsWith("LOWER_")||stage.equals("UPPER_AT")||stage.equals("UPPER_INITIAL"))
            return Long.toString(number("atRemaining"));
        return "";
    }
    public int displayQueued(){advance();return pending.size()+(showing==null?0:1);}
    private void advance(){
        long now=clock.getAsLong();
        if(showing!=null&&now<showingUntil)return;
        while(showing!=null&&now>=showingUntil)showing=null;
        if(showing==null&&!pending.isEmpty()){
            showing=pending.removeFirst();
            showingUntil=Math.addExact(now,showing.durationMs());
        }
    }
    /** Immutable UI specification rectangles: never duplicate or shift controls. */
    public static SlotLayout.Rect leftStatusRect(int index){
        if(index<0||index>2)throw new IllegalArgumentException("lamp");
        return new SlotLayout.Rect(418,372+61*index,146,36);
    }
    public static SlotLayout.Rect announcementRect(int index){
        if(index<0||index>1)throw new IllegalArgumentException("lamp");
        return new SlotLayout.Rect(index==0?574:1520,230,76,64);
    }
    public static SlotLayout.Rect rightLampRect(int index){
        if(index<0||index>2)throw new IllegalArgumentException("lamp");
        return new SlotLayout.Rect(1583,370+69*index,34,34);
    }
    public static SlotLayout.Rect lcdRect(){return new SlotLayout.Rect(356,575,272,132);}
}
