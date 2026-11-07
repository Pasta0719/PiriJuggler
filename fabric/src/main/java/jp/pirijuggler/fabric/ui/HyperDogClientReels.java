package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import com.google.gson.*;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * HYPER_DOG-only Fabric reel model. It never receives or draws the hidden role.
 * Stop positions originate in Paper REEL_STOP packets, not client lottery logic.
 * The dedicated cabinet screen will consume this model when user PNGs arrive.
 */
public final class HyperDogClientReels {
    private final LongSupplier epochMillis;
    private UUID sessionId, spinId;
    private int machineId;
    private String presentation="NORMAL";
    private final int[] stops={-1,-1,-1},pressed={-1,-1,-1};
    private final double[] starts={0,0,0};
    private long localReceivedAt, serverStartedAt;
    private boolean opened;

    public HyperDogClientReels(LongSupplier epochMillis){this.epochMillis=Objects.requireNonNull(epochMillis);}
    public boolean opened(){return opened;}
    public boolean spinning(){return opened&&spinId!=null;}
    public UUID spinId(){return spinId;}
    public int machineId(){return machineId;}
    public String presentation(){return presentation;}
    public int stopIndex(Reel reel){return stops[Objects.requireNonNull(reel).ordinal()];}
    public int pressedIndex(Reel reel){return pressed[Objects.requireNonNull(reel).ordinal()];}
    public int stoppedMask(){int mask=0;for(int i=0;i<3;i++)if(stops[i]>=0)mask|=1<<i;return mask;}
    public boolean complete(){return spinning()&&stoppedMask()==7;}

    public void receive(Envelope envelope){
        Objects.requireNonNull(envelope);
        JsonObject b=envelope.payload();
        switch(envelope.packetType()){
            case OPEN_MACHINE -> open(b);
            case SESSION_END, SESSION_SUSPENDED -> {
                if(opened&&b.has("sessionId")&&sessionId.toString().equals(b.get("sessionId").getAsString()))reset();
            }
            case SPIN_START -> {
                if(!matches(b))return;
                spinId=UUID.fromString(b.get("spinId").getAsString());
                presentation=b.get("mode").getAsString();
                if(!Set.of("NORMAL","CZ","AT_RED7_CONFIRM").contains(presentation))
                    throw new IllegalArgumentException("presentation");
                var phases=b.getAsJsonObject("startPhase");
                for(Reel reel:Reel.values()){
                    double start=phases.get(reel.name().toLowerCase(Locale.ROOT)).getAsDouble();
                    if(!Double.isFinite(start)||start<0||start>=21)throw new IllegalArgumentException("phase");
                    starts[reel.ordinal()]=start;
                }
                Arrays.fill(stops,-1);Arrays.fill(pressed,-1);
                serverStartedAt=b.get("startedEpochMs").getAsLong();
                localReceivedAt=epochMillis.getAsLong();
            }
            case REEL_STOP -> {
                if(!spinning()||!b.has("spinId")||!spinId.toString().equals(b.get("spinId").getAsString()))return;
                Reel reel=Reel.valueOf(b.get("reel").getAsString());int i=reel.ordinal();
                int target=b.get("stopIndex").getAsInt(),input=b.get("pressedIndex").getAsInt();
                int slip=b.get("slip").getAsInt();
                if(target<0||target>=21||input<0||input>=21||slip!=ReelMotion.slip(target,input))
                    throw new IllegalArgumentException("bad authoritative stop");
                if(stops[i]>=0) {
                    if(stops[i]!=target||pressed[i]!=input)throw new IllegalStateException("contradictory server stop");
                    return; // idempotent duplicate packet
                }
                stops[i]=target;pressed[i]=input;
            }
            default -> {}
        }
    }

    public HyperDogReelControl.Icon visible(Reel reel,int row){
        Objects.requireNonNull(reel);
        if(!spinning())throw new IllegalStateException("no HYPER_DOG spin");
        int i=reel.ordinal();
        int middle=stops[i]>=0?stops[i]:currentIndex(reel);
        return HyperDogReelControl.visible(reel,middle,row);
    }
    public String texturePath(Reel reel,int row){
        if(!spinning())throw new IllegalStateException("no HYPER_DOG spin");
        int middle=stops[reel.ordinal()]>=0?stops[reel.ordinal()]:currentIndex(reel);
        return HyperDogReelImageMapping.texturePath(reel,middle,row);
    }
    public int currentIndex(Reel reel){
        if(!spinning())throw new IllegalStateException("no HYPER_DOG spin");
        int i=reel.ordinal();if(stops[i]>=0)return stops[i];
        long elapsed=Math.max(0,epochMillis.getAsLong()-localReceivedAt);
        return (int)Math.floor(ReelMotion.phase(ReelMotion.Profile.NORMAL,starts[i],elapsed/1000.0));
    }
    public boolean canStop(PacketType action){
        if(!spinning()||complete()||"AT_RED7_CONFIRM".equals(presentation))return false;
        long elapsed=Math.max(epochMillis.getAsLong()-localReceivedAt,epochMillis.getAsLong()-serverStartedAt);
        if("CZ".equals(presentation)){
            return elapsed>=900 && stoppedMask()==(Reel.LEFT.bit()|Reel.RIGHT.bit()) &&
                (action==PacketType.STOP_CENTER||action==PacketType.SPACE_ACTION);
        }
        if(elapsed<700)return false;
        Reel reel=switch(action){
            case STOP_LEFT -> Reel.LEFT;case STOP_CENTER -> Reel.CENTER;case STOP_RIGHT -> Reel.RIGHT;
            case SPACE_ACTION -> Arrays.stream(Reel.values()).filter(r->(stoppedMask()&r.bit())==0).findFirst().orElse(null);
            default -> null;
        };
        return reel!=null&&(stoppedMask()&reel.bit())==0;
    }
    private void open(JsonObject b){
        reset();
        if(!b.has("machineType")||!"HYPER_DOG".equals(b.get("machineType").getAsString()))return;
        sessionId=UUID.fromString(b.get("sessionId").getAsString());
        machineId=b.get("machineId").getAsInt();
        opened=true;
    }
    private boolean matches(JsonObject b){
        return opened&&b.has("sessionId")&&b.has("machineId")&&
            sessionId.toString().equals(b.get("sessionId").getAsString())&&
            b.get("machineId").getAsInt()==machineId;
    }
    private void reset(){
        opened=false;sessionId=null;spinId=null;machineId=0;presentation="NORMAL";
        Arrays.fill(stops,-1);Arrays.fill(pressed,-1);Arrays.fill(starts,0);
        localReceivedAt=0;serverStartedAt=0;
    }
}
