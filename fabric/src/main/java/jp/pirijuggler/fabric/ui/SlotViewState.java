package jp.pirijuggler.fabric.ui;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import java.util.*;
import java.util.function.LongSupplier;
import jp.pirijuggler.common.reel.ReelMotion;

/** Public packets only; visual interpolation never computes a winning result. */
public final class SlotViewState {
    private static final long MIN_GAME_INTERVAL_NANOS=2_000_000_000L;
    private final LongSupplier time;
    private final double[] starts=new double[3],rest=new double[3];
    private final Stop[] stops=new Stop[3];
    private final Press[] presses=new Press[3];
    private record Stop(double from,double target,long at,long duration) {}
    private record Press(int pressedIndex,int stopIndex,long at) {}
    private UUID session,spin;private int machine;private String machineType="JUGGLER";private long spinAt,noticeAt,nextGameAt;private String animation="NORMAL";private int stopEnableAfterMs;
    private boolean spinning,notice,blink;private JsonObject state,dataLamp,stopHints=new JsonObject();private String error="";
    public SlotViewState(LongSupplier nanos){time=nanos;}
    public void receive(Envelope envelope) {
        JsonObject b=envelope.payload();long now=time.getAsLong();
        switch(envelope.packetType()) {
            case OPEN_MACHINE -> {session=UUID.fromString(b.get("sessionId").getAsString());machine=b.get("machineId").getAsInt();machineType=b.has("machineType")?b.get("machineType").getAsString():"JUGGLER";spin=null;state=null;dataLamp=null;stopHints=new JsonObject();error="";spinning=false;notice=false;blink=false;nextGameAt=0;Arrays.fill(stops,null);Arrays.fill(presses,null);Arrays.fill(rest,0);}
            case PUBLIC_STATE -> {if(matches(b)) {
                state=b.deepCopy();if(b.has("machineType"))machineType=b.get("machineType").getAsString();notice=b.get("lampOn").getAsBoolean();error="";
                var display=b.getAsJsonObject("displayStops");
                for(int i=0;i<3;i++){rest[i]=display.get(new String[]{"left","center","right"}[i]).getAsDouble();if(spin==null)starts[i]=rest[i];}
                if(!b.get("gameState").getAsString().contains("SPINNING")){spinning=false;stopHints=new JsonObject();Arrays.fill(presses,null);}
            }}
            case SPIN_START -> {if(matches(b)) {
                spin=UUID.fromString(b.get("spinId").getAsString());animation=b.get("animation").getAsString();spinAt=now;spinning=true;error="";
                stopEnableAfterMs=b.has("stopEnableAfterMs")?b.get("stopEnableAfterMs").getAsInt():ReelMotion.Profile.valueOf(animation).clientDelayMs();
                stopHints=b.has("stopHints")?b.getAsJsonObject("stopHints").deepCopy():new JsonObject();
                var phases=b.getAsJsonObject("startPhase");for(int i=0;i<3;i++)starts[i]=phases.get(new String[]{"left","center","right"}[i]).getAsDouble();Arrays.fill(stops,null);Arrays.fill(presses,null);
                if(animation.equals("RESUME_NORMAL")&&state!=null&&state.has("stoppedMask"))for(int i=0;i<3;i++)if((state.get("stoppedMask").getAsInt()&(1<<i))!=0)stops[i]=new Stop(rest[i],rest[i],now,0);
            }}
            case REEL_STOP -> {if(matchesSpin(b)) {
                int reel=switch(b.get("reel").getAsString()){case "LEFT"->0;case "CENTER"->1;case "RIGHT"->2;default->throw new IllegalArgumentException("Unknown reel");};
                Press press=presses[reel];int target=b.get("stopIndex").getAsInt();int pressed=b.has("pressedIndex")?b.get("pressedIndex").getAsInt():-1;
                if(press!=null&&press.pressedIndex()==pressed&&press.stopIndex()==target){
                    presses[reel]=null;rest[reel]=target;
                } else {
                    double from=phase(reel);presses[reel]=null;int requested=b.get("durationMs").getAsInt();
                    double endpoint=ReelMotion.normalStopEndpoint(from,target);int visualMs=ReelMotion.visualDurationMs(from,endpoint,requested);
                    stops[reel]=new Stop(from,endpoint,now,visualMs*1_000_000L);rest[reel]=target;
                }
                if(b.has("nextStopHints"))stopHints=b.getAsJsonObject("nextStopHints").deepCopy();
                if(allStopped())nextGameAt=spinAt+MIN_GAME_INTERVAL_NANOS;
            }}
            case NOTICE -> {if(matchesSpin(b)){notice="ON".equals(b.get("lamp").getAsString());blink="FAST_BLINK_1S".equals(b.get("pattern").getAsString());noticeAt=now;}}
            case DATA_LAMP -> {if(b.has("machineId")&&b.get("machineId").getAsInt()==machine)dataLamp=b.deepCopy();}
            case ACTION_REJECTED,ERROR -> {if(b.has("errorCode"))error=b.get("errorCode").getAsString();for(int i=0;i<3;i++)if(presses[i]!=null){presses[i]=null;stops[i]=null;}}
            default -> {}
        }
    }
    /**
     * Begin the exact server-precomputed stop motion at the local button edge. The returned
     * pressed index is sent back to Paper, which validates timing and uses that same index.
     */
    public int localInput(PacketType action){
        if(!spinning||hasPendingPress())return -1;
        int reel=switch(action){case STOP_LEFT->0;case STOP_CENTER->1;case STOP_RIGHT->2;case SPACE_ACTION->nextPendingReel();default->-1;};
        if(reel<0||stops[reel]!=null)return -1;
        double from=phase(reel);int pressed=(int)Math.floor(from);JsonObject hint=hint(reel,pressed);if(hint==null)return -1;
        int target=hint.get("stopIndex").getAsInt(),requested=hint.get("durationMs").getAsInt();long now=time.getAsLong();
        double endpoint=ReelMotion.normalStopEndpoint(from,target);int visualMs=ReelMotion.visualDurationMs(from,endpoint,requested);
        stops[reel]=new Stop(from,endpoint,now,visualMs*1_000_000L);presses[reel]=new Press(pressed,target,now);rest[reel]=target;return pressed;
    }
    private JsonObject hint(int reel,int pressed){
        String name=new String[]{"left","center","right"}[reel];if(!stopHints.has(name))return null;JsonArray a=stopHints.getAsJsonArray(name);return pressed>=0&&pressed<a.size()?a.get(pressed).getAsJsonObject():null;
    }
    private boolean hasPendingPress(){for(var p:presses)if(p!=null)return true;return false;}
    private boolean allStopped(){for(var s:stops)if(s==null)return false;return true;}
    private int nextPendingReel(){for(int i=0;i<3;i++)if(stops[i]==null)return i;return -1;}
    private boolean leverReadyState(){
        if(state==null||!state.has("gameState"))return false;
        return switch(state.get("gameState").getAsString()){
            case "NORMAL_BETTED","REPLAY_READY","BONUS_ENTRY_BETTED_BIG","BONUS_ENTRY_BETTED_REG","BIG_BETTED","REG_BETTED"->true;
            default->false;
        };
    }
    public boolean shouldQueueLever(){return !spinning&&leverReadyState()&&time.getAsLong()<nextGameAt;}
    public boolean queuedLeverReady(){return !spinning&&leverReadyState()&&time.getAsLong()>=nextGameAt;}
    public long nextGameRemainingNanos(){return Math.max(0,nextGameAt-time.getAsLong());}
    public boolean matches(JsonObject b){return session!=null&&b.has("sessionId")&&b.has("machineId")&&session.toString().equals(b.get("sessionId").getAsString())&&machine==b.get("machineId").getAsInt();}
    public boolean matchesSpin(JsonObject b){return spin!=null&&b.has("spinId")&&spin.toString().equals(b.get("spinId").getAsString());}
    public static double wrap(double value){return ReelMotion.wrap(value);}
    public static double distance(String animation,double seconds){return ReelMotion.delta(ReelMotion.Profile.valueOf(animation),seconds);}
    public double phase(int reel){
        long now=time.getAsLong();Stop stop=stops[reel];
        if(stop!=null){double p=stop.duration==0?1:Math.min(1,Math.max(0,(now-stop.at)/(double)stop.duration));return p>=1?rest[reel]:wrap(stop.from+(stop.target-stop.from)*p);}
        return spinning?wrap(starts[reel]+distance(animation,(now-spinAt)/1e9)):rest[reel];
    }
    public boolean lampOn(){long elapsed=time.getAsLong()-noticeAt;return notice&&(!blink||elapsed>=1_000_000_000L||elapsed/100_000_000L%2==0);}
    public boolean canSend(PacketType action){
        if(!spinning||!Set.of(PacketType.SPACE_ACTION,PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT).contains(action))return true;
        if(time.getAsLong()-spinAt<stopEnableAfterMs*1_000_000L||hasPendingPress())return false;
        int reel=switch(action){case STOP_LEFT->0;case STOP_CENTER->1;case STOP_RIGHT->2;case SPACE_ACTION->nextPendingReel();default->-1;};
        return reel>=0&&stops[reel]==null&&stopHints.has(new String[]{"left","center","right"}[reel]);
    }
    public JsonObject publicState(){return state==null?null:state.deepCopy();}
    public JsonObject dataLamp(){return dataLamp==null?null:dataLamp.deepCopy();}
    public String error(){return error;}
    public int machineId(){return machine;}
    public String machineType(){return machineType;}
    public String value(String name){return state!=null&&state.has(name)?state.get(name).getAsString():"—";}
}
