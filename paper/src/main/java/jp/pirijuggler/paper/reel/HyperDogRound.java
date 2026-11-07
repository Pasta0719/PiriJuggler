package jp.pirijuggler.paper.reel;

import jp.pirijuggler.common.reel.*;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-authoritative HYPER_DOG stop state.
 * A server-driven automatic stop NEVER consumes the player's client sequence.
 */
public final class HyperDogRound {
    public record Snapshot(UUID spinId, HyperDogReelControl.Role role, SkillStopHistory history, long lastSequence) {
        public Snapshot {
            Objects.requireNonNull(spinId); Objects.requireNonNull(role); Objects.requireNonNull(history);
        }
    }
    public record StopResult(HyperDogReelControl.Choice choice, int[] middleStops, boolean complete, long sequence) {
        public StopResult { middleStops=middleStops.clone(); }
        @Override public int[] middleStops(){return middleStops.clone();}
    }

    private final UUID spinId;
    private final HyperDogReelControl.Role role;
    private final HyperDogReelControl controller=new HyperDogReelControl();
    private SkillStopHistory history;
    private long lastSequence;

    public HyperDogRound(UUID spinId, HyperDogReelControl.Role role) {
        this(spinId,role,SkillStopHistory.empty(),-1);
    }
    private HyperDogRound(UUID spinId,HyperDogReelControl.Role role,SkillStopHistory history,long sequence) {
        this.spinId=Objects.requireNonNull(spinId);
        this.role=Objects.requireNonNull(role);
        this.history=Objects.requireNonNull(history);
        if(sequence<-1 || history.count()>3)throw new IllegalArgumentException("invalid snapshot");
        if(history.count()==3 && !HyperDogReelControl.legalFinal(role,history.stops()))
            throw new IllegalArgumentException("illegal saved stops");
        this.lastSequence=sequence;
    }
    public static HyperDogRound restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        return new HyperDogRound(snapshot.spinId(),snapshot.role(),snapshot.history(),snapshot.lastSequence());
    }
    public Snapshot snapshot() {return new Snapshot(spinId,role,history,lastSequence);}
    public boolean complete() {return history.count()==3;}
    public HyperDogReelControl.Role role() {return role;}
    public UUID spinId() {return spinId;}
    public int stoppedMask(){return history.mask();}

    public StopResult stopManual(Reel reel,int pressed,long sequence) {
        Objects.requireNonNull(reel);
        if(sequence<=lastSequence)throw new IllegalArgumentException("stale or duplicate client sequence");
        if(role==HyperDogReelControl.Role.AT_ENTRY_RED7)
            throw new IllegalStateException("AT red-seven presentation is automatic");
        if(isCz() && (reel!=Reel.CENTER ||
             (history.mask()&(Reel.LEFT.bit()|Reel.RIGHT.bit()))!=(Reel.LEFT.bit()|Reel.RIGHT.bit())))
            throw new IllegalStateException("CZ requires auto-stopped outer reels");
        return doStop(reel,pressed,sequence);
    }

    /** Paper-only automatic stop; does not advance or consume a client sequence. */
    public StopResult stopAutomatic(Reel reel,int pressed) {
        Objects.requireNonNull(reel);
        if(role==HyperDogReelControl.Role.AT_ENTRY_RED7)return doStop(reel,pressed,null);
        if(isCz()) {
            if(reel==Reel.LEFT && history.count()==0)return doStop(reel,pressed,null);
            if(reel==Reel.RIGHT && history.count()==1 && (history.mask()&Reel.LEFT.bit())!=0)
                return doStop(reel,pressed,null);
        }
        throw new IllegalStateException("automatic stop forbidden");
    }

    private boolean isCz() {return role==HyperDogReelControl.Role.CZ_WIN||role==HyperDogReelControl.Role.CZ_LOSE;}
    private StopResult doStop(Reel reel,int pressed,Long clientSequence) {
        if(complete())throw new IllegalStateException("spin already complete");
        var choice=controller.choose(role,history,reel,pressed);
        history=history.append(reel,pressed,choice.stopIndex());
        if(clientSequence!=null)lastSequence=clientSequence;
        if(complete() && !HyperDogReelControl.legalFinal(role,history.stops()))
            throw new AssertionError("invalid reel outcome");
        return new StopResult(choice,history.stops(),complete(),lastSequence);
    }
}
