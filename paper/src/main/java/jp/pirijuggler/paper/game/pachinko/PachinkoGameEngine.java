package jp.pirijuggler.paper.game.pachinko;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.ErrorCode;
import jp.pirijuggler.common.protocol.ErrorPackets;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.game.GameEngine;
import jp.pirijuggler.paper.game.GameTransition;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.session.Session;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Durable protocol boundary for the pachinko family.
 *
 * Phase 02 deliberately does not consume balls or resolve starts. Those rules are
 * introduced in Phase 03+. Until then, pachinko-specific requests are rejected
 * through the normal durable GameStore path rather than being interpreted as slot
 * BET/LEVER/reel-stop actions.
 */
public final class PachinkoGameEngine implements GameEngine {
    private final RandomGenerator random;
    private final PachinkoRouting routing;

    public PachinkoGameEngine(){this(new SplittableRandom(),PachinkoRouting.reference());}
    public PachinkoGameEngine(RandomGenerator random){this(random,PachinkoRouting.reference());}
    public PachinkoGameEngine(RandomGenerator random,PachinkoRouting routing){
        this.random=java.util.Objects.requireNonNull(random);
        this.routing=java.util.Objects.requireNonNull(routing);
    }

    private static final Set<PacketType> PACHINKO_ACTIONS=Set.of(
            PacketType.PACHINKO_FIRE,
            PacketType.PACHINKO_START,
            PacketType.PACHINKO_PRESENTATION
    );

    @Override
    public GameTransition plan(Session before, Machine machine, PacketType action, long sequence,
                               long now, long receivedNanos, int ping, Integer clientPressedIndex) {
        if(before.lifecycle()!=Session.Lifecycle.ACTIVE)throw new DomainException("SESSION_MISMATCH");
        if(sequence<=before.sequence())throw new DomainException("SEQUENCE_OLD");
        if(!PACHINKO_ACTIONS.contains(action))return rejected(before,machine,sequence,now,ErrorCode.INVALID_STATE);
        PachinkoRuntime runtime=runtime(before,machine);
        if(action==PacketType.PACHINKO_PRESENTATION){
            if(runtime.presentation()==PachinkoRuntime.Presentation.RIGHT_KURUN)
                return resolveRightPresentation(before,runtime,sequence,machine.id(),now);
            if(runtime.presentation()!=PachinkoRuntime.Presentation.LEFT_KURUN)
                return rejected(before,machine,sequence,now,ErrorCode.INVALID_STATE);
            if(runtime.initialHitCommitted())return initialPayout(before,runtime,sequence,machine.id(),now);
            PachinkoRuntime idle=new PachinkoRuntime(runtime.mode(),runtime.ballsHeld(),runtime.ballsLoaned(),runtime.totalFired(),
                    runtime.totalStarts(),runtime.ballSequenceId(),PachinkoRuntime.Presentation.IDLE,false,PachinkoRuntime.InitialOutcome.NONE,
                    runtime.rushActive(),runtime.rushWins(),runtime.rightOutcome(),runtime.currentPayout(),runtime.cumulativePayout(),now);
            return accepted(before,idle,action,sequence);
        }
        if(action!=PacketType.PACHINKO_FIRE)return rejected(before,machine,sequence,now,ErrorCode.INVALID_STATE);
        if(runtime.rushActive() && runtime.mode()==PachinkoRuntime.Mode.RUSH && runtime.presentation()==PachinkoRuntime.Presentation.IDLE)
            return startRightKurun(before,runtime,sequence,machine.id(),now);
        if(runtime.mode()!=PachinkoRuntime.Mode.NORMAL || runtime.presentation()!=PachinkoRuntime.Presentation.IDLE)
            return rejected(before,machine,sequence,now,ErrorCode.INVALID_STATE);
        if(runtime.ballsHeld()<1)return rejected(before,machine,sequence,now,ErrorCode.INVALID_STATE);
        try {
            PachinkoRuntime fired=PachinkoBallAccounting.fire(runtime,now);
            if(!routing.entersStart(random))return accepted(before,fired,action,sequence);
            PachinkoRuntime started=PachinkoBallAccounting.validStart(fired,now);
            boolean v=rollInitialV(random);
            PachinkoRuntime presenting=new PachinkoRuntime(
                    started.mode(),started.ballsHeld(),started.ballsLoaned(),started.totalFired(),started.totalStarts(),
                    started.ballSequenceId(),PachinkoRuntime.Presentation.LEFT_KURUN,v,PachinkoRuntime.InitialOutcome.NONE,
                    started.rushActive(),started.rushWins(),started.rightOutcome(),started.currentPayout(),started.cumulativePayout(),now);
            return acceptedStart(before,presenting,sequence,machine.id(),v);
        } catch(IllegalStateException invalid) {
            return rejected(before,machine,sequence,now,ErrorCode.INVALID_STATE);
        }
    }

    private static PachinkoRuntime runtime(Session before,Machine machine){
        return PachinkoRuntime.fromJson(before.machineState()!=null?before.machineState().toString():machine.runtimeJson());
    }

    private GameTransition initialPayout(Session before,PachinkoRuntime runtime,long sequence,int machineId,long now){
        return initialPayout(before,runtime,sequence,machineId,now,rollRushEntry(random));
    }

    public GameTransition initialPayout(Session before,PachinkoRuntime runtime,long sequence,int machineId,long now,boolean rush){
        int payout=rush?PachinkoSpec.RUSH_INITIAL_PAYOUT:PachinkoSpec.NORMAL_INITIAL_PAYOUT;
        PachinkoRuntime.InitialOutcome outcome=rush?PachinkoRuntime.InitialOutcome.RUSH_1500:PachinkoRuntime.InitialOutcome.NORMAL_450;
        PachinkoRuntime paid=new PachinkoRuntime(rush?PachinkoRuntime.Mode.RUSH:PachinkoRuntime.Mode.NORMAL,
                Math.addExact(runtime.ballsHeld(),payout),runtime.ballsLoaned(),runtime.totalFired(),runtime.totalStarts(),
                runtime.ballSequenceId(),PachinkoRuntime.Presentation.IDLE,true,outcome,rush,runtime.rushWins(),runtime.rightOutcome(),
                payout,Math.addExact(runtime.cumulativePayout(),payout),now);
        var values=new LinkedHashMap<>(before.snapshot());values.put("last_client_sequence",sequence);
        values.put("last_activity",now);values.put("machine_state_json",paid.toJsonString());Session after=new Session(values);
        JsonObject accepted=new JsonObject();accepted.addProperty("clientSequence",sequence);accepted.addProperty("action",PacketType.PACHINKO_PRESENTATION.name());
        JsonObject event=new JsonObject();event.addProperty("machineId",machineId);event.addProperty("ballSequenceId",runtime.ballSequenceId());
        event.addProperty("side","INITIAL_PAYOUT");event.addProperty("outcome",outcome.name());event.addProperty("payout",payout);
        event.addProperty("rush",rush);event.addProperty("startTime",now);
        return new GameTransition(UUID.randomUUID(),before,after,0,0,0,false,false,null,false,0,
                List.of(Envelope.current(PacketType.ACTION_ACCEPTED,accepted),Envelope.current(PacketType.PACHINKO_EVENT,event)),
                List.of(),List.of(),paid.toJsonString());
    }

    private static GameTransition acceptedStart(Session before,PachinkoRuntime runtime,long sequence,int machineId,boolean v){
        var values=new LinkedHashMap<>(before.snapshot());
        values.put("last_client_sequence",sequence);
        values.put("last_activity",runtime.lastActivity());
        values.put("machine_state_json",runtime.toJsonString());
        Session after=new Session(values);
        long seed=presentationSeed(before.id(),machineId,runtime.ballSequenceId());
        JsonObject accepted=new JsonObject();accepted.addProperty("clientSequence",sequence);accepted.addProperty("action",PacketType.PACHINKO_FIRE.name());
        JsonObject event=new JsonObject();
        event.addProperty("machineId",machineId);event.addProperty("ballSequenceId",runtime.ballSequenceId());
        event.addProperty("side","LEFT");event.addProperty("outcome",v?"V":"OUT");
        event.addProperty("seed",seed);event.addProperty("startTime",runtime.lastActivity());
        return new GameTransition(UUID.randomUUID(),before,after,0,0,0,false,false,null,false,0,
                List.of(Envelope.current(PacketType.ACTION_ACCEPTED,accepted),Envelope.current(PacketType.PACHINKO_EVENT,event)),
                List.of(),List.of(),runtime.toJsonString());
    }


    private GameTransition startRightKurun(Session before,PachinkoRuntime runtime,long sequence,int machineId,long now){
        boolean win=rollRushContinuation(random);
        PachinkoRuntime.RightOutcome outcome=win?(rollRight3000(random)?PachinkoRuntime.RightOutcome.WIN_3000:PachinkoRuntime.RightOutcome.WIN_1500):PachinkoRuntime.RightOutcome.OUT;
        PachinkoRuntime pending=new PachinkoRuntime(PachinkoRuntime.Mode.RUSH,runtime.ballsHeld(),runtime.ballsLoaned(),runtime.totalFired(),runtime.totalStarts(),
                runtime.ballSequenceId()+1,PachinkoRuntime.Presentation.RIGHT_KURUN,runtime.initialHitCommitted(),runtime.initialOutcome(),true,runtime.rushWins(),outcome,0,runtime.cumulativePayout(),now);
        return acceptedRight(before,pending,sequence,machineId);
    }

    private GameTransition resolveRightPresentation(Session before,PachinkoRuntime runtime,long sequence,int machineId,long now){
        if(runtime.rightOutcome()==PachinkoRuntime.RightOutcome.OUT){
            PachinkoRuntime ended=new PachinkoRuntime(PachinkoRuntime.Mode.NORMAL,runtime.ballsHeld(),runtime.ballsLoaned(),runtime.totalFired(),runtime.totalStarts(),runtime.ballSequenceId(),
                    PachinkoRuntime.Presentation.IDLE,runtime.initialHitCommitted(),runtime.initialOutcome(),false,runtime.rushWins(),PachinkoRuntime.RightOutcome.NONE,0,runtime.cumulativePayout(),now);
            return accepted(before,ended,PacketType.PACHINKO_PRESENTATION,sequence);
        }
        int payout=runtime.rightOutcome()==PachinkoRuntime.RightOutcome.WIN_3000?PachinkoSpec.RIGHT_3000_PAYOUT:PachinkoSpec.RIGHT_1500_PAYOUT;
        PachinkoRuntime paid=new PachinkoRuntime(PachinkoRuntime.Mode.RUSH,Math.addExact(runtime.ballsHeld(),payout),runtime.ballsLoaned(),runtime.totalFired(),runtime.totalStarts(),runtime.ballSequenceId(),
                PachinkoRuntime.Presentation.IDLE,runtime.initialHitCommitted(),runtime.initialOutcome(),true,runtime.rushWins()+1,PachinkoRuntime.RightOutcome.NONE,payout,Math.addExact(runtime.cumulativePayout(),payout),now);
        return accepted(before,paid,PacketType.PACHINKO_PRESENTATION,sequence);
    }

    private static GameTransition acceptedRight(Session before,PachinkoRuntime runtime,long sequence,int machineId){
        var values=new LinkedHashMap<>(before.snapshot());values.put("last_client_sequence",sequence);values.put("last_activity",runtime.lastActivity());values.put("machine_state_json",runtime.toJsonString());
        Session after=new Session(values);JsonObject accepted=new JsonObject();accepted.addProperty("clientSequence",sequence);accepted.addProperty("action",PacketType.PACHINKO_FIRE.name());
        JsonObject event=new JsonObject();event.addProperty("machineId",machineId);event.addProperty("ballSequenceId",runtime.ballSequenceId());event.addProperty("side","RIGHT");
        event.addProperty("outcome",runtime.rightOutcome().name());event.addProperty("seed",presentationSeed(before.id(),machineId,runtime.ballSequenceId()));event.addProperty("startTime",runtime.lastActivity());
        return new GameTransition(UUID.randomUUID(),before,after,0,0,0,false,false,null,false,0,List.of(Envelope.current(PacketType.ACTION_ACCEPTED,accepted),Envelope.current(PacketType.PACHINKO_EVENT,event)),List.of(),List.of(),runtime.toJsonString());
    }

    static boolean rollRushContinuation(RandomGenerator random){return random.nextDouble()<PachinkoSpec.RUSH_CONTINUATION_RATE;}
    static boolean rollRight3000(RandomGenerator random){return random.nextDouble()<PachinkoSpec.RIGHT_3000_RATE;}

    static boolean rollRushEntry(RandomGenerator random){return random.nextDouble()<PachinkoSpec.RUSH_ENTRY_RATE;}

    static boolean rollInitialV(RandomGenerator random){return random.nextInt((int)PachinkoSpec.INITIAL_JACKPOT_DENOMINATOR)==0;}

    public static long presentationSeed(UUID session,int machine,long ballSequence){
        long x=session.getMostSignificantBits()^session.getLeastSignificantBits()^((long)machine<<32)^ballSequence;
        x^=x>>>33;x*=0xff51afd7ed558ccdl;x^=x>>>33;x*=0xc4ceb9fe1a85ec53l;x^=x>>>33;return x;
    }

    private static GameTransition accepted(Session before,PachinkoRuntime runtime,PacketType action,long sequence){
        var values=new LinkedHashMap<>(before.snapshot());
        values.put("last_client_sequence",sequence);
        values.put("last_activity",runtime.lastActivity());
        values.put("machine_state_json",runtime.toJsonString());
        Session after=new Session(values);
        JsonObject body=new JsonObject();
        body.addProperty("clientSequence",sequence);
        body.addProperty("action",action.name());
        return new GameTransition(UUID.randomUUID(),before,after,0,0,0,false,false,null,false,0,
                List.of(Envelope.current(PacketType.ACTION_ACCEPTED,body)),List.of(),List.of(),runtime.toJsonString());
    }

    private static GameTransition rejected(Session before,Machine machine,long sequence,long now,ErrorCode code){
        PachinkoRuntime runtime=runtime(before,machine);
        var values=new LinkedHashMap<>(before.snapshot());
        values.put("last_client_sequence",sequence);
        values.put("last_activity",now);
        values.put("machine_state_json",runtime.toJsonString());
        Session after=new Session(values);
        return new GameTransition(
                UUID.randomUUID(),before,after,
                0,0,0,false,false,null,false,0,
                List.of(ErrorPackets.rejected(sequence,code)),
                List.of(),List.of(),runtime.toJsonString()
        );
    }

    @Override public List<Envelope> committed(GameTransition action,long sentNanos){
        return List.copyOf(action.packets());
    }

    @Override public List<GameTransition.Scheduled> scheduled(GameTransition action){
        return List.copyOf(action.scheduled());
    }

    @Override public Optional<Envelope> resume(Session saved,long sentNanos){
        PachinkoRuntime runtime=PachinkoRuntime.fromJson(saved.machineState()!=null?saved.machineState().toString():null);
        if(runtime.presentation()!=PachinkoRuntime.Presentation.LEFT_KURUN && runtime.presentation()!=PachinkoRuntime.Presentation.RIGHT_KURUN)return Optional.empty();
        JsonObject event=new JsonObject();
        event.addProperty("machineId",saved.machine());event.addProperty("ballSequenceId",runtime.ballSequenceId());
        boolean right=runtime.presentation()==PachinkoRuntime.Presentation.RIGHT_KURUN;
        event.addProperty("side",right?"RIGHT":"LEFT");event.addProperty("outcome",right?runtime.rightOutcome().name():(runtime.initialHitCommitted()?"V":"OUT"));
        event.addProperty("seed",presentationSeed(saved.id(),saved.machine(),runtime.ballSequenceId()));
        event.addProperty("startTime",runtime.lastActivity());
        return Optional.of(Envelope.current(PacketType.PACHINKO_EVENT,event));
    }

    @Override public Session capture(Session saved,long now){
        return saved;
    }

    @Override public void forget(UUID session){}
}
