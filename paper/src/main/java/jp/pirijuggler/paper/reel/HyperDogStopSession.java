package jp.pirijuggler.paper.reel;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import java.util.*;

/**
 * Isolated Paper transport adapter for HYPER_DOG Phase 02. The future GameEngine
 * owns role drawing, accounting and persistence; this adapter only handles stops.
 * No internal role or lottery state is transmitted to Fabric.
 */
public final class HyperDogStopSession {
    public record Snapshot(UUID sessionId,int machineId,HyperDogRound.Snapshot round,
                           long startedEpochMs,double[] startPhases) {
        public Snapshot {
            Objects.requireNonNull(sessionId);Objects.requireNonNull(round);
            if(machineId<=0||startedEpochMs<0||startPhases==null||startPhases.length!=3)
                throw new IllegalArgumentException("snapshot");
            startPhases=startPhases.clone();
            for(double p:startPhases)if(!Double.isFinite(p)||p<0||p>=21)
                throw new IllegalArgumentException("phase");
        }
        @Override public double[] startPhases(){return startPhases.clone();}
    }
    private final UUID sessionId;
    private final int machineId;
    private final long startedEpochMs;
    private final double[] startPhases;
    private final HyperDogRound round;

    public HyperDogStopSession(UUID sessionId,int machineId,UUID spinId,
                               HyperDogReelControl.Role role,long startedEpochMs,double[] startPhases) {
        this(new Snapshot(sessionId,machineId,new HyperDogRound(spinId,role).snapshot(),startedEpochMs,startPhases));
    }
    private HyperDogStopSession(Snapshot saved) {
        sessionId=saved.sessionId();machineId=saved.machineId();
        startedEpochMs=saved.startedEpochMs();startPhases=saved.startPhases();
        round=HyperDogRound.restore(saved.round());
    }
    public static HyperDogStopSession restore(Snapshot saved){return new HyperDogStopSession(saved);}
    public Snapshot snapshot(){return new Snapshot(sessionId,machineId,round.snapshot(),startedEpochMs,startPhases);}
    public boolean complete(){return round.complete();}
    public long presentationEndsAtMs(){
        return Math.addExact(startedEpochMs,round.role()==HyperDogReelControl.Role.AT_ENTRY_RED7?1040:0);
    }
    public boolean canStartNextGame(long nowMs){
        return round.complete() && nowMs>=presentationEndsAtMs();
    }

    public Envelope begin() {
        JsonObject b=new JsonObject();
        b.addProperty("sessionId",sessionId.toString());
        b.addProperty("machineId",machineId);
        b.addProperty("spinId",round.spinId().toString());
        b.addProperty("animation","NORMAL");
        b.addProperty("mode",presentation());
        b.addProperty("stopEnableAfterMs",isCz()?900:700);
        b.addProperty("startedEpochMs",startedEpochMs);
        JsonObject phases=new JsonObject();
        for(Reel reel:Reel.values())phases.addProperty(reel.name().toLowerCase(Locale.ROOT),startPhases[reel.ordinal()]);
        b.add("startPhase",phases);
        // Do not send a 21-entry role-derived stop hint table: it reveals unrevealed internal roles.
        return Envelope.current(PacketType.SPIN_START,b);
    }
    public List<Envelope> replayOnReconnect() {
        List<Envelope> result=new ArrayList<>();
        result.add(begin());
        var h=round.snapshot().history();
        for(int index:h.order()){
            Reel reel=Reel.values()[index];
            int p=h.input(index),target=h.stop(index);
            int slip=ReelMotion.slip(target,p);
            var choice=new HyperDogReelControl.Choice(p,target,slip,ReelMotion.durationMs(slip));
            result.add(stopPacket(reel,choice,isAutomatic(reel)));
        }
        return List.copyOf(result);
    }

    /** Called by Paper's scheduled tick; repeated or late calls are idempotent. */
    public List<Envelope> tick(long nowMs) {
        if(nowMs<startedEpochMs)return List.of();
        long elapsed=nowMs-startedEpochMs;
        List<Envelope> result=new ArrayList<>();
        if(isCz()) {
            if(elapsed>=500 && (round.stoppedMask()&Reel.LEFT.bit())==0)
                result.add(automatic(Reel.LEFT,500));
            if(elapsed>=700 && (round.stoppedMask()&Reel.RIGHT.bit())==0)
                result.add(automatic(Reel.RIGHT,700));
        } else if(round.role()==HyperDogReelControl.Role.AT_ENTRY_RED7 && elapsed>=720) {
            for(Reel reel:Reel.values())if((round.stoppedMask()&reel.bit())==0)
                result.add(automatic(reel,720));
        }
        return List.copyOf(result);
    }
    private Envelope automatic(Reel reel,long relativeMs){
        var out=round.stopAutomatic(reel,pressedAt(reel,relativeMs));
        return stopPacket(reel,out.choice(),true);
    }

    /** Paper receives only an existing STOP packet, never a client-supplied role or payout. */
    public List<Envelope> receive(Envelope input,long nowMs) {
        List<Envelope> events=new ArrayList<>(tick(nowMs));
        long sequence=-1;
        try {
            if(input.protocol()!=Protocol.VERSION) {events.add(ErrorPackets.rejected(sequence,ErrorCode.PROTOCOL_MISMATCH));return events;}
            JsonObject b=input.payload();
            if(!b.has("sessionId")||!b.has("machineId")||!b.has("clientSequence") ||
                b.size() != (b.has("pressedIndex")?4:3) ||
                !sessionId.toString().equals(b.get("sessionId").getAsString()) ||
                machineId!=b.get("machineId").getAsInt()) {
                events.add(ErrorPackets.rejected(sequence,ErrorCode.SESSION_MISMATCH));return events;
            }
            sequence=b.get("clientSequence").getAsLong();
            if(sequence<=round.snapshot().lastSequence()) {
                events.add(ErrorPackets.rejected(sequence,ErrorCode.SEQUENCE_OLD));return events;
            }
            Reel reel=switch(input.packetType()){
                case STOP_LEFT->Reel.LEFT;case STOP_CENTER->Reel.CENTER;case STOP_RIGHT->Reel.RIGHT;
                case SPACE_ACTION->nextManualReel();default->null;
            };
            if(reel==null) {
                events.add(ErrorPackets.rejected(sequence,ErrorCode.INVALID_STATE));return events;
            }
            // Non-manual presentation paths reject input regardless of auto-stop progress.
            // A complete red-seven animation is still input-locked, not a user stop.
            if(round.role()==HyperDogReelControl.Role.AT_ENTRY_RED7 ||
                (isCz() && reel!=Reel.CENTER)) {
                events.add(ErrorPackets.rejected(sequence,ErrorCode.INVALID_STATE));return events;
            }
            if((round.stoppedMask()&reel.bit())!=0){
                events.add(ErrorPackets.rejected(sequence,ErrorCode.ALREADY_STOPPED));return events;
            }
            if(nowMs<startedEpochMs || nowMs-startedEpochMs < (isCz()?900:650)){
                events.add(ErrorPackets.rejected(sequence,ErrorCode.STOP_TOO_EARLY));return events;
            }
            int pressed=b.has("pressedIndex")?b.get("pressedIndex").getAsInt():
                pressedAt(reel,nowMs-startedEpochMs);
            if(pressed<0||pressed>=21){
                events.add(ErrorPackets.rejected(sequence,ErrorCode.SESSION_MISMATCH));return events;
            }
            var choice=round.stopManual(reel,pressed,sequence).choice();
            JsonObject accepted=new JsonObject();
            accepted.addProperty("clientSequence",sequence);
            accepted.addProperty("action",input.packetType().name());
            events.add(Envelope.current(PacketType.ACTION_ACCEPTED,accepted));
            events.add(stopPacket(reel,choice,false));
            return List.copyOf(events);
        } catch(IllegalArgumentException | IllegalStateException | UnsupportedOperationException malformed){
            events.add(ErrorPackets.rejected(sequence,ErrorCode.INVALID_STATE));
            return List.copyOf(events);
        }
    }

    private Reel nextManualReel(){
        if(isCz())return Reel.CENTER;
        for(Reel reel:Reel.values())if((round.stoppedMask()&reel.bit())==0)return reel;
        return null;
    }
    private int pressedAt(Reel reel,long elapsedMs){
        double phase=ReelMotion.phase(ReelMotion.Profile.NORMAL,startPhases[reel.ordinal()],elapsedMs/1000.0);
        return (int)Math.floor(phase);
    }
    private Envelope stopPacket(Reel reel,HyperDogReelControl.Choice choice,boolean automatic){
        JsonObject b=new JsonObject();
        b.addProperty("spinId",round.spinId().toString());
        b.addProperty("reel",reel.name());
        b.addProperty("pressedIndex",choice.pressedIndex());
        b.addProperty("stopIndex",choice.stopIndex());
        b.addProperty("slip",choice.slip());
        b.addProperty("durationMs",automatic?0:choice.durationMs());
        b.addProperty("automatic",automatic);
        return Envelope.current(PacketType.REEL_STOP,b);
    }
    private String presentation(){return isCz()?"CZ":round.role()==HyperDogReelControl.Role.AT_ENTRY_RED7?"AT_RED7_CONFIRM":round.role()==HyperDogReelControl.Role.AT_ENTRY_RAINBOW7?"RAINBOW7_CONFIRM":"NORMAL";}
    private boolean isCz(){return round.role()==HyperDogReelControl.Role.CZ_WIN||round.role()==HyperDogReelControl.Role.CZ_LOSE;}
    private boolean isAutomatic(Reel reel){return isCz()&&reel!=Reel.CENTER||round.role()==HyperDogReelControl.Role.AT_ENTRY_RED7;}

    /** Lossless durable snapshot of the server-only role, reel and timing state. Never send this to clients. */
    public JsonObject toJson(){
        JsonObject b=new JsonObject();var saved=snapshot();var h=saved.round().history();
        b.addProperty("sessionId",saved.sessionId().toString());b.addProperty("machineId",saved.machineId());
        b.addProperty("spinId",saved.round().spinId().toString());b.addProperty("role",saved.round().role().name());
        b.addProperty("startedEpochMs",startedEpochMs);b.addProperty("lastSequence",saved.round().lastSequence());
        JsonArray phases=new JsonArray();for(double phase:startPhases)phases.add(phase);b.add("startPhases",phases);
        b.add("inputs",ints(h.inputs()));b.add("stops",ints(h.stops()));b.add("order",ints(h.order()));
        return b;
    }
    public static HyperDogStopSession fromJson(JsonObject b){
        try {
            double[] phases=new double[3];JsonArray p=b.getAsJsonArray("startPhases");
            if(p.size()!=3)throw new IllegalArgumentException("phases");
            for(int i=0;i<3;i++)phases[i]=p.get(i).getAsDouble();
            var history=new SkillStopHistory(readInts(b,"inputs"),readInts(b,"stops"),readInts(b,"order"));
            var round=new HyperDogRound.Snapshot(UUID.fromString(b.get("spinId").getAsString()),
                    HyperDogReelControl.Role.valueOf(b.get("role").getAsString()),history,
                    b.get("lastSequence").getAsLong());
            return restore(new Snapshot(UUID.fromString(b.get("sessionId").getAsString()),b.get("machineId").getAsInt(),
                    round,b.get("startedEpochMs").getAsLong(),phases));
        }catch(RuntimeException problem){throw new IllegalArgumentException("invalid HYPER_DOG durable snapshot",problem);}
    }
    private static JsonArray ints(int[] values){JsonArray a=new JsonArray();for(int value:values)a.add(value);return a;}
    private static int[] readInts(JsonObject b,String key){JsonArray a=b.getAsJsonArray(key);int[] r=new int[a.size()];for(int i=0;i<r.length;i++)r[i]=a.get(i).getAsInt();return r;}
}
