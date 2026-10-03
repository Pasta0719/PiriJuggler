package jp.pirijuggler.paper.reel;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.action.ActionGate;
import jp.pirijuggler.paper.threading.MainThread;
import java.util.*;

/** Existing STOP protocol and motion, with the skill machine's dedicated controller. */
public final class SkillStopRound {
    private final SkillStopControl control;
    private final SkillStopControl.Context context;
    private final ReelRound.Identity identity;
    private final ReelMotion.Profile profile;
    private final double[] starts;
    private final String mode;
    private final MainThread main;
    private final ActionGate gate;
    private SkillStopHistory history;
    private StopTriplet display;
    private Long motionStart;
    public SkillStopRound(SkillStopControl control, SkillStopControl.Context context, SkillStopHistory history,
                          ReelRound.Identity identity, ReelMotion.Profile profile, String mode, double[] starts,
                          StopTriplet display, long sequence, MainThread main) {
        main.requireMainThread();this.control=control;this.context=context;this.history=history;this.identity=identity;
        this.profile=profile;this.mode=mode;this.starts=starts.clone();this.display=display;this.main=main;this.gate=new ActionGate(main,sequence);
        if(starts.length!=3)throw new IllegalArgumentException("starts");
        for(Reel r:Reel.values())if((history.mask()&r.bit())!=0&&history.stop(r.ordinal())!=display.stop(r))throw new IllegalArgumentException("history/display mismatch");
    }
    public Envelope begin(long now) {
        main.requireMainThread();if(motionStart!=null)throw new IllegalStateException("already began");motionStart=now;
        JsonObject b=new JsonObject();b.addProperty("sessionId",identity.session().toString());b.addProperty("machineId",identity.machine());b.addProperty("spinId",identity.spin().toString());
        b.addProperty("mode",mode);b.addProperty("animation",profile.name());
        JsonObject phases=new JsonObject();for(Reel r:Reel.values())phases.addProperty(r.name().toLowerCase(Locale.ROOT),starts[r.ordinal()]);
        b.add("startPhase",phases);b.addProperty("stopEnableAfterMs",profile.clientDelayMs());b.add("stopHints",hints());return Envelope.current(PacketType.SPIN_START,b);
    }
    public ReelRound.Result receive(UUID owner, Envelope input, long received, int ping) {
        main.requireMainThread();long sequence=-1;JsonObject b=input.payload();
        try {
            if(input.protocol()!=Protocol.VERSION)return rejected(sequence,ErrorCode.PROTOCOL_MISMATCH);
            Set<String> keys=new HashSet<>(b.keySet());boolean hasPressed=keys.remove("pressedIndex");
            if(!keys.equals(Set.of("sessionId","machineId","clientSequence")))return rejected(sequence,ErrorCode.SESSION_MISMATCH);
            sequence=b.get("clientSequence").getAsBigDecimal().longValueExact();
            if(hasPressed){int p=b.get("pressedIndex").getAsBigDecimal().intValueExact();if(p<0||p>=21)return rejected(sequence,ErrorCode.SESSION_MISMATCH);}
            if(!identity.owner().equals(owner)||!identity.session().toString().equals(b.get("sessionId").getAsString())||identity.machine()!=b.get("machineId").getAsBigDecimal().intValueExact())return rejected(sequence,ErrorCode.SESSION_MISMATCH);
        }catch(RuntimeException invalid){return rejected(sequence,ErrorCode.SESSION_MISMATCH);}
        var decision=gate.acceptStop(sequence,identity.spin(),motionStart==null?null:identity.spin());if(decision.error()!=null)return rejected(sequence,decision.error());
        Reel reel=switch(input.packetType()){case STOP_LEFT->Reel.LEFT;case STOP_CENTER->Reel.CENTER;case STOP_RIGHT->Reel.RIGHT;case SPACE_ACTION->Arrays.stream(Reel.values()).filter(r->(history.mask()&r.bit())==0).findFirst().orElse(null);default->null;};
        if(reel==null)return rejected(sequence,ErrorCode.INVALID_STATE);
        if((history.mask()&reel.bit())!=0)return rejected(sequence,ErrorCode.ALREADY_STOPPED);
        if(ReelMotion.effectiveMillis(motionStart,received,ping)<profile.serverThresholdMs())return rejected(sequence,ErrorCode.STOP_TOO_EARLY);
        try(var lease=gate.beginBusy()){
            int pressed=b.has("pressedIndex")?b.get("pressedIndex").getAsInt():ReelMotion.pressedIndex(profile,starts[reel.ordinal()],motionStart,received,ping);
            var choice=control.choose(context,history,reel,pressed);history=history.append(reel,pressed,choice.stopIndex());display=display.with(reel,choice.stopIndex());
            int tenpai=SkillStopControl.sevenTenpai(history);boolean sound=history.count()==2&&(context.premium()==SkillStopControl.Premium.F||tenpai>0);
            JsonObject accepted=new JsonObject();accepted.addProperty("clientSequence",sequence);accepted.addProperty("action",input.packetType().name());
            JsonObject stop=new JsonObject();stop.addProperty("spinId",identity.spin().toString());stop.addProperty("reel",reel.name());stop.addProperty("pressedIndex",pressed);stop.addProperty("stopIndex",choice.stopIndex());stop.addProperty("slip",choice.slip());stop.addProperty("durationMs",choice.durationMs());stop.add("nextStopHints",hints());
            var legacyChoice=new StopSolver.Choice(display,0,pressed,choice.stopIndex(),choice.slip(),choice.durationMs());
            return new ReelRound.Result(List.of(Envelope.current(PacketType.ACTION_ACCEPTED,accepted),Envelope.current(PacketType.REEL_STOP,stop)),legacyChoice,sound,tenpai);
        }
    }
    private JsonObject hints(){
        JsonObject all=new JsonObject();for(Reel r:Reel.values())if((history.mask()&r.bit())==0){JsonArray a=new JsonArray();for(int p=0;p<21;p++){
            var c=control.choose(context,history,r,p);JsonObject item=new JsonObject();item.addProperty("stopIndex",c.stopIndex());item.addProperty("slip",c.slip());item.addProperty("durationMs",c.durationMs());a.add(item);
        }all.add(r.name().toLowerCase(Locale.ROOT),a);}return all;
    }
    private static ReelRound.Result rejected(long sequence,ErrorCode code){return new ReelRound.Result(List.of(ErrorPackets.rejected(sequence,code)),null,false,0);}
    public StopTriplet display(){return display;}
    public int stoppedMask(){return history.mask();}
    public SkillStopHistory history(){return history;}
    public SkillStopControl.Outcome outcome(){return control.outcome(context,history);}
}
