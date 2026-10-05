package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.game.pachinko.PachinkoGameEngine;
import jp.pirijuggler.paper.game.pachinko.PachinkoRuntime;
import jp.pirijuggler.paper.game.pachinko.PachinkoBallAccounting;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PachinkoGameEngineTest extends GameFixture {
    @Test void dedicatedActionUsesDurableGameStorePathAndPreservesRuntime() throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",20,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        UUID player=UUID.randomUUID();
        db.seat(player,id,NOW);
        var before=db.state().session(player);
        var machine=db.state().machine(id);
        var engine=new PachinkoGameEngine();

        var transition=engine.plan(before,machine,PacketType.PACHINKO_FIRE,before.sequence()+1,NOW+1,1_000_000_000L,0,null);
        var after=store.commit(transition);

        assertEquals(before.sequence()+1,after.sequence());
        assertNotNull(after.machineState());
        assertEquals(PachinkoRuntime.initial(),PachinkoRuntime.fromJson(after.machineState().toString()));
        assertEquals(PachinkoRuntime.initial(),PachinkoRuntime.fromJson(db.state().machine(id).runtimeJson()));
        assertEquals(PacketType.ACTION_REJECTED,engine.committed(transition,0).getFirst().packetType());
    }

    @Test void slotActionIsNeverReinterpretedAsPachinkoAction() throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",21,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        UUID player=UUID.randomUUID();
        db.seat(player,id,NOW);
        var before=db.state().session(player);
        var engine=new PachinkoGameEngine();

        var transition=engine.plan(before,db.state().machine(id),PacketType.SPACE_ACTION,before.sequence()+1,NOW+1,1_000_000_000L,0,null);

        assertEquals(PacketType.ACTION_REJECTED,transition.packets().getFirst().packetType());
        assertEquals(0,transition.bet());
        assertEquals(0,transition.payout());
    }
    @Test void reconnectReconstructsSameCommittedLeftKurunEvent() throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",23,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        UUID player=UUID.randomUUID();db.seat(player,id,NOW);
        var r=PachinkoBallAccounting.validStart(PachinkoBallAccounting.fire(PachinkoBallAccounting.lend(PachinkoRuntime.initial(),NOW),NOW+1),NOW+2);
        r=new PachinkoRuntime(r.mode(),r.ballsHeld(),r.ballsLoaned(),r.totalFired(),r.totalStarts(),r.ballSequenceId(),
                PachinkoRuntime.Presentation.LEFT_KURUN,false,r.initialOutcome(),r.rushActive(),r.rushWins(),r.currentPayout(),r.cumulativePayout(),NOW+2);
        db.sql("UPDATE player_sessions SET machine_state_json=? WHERE player_uuid=?",r.toJsonString(),player.toString());
        var saved=db.state().session(player);var engine=new PachinkoGameEngine();
        var first=engine.resume(saved,0).orElseThrow();var second=engine.resume(saved,99).orElseThrow();
        assertEquals(PacketType.PACHINKO_EVENT,first.packetType());
        assertEquals(first.payload(),second.payload());
        assertEquals("OUT",first.payload().get("outcome").getAsString());
        assertEquals(r.ballSequenceId(),first.payload().get("ballSequenceId").getAsLong());
    }

}
