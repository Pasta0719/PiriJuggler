package jp.pirijuggler.fabric.ui;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import java.util.*;
import java.util.function.LongSupplier;
import jp.pirijuggler.common.reel.ReelMotion;

/** Public packets only; visual interpolation never computes a winning result. */
public final class SlotViewState {
    private final LongSupplier time;
    private final double[] starts=new double[3],rest=new double[3];
    private final Stop[] stops=new Stop[3];
    private record Stop(double from,double target,long at,long duration) {}
    private UUID session,spin;private int machine;private long spinAt,noticeAt;private String animation="NORMAL";private int stopEnableAfterMs;
    private boolean spinning,notice,blink;private JsonObject state,dataLamp;private String error="";
    public SlotViewState(LongSupplier nanos){time=nanos;}
    public void receive(Envelope envelope) {
        JsonObject b=envelope.payload();long now=time.getAsLong();
        switch(envelope.packetType()) {
            case OPEN_MACHINE -> {session=UUID.fromString(b.get("sessionId").getAsString());machine=b.get("machineId").getAsInt();spin=null;state=null;dataLamp=null;error="";spinning=false;notice=false;blink=false;Arrays.fill(stops,null);Arrays.fill(rest,0);}
            case PUBLIC_STATE -> {if(matches(b)) {
                state=b.deepCopy();notice=b.get("lampOn").getAsBoolean();error="";
                var display=b.getAsJsonObject("displayStops");
                for(int i=0;i<3;i++){rest[i]=display.get(new String[]{"left","center","right"}[i]).getAsDouble();if(spin==null)starts[i]=rest[i];}
                if(!b.get("gameState").getAsString().contains("SPINNING"))spinning=false;
            }}
            case SPIN_START -> {if(matches(b)) {
                spin=UUID.fromString(b.get("spinId").getAsString());animation=b.get("animation").getAsString();spinAt=now;spinning=true;error="";
                stopEnableAfterMs=b.has("stopEnableAfterMs")?b.get("stopEnableAfterMs").getAsInt():ReelMotion.Profile.valueOf(animation).clientDelayMs();
                var phases=b.getAsJsonObject("startPhase");for(int i=0;i<3;i++)starts[i]=phases.get(new String[]{"left","center","right"}[i]).getAsDouble();Arrays.fill(stops,null);
                if(animation.equals("RESUME_NORMAL")&&state!=null&&state.has("stoppedMask"))for(int i=0;i<3;i++)if((state.get("stoppedMask").getAsInt()&(1<<i))!=0)stops[i]=new Stop(rest[i],rest[i],now,0);
            }}
            case REEL_STOP -> {if(matchesSpin(b)) {
                int reel=switch(b.get("reel").getAsString()){case "LEFT"->0;case "CENTER"->1;case "RIGHT"->2;default->throw new IllegalArgumentException("Unknown reel");};
                if(stops[reel]!=null)return;double from=phase(reel);int target=b.get("stopIndex").getAsInt();
                double endpoint=ReelMotion.normalStopEndpoint(from,target);int requested=b.get("durationMs").getAsInt();int visualMs=ReelMotion.visualDurationMs(from,endpoint,requested);
                stops[reel]=new Stop(from,endpoint,now,visualMs*1_000_000L);rest[reel]=target;
            }}
            case NOTICE -> {if(matchesSpin(b)){notice="ON".equals(b.get("lamp").getAsString());blink="FAST_BLINK_1S".equals(b.get("pattern").getAsString());noticeAt=now;}}
            case DATA_LAMP -> {if(b.has("machineId")&&b.get("machineId").getAsInt()==machine)dataLamp=b.deepCopy();}
            case ACTION_REJECTED,ERROR -> {if(b.has("errorCode"))error=b.get("errorCode").getAsString();}
            default -> {}
        }
    }
    public boolean matches(JsonObject b){return session!=null&&b.has("sessionId")&&b.has("machineId")&&session.toString().equals(b.get("sessionId").getAsString())&&machine==b.get("machineId").getAsInt();}
    public boolean matchesSpin(JsonObject b){return spin!=null&&b.has("spinId")&&spin.toString().equals(b.get("spinId").getAsString());}
    public static double wrap(double value){return ReelMotion.wrap(value);}
    public static double distance(String animation,double seconds){return ReelMotion.delta(ReelMotion.Profile.valueOf(animation),seconds);}
    public double phase(int reel){
        long now=time.getAsLong();Stop stop=stops[reel];if(stop!=null){double p=stop.duration==0?1:Math.min(1,Math.max(0,(now-stop.at)/(double)stop.duration));return p>=1?wrap(stop.target):wrap(stop.from+(stop.target-stop.from)*p);}
        return spinning?wrap(starts[reel]+distance(animation,(now-spinAt)/1e9)):rest[reel];
    }
    public boolean lampOn(){long elapsed=time.getAsLong()-noticeAt;return notice&&(!blink||elapsed>=1_000_000_000L||elapsed/100_000_000L%2==0);}
    public boolean canSend(PacketType action){return !spinning||!Set.of(PacketType.SPACE_ACTION,PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT).contains(action)||time.getAsLong()-spinAt>=stopEnableAfterMs*1_000_000L;}
    public JsonObject publicState(){return state==null?null:state.deepCopy();}
    public JsonObject dataLamp(){return dataLamp==null?null:dataLamp.deepCopy();}
    public String error(){return error;}
    public int machineId(){return machine;}
    public String value(String name){return state!=null&&state.has(name)?state.get(name).getAsString():"—";}
}
