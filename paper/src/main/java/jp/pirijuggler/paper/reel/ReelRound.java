package jp.pirijuggler.paper.reel;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.action.ActionGate;
import jp.pirijuggler.paper.threading.MainThread;
import java.util.*;

/** Server-owned reel round. Draw, BET and financial persistence belong to its game controller. */
public final class ReelRound {
    public record Identity(UUID owner,UUID session,int machine,UUID spin){public Identity {Objects.requireNonNull(owner);Objects.requireNonNull(session);Objects.requireNonNull(spin);if(machine<1)throw new IllegalArgumentException("Machine ID");}}
    public record Result(List<Envelope> packets,StopSolver.Choice choice,boolean tenpaiSound,int actualTenpaiLines){public Result {packets=List.copyOf(packets);}public boolean accepted(){return choice!=null;}}
    private final Identity identity;private final StopSolver solver;private final DisplayRole role;private final DisplayRole alternateRole;private final boolean premiumF;private final boolean allowBarConfirmation;
    private final boolean forbidRightFirstGrapeSevenBar;
    private final ReelMotion.Profile profile;private final String mode;private final double[] starts;private final MainThread main;private final ActionGate gate;
    private StopTriplet display;private int stoppedMask;private Long motionStartNanos;
    public ReelRound(StopSolver solver,Identity identity,DisplayRole role,boolean premiumF,ReelMotion.Profile profile,String mode,double[] starts,StopTriplet display,int stoppedMask,long lastSequence,MainThread main){
        this(solver,identity,role,null,premiumF,premiumF||role==DisplayRole.PREMIUM_B,profile,mode,starts,display,stoppedMask,lastSequence,main,defaultRightFirstTellRestriction(role,mode));
    }
    public ReelRound(StopSolver solver,Identity identity,DisplayRole role,boolean premiumF,ReelMotion.Profile profile,String mode,double[] starts,StopTriplet display,int stoppedMask,long lastSequence,MainThread main,boolean forbidRightFirstGrapeSevenBar){
        this(solver,identity,role,null,premiumF,premiumF||role==DisplayRole.PREMIUM_B,profile,mode,starts,display,stoppedMask,lastSequence,main,forbidRightFirstGrapeSevenBar);
    }
    public ReelRound(StopSolver solver,Identity identity,DisplayRole role,DisplayRole alternateRole,boolean premiumF,ReelMotion.Profile profile,String mode,double[] starts,StopTriplet display,int stoppedMask,long lastSequence,MainThread main){
        this(solver,identity,role,alternateRole,premiumF,premiumF||role==DisplayRole.PREMIUM_B,profile,mode,starts,display,stoppedMask,lastSequence,main,defaultRightFirstTellRestriction(role,mode));
    }
    public ReelRound(StopSolver solver,Identity identity,DisplayRole role,DisplayRole alternateRole,boolean premiumF,boolean allowBarConfirmation,ReelMotion.Profile profile,String mode,double[] starts,StopTriplet display,int stoppedMask,long lastSequence,MainThread main){
        this(solver,identity,role,alternateRole,premiumF,allowBarConfirmation,profile,mode,starts,display,stoppedMask,lastSequence,main,defaultRightFirstTellRestriction(role,mode));
    }
    public ReelRound(StopSolver solver,Identity identity,DisplayRole role,DisplayRole alternateRole,boolean premiumF,ReelMotion.Profile profile,String mode,double[] starts,StopTriplet display,int stoppedMask,long lastSequence,MainThread main,boolean forbidRightFirstGrapeSevenBar){
        this(solver,identity,role,alternateRole,premiumF,premiumF||role==DisplayRole.PREMIUM_B,profile,mode,starts,display,stoppedMask,lastSequence,main,forbidRightFirstGrapeSevenBar);
    }
    public ReelRound(StopSolver solver,Identity identity,DisplayRole role,DisplayRole alternateRole,boolean premiumF,boolean allowBarConfirmation,ReelMotion.Profile profile,String mode,double[] starts,StopTriplet display,int stoppedMask,long lastSequence,MainThread main,boolean forbidRightFirstGrapeSevenBar){
        this.solver=Objects.requireNonNull(solver);this.identity=identity;this.role=role;this.alternateRole=alternateRole;this.premiumF=premiumF;this.allowBarConfirmation=allowBarConfirmation;this.profile=profile;this.mode=mode;this.main=main;this.forbidRightFirstGrapeSevenBar=forbidRightFirstGrapeSevenBar;main.requireMainThread();
        if(!Set.of("NORMAL","BONUS_ENTRY","BIG","REG").contains(mode)||starts.length!=3||stoppedMask<0||stoppedMask>7)throw new IllegalArgumentException("Invalid round state");
        this.starts=starts.clone();for(double phase:starts)if(!Double.isFinite(phase)||phase<0||phase>=21)throw new IllegalArgumentException("Start phase");
        if(premiumF&&role!=DisplayRole.BONUS&&role!=DisplayRole.BONUS_CHERRY&&role!=DisplayRole.PIERO_BONUS)throw new IllegalArgumentException("Invalid premium F base");
        if(premiumF&&alternateRole!=null)throw new IllegalArgumentException("Premium F cannot use direct-entry alternatives");
        if(solver.catalogue().candidates(role,stoppedMask,display).isEmpty()&&(alternateRole==null||solver.catalogue().candidates(alternateRole,stoppedMask,display).isEmpty()))throw new IllegalArgumentException("Unreachable resumed stops");
        this.display=display;this.stoppedMask=stoppedMask;gate=new ActionGate(main,lastSequence);
    }
    private static boolean defaultRightFirstTellRestriction(DisplayRole role,String mode){
        if(!"NORMAL".equals(mode))return false;
        return switch(role){case GRAPE,BONUS,BONUS_CHERRY,PIERO_BONUS,PREMIUM_B->false;default->true;};
    }
    private boolean bonusAwardGame(){
        return "NORMAL".equals(mode)&&alternateRole!=null;
    }
    /** Call immediately before sending this envelope, using System.nanoTime on Paper's thread. */
    public Envelope begin(long now){
        main.requireMainThread();if(motionStartNanos!=null)throw new IllegalStateException("Round already began");motionStartNanos=now;
        JsonObject b=new JsonObject();b.addProperty("sessionId",identity.session.toString());b.addProperty("machineId",identity.machine);b.addProperty("spinId",identity.spin.toString());b.addProperty("mode",mode);b.addProperty("animation",profile.name());
        JsonObject phases=new JsonObject();for(var reel:Reel.values())phases.addProperty(reel.name().toLowerCase(Locale.ROOT),starts[reel.ordinal()]);b.add("startPhase",phases);b.addProperty("stopEnableAfterMs",profile.clientDelayMs());b.add("stopHints",stopHints());return Envelope.current(PacketType.SPIN_START,b);
    }
    public Result receive(UUID owner,Envelope input,long receivedNanos,int playerPing){
        main.requireMainThread();long sequence=-1;JsonObject b=input.payload();
        try {
            if(input.protocol()!=Protocol.VERSION)return rejected(sequence,ErrorCode.PROTOCOL_MISMATCH);
            var keys=new HashSet<>(b.keySet());boolean hasPressed=keys.remove("pressedIndex");
            if(!keys.equals(Set.of("sessionId","machineId","clientSequence")))return rejected(sequence,ErrorCode.SESSION_MISMATCH);
            sequence=b.get("clientSequence").getAsBigDecimal().longValueExact();
            if(hasPressed){int p=b.get("pressedIndex").getAsBigDecimal().intValueExact();if(p<0||p>=21)return rejected(sequence,ErrorCode.SESSION_MISMATCH);}
            if(!identity.owner.equals(owner)||!identity.session.toString().equals(b.get("sessionId").getAsString())||identity.machine!=b.get("machineId").getAsBigDecimal().intValueExact())return rejected(sequence,ErrorCode.SESSION_MISMATCH);
        }catch(RuntimeException malformed){return rejected(sequence,ErrorCode.SESSION_MISMATCH);}
        var decision=gate.acceptStop(sequence,identity.spin,motionStartNanos==null?null:identity.spin);if(decision.error()!=null)return rejected(sequence,decision.error());
        Reel reel=switch(input.packetType()){case STOP_LEFT->Reel.LEFT;case STOP_CENTER->Reel.CENTER;case STOP_RIGHT->Reel.RIGHT;case SPACE_ACTION->nextReel();default->null;};
        if(reel==null)return rejected(sequence,ErrorCode.INVALID_STATE);
        if((stoppedMask&reel.bit())!=0)return rejected(sequence,ErrorCode.ALREADY_STOPPED);
        double elapsed=ReelMotion.effectiveMillis(motionStartNanos,receivedNanos,playerPing);
        if(elapsed<profile.serverThresholdMs())return rejected(sequence,ErrorCode.STOP_TOO_EARLY);
        try(var lease=gate.beginBusy()){
            int pressed=b.has("pressedIndex")?b.get("pressedIndex").getAsInt():ReelMotion.pressedIndex(profile,starts[reel.ordinal()],motionStartNanos,receivedNanos,playerPing);
            var choice=solver.choose(role,alternateRole,stoppedMask,display,reel,pressed,premiumF,allowBarConfirmation,forbidRightFirstGrapeSevenBar,bonusAwardGame());display=display.with(reel,choice.stopIndex());stoppedMask|=reel.bit();
            int tenpai=Integer.bitCount(stoppedMask)==2?StopCatalogue.sevenTenpaiLines(display,stoppedMask):0;
            boolean sound=Integer.bitCount(stoppedMask)==2&&(premiumF||tenpai>0);
            if(premiumF&&Integer.bitCount(stoppedMask)==2&&tenpai!=0)throw new IllegalStateException("Premium F unexpectedly has SEVEN tenpai");
            JsonObject accepted=new JsonObject();accepted.addProperty("clientSequence",sequence);accepted.addProperty("action",input.packetType().name());
            JsonObject stop=new JsonObject();stop.addProperty("spinId",identity.spin.toString());stop.addProperty("reel",reel.name());stop.addProperty("pressedIndex",pressed);stop.addProperty("stopIndex",choice.stopIndex());stop.addProperty("slip",choice.slip());stop.addProperty("durationMs",choice.durationMs());stop.add("nextStopHints",stopHints());
            return new Result(List.of(Envelope.current(PacketType.ACTION_ACCEPTED,accepted),Envelope.current(PacketType.REEL_STOP,stop)),choice,sound,tenpai);
        }
    }
    /** Authoritative per-pressed-index choices for the current partial reel state. */
    private JsonObject stopHints(){
        JsonObject all=new JsonObject();
        for(var reel:Reel.values()){
            if((stoppedMask&reel.bit())!=0)continue;
            JsonArray choices=new JsonArray();
            for(int pressed=0;pressed<21;pressed++){
                var choice=solver.choose(role,alternateRole,stoppedMask,display,reel,pressed,premiumF,allowBarConfirmation,forbidRightFirstGrapeSevenBar,bonusAwardGame());JsonObject item=new JsonObject();
                item.addProperty("stopIndex",choice.stopIndex());item.addProperty("slip",choice.slip());item.addProperty("durationMs",choice.durationMs());choices.add(item);
            }
            all.add(reel.name().toLowerCase(Locale.ROOT),choices);
        }
        return all;
    }
    private Reel nextReel(){for(var reel:Reel.values())if((stoppedMask&reel.bit())==0)return reel;return null;}
    private Result rejected(long sequence,ErrorCode code){return new Result(List.of(ErrorPackets.rejected(sequence,code)),null,false,0);}
    public StopTriplet display(){main.requireMainThread();return display;}public int stoppedMask(){main.requireMainThread();return stoppedMask;}public long lastSequence(){main.requireMainThread();return gate.lastClientSequence();}
    public Identity identity(){return identity;}
}
