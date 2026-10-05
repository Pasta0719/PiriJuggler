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

/**
 * Durable protocol boundary for the pachinko family.
 *
 * Phase 02 deliberately does not consume balls or resolve starts. Those rules are
 * introduced in Phase 03+. Until then, pachinko-specific requests are rejected
 * through the normal durable GameStore path rather than being interpreted as slot
 * BET/LEVER/reel-stop actions.
 */
public final class PachinkoGameEngine implements GameEngine {
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
        if(action!=PacketType.PACHINKO_FIRE)return rejected(before,machine,sequence,now,ErrorCode.INVALID_STATE);
        try {
            PachinkoRuntime fired=PachinkoBallAccounting.fire(runtime,now);
            return accepted(before,fired,action,sequence);
        } catch(IllegalStateException invalid) {
            return rejected(before,machine,sequence,now,ErrorCode.INVALID_STATE);
        }
    }

    private static PachinkoRuntime runtime(Session before,Machine machine){
        return PachinkoRuntime.fromJson(before.machineState()!=null?before.machineState().toString():machine.runtimeJson());
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
        return Optional.empty();
    }

    @Override public Session capture(Session saved,long now){
        return saved;
    }

    @Override public void forget(UUID session){}
}
