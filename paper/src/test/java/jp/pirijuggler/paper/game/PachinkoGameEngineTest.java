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
                PachinkoRuntime.Presentation.LEFT_KURUN,false,r.initialOutcome(),r.rushActive(),r.rushWins(),r.rightOutcome(),r.currentPayout(),r.cumulativePayout(),NOW+2);
        db.sql("UPDATE player_sessions SET machine_state_json=? WHERE player_uuid=?",r.toJsonString(),player.toString());
        var saved=db.state().session(player);var engine=new PachinkoGameEngine();
        var first=engine.resume(saved,0).orElseThrow();var second=engine.resume(saved,99).orElseThrow();
        assertEquals(PacketType.PACHINKO_EVENT,first.packetType());
        assertEquals(first.payload(),second.payload());
        assertEquals("OUT",first.payload().get("outcome").getAsString());
        assertEquals(r.ballSequenceId(),first.payload().get("ballSequenceId").getAsLong());
    }

    @Test void committedInitialPayoutCannotBeDuplicated() throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",24,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        UUID player=UUID.randomUUID();db.seat(player,id,NOW);
        var r=PachinkoBallAccounting.validStart(PachinkoBallAccounting.fire(PachinkoBallAccounting.lend(PachinkoRuntime.initial(),NOW),NOW+1),NOW+2);
        r=new PachinkoRuntime(r.mode(),r.ballsHeld(),r.ballsLoaned(),r.totalFired(),r.totalStarts(),r.ballSequenceId(),
                PachinkoRuntime.Presentation.LEFT_KURUN,true,PachinkoRuntime.InitialOutcome.NONE,false,0,PachinkoRuntime.RightOutcome.NONE,0,0,NOW+2);
        db.sql("UPDATE player_sessions SET machine_state_json=? WHERE player_uuid=?",r.toJsonString(),player.toString());
        var engine=new PachinkoGameEngine(new java.util.Random(7));
        var before=db.state().session(player);var first=engine.plan(before,db.state().machine(id),PacketType.PACHINKO_PRESENTATION,1,NOW+3,0,0,null);
        var paid=store.commit(first);var paidRuntime=PachinkoRuntime.fromJson(paid.machineState().toString());
        assertTrue(paidRuntime.currentPayout()==450||paidRuntime.currentPayout()==1500);
        assertEquals(paidRuntime.currentPayout(),paidRuntime.cumulativePayout());
        long heldAfter=paidRuntime.ballsHeld();
        var duplicate=engine.plan(paid,db.state().machine(id),PacketType.PACHINKO_PRESENTATION,2,NOW+4,0,0,null);
        var afterDuplicate=store.commit(duplicate);var finalRuntime=PachinkoRuntime.fromJson(afterDuplicate.machineState().toString());
        assertEquals(heldAfter,finalRuntime.ballsHeld());
        assertEquals(paidRuntime.cumulativePayout(),finalRuntime.cumulativePayout());
        assertEquals(PacketType.ACTION_REJECTED,duplicate.packets().getFirst().packetType());
    }

    @Test void validStartCommitsOneDeterministicLeftKurunEventBeforeAnyPayout() throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",25,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        UUID player=UUID.randomUUID();db.seat(player,id,NOW);
        var loaned=PachinkoBallAccounting.lend(PachinkoRuntime.initial(),NOW);
        db.sql("UPDATE player_sessions SET machine_state_json=? WHERE player_uuid=?",loaned.toJsonString(),player.toString());
        var before=db.state().session(player);
        var alwaysStart=new jp.pirijuggler.paper.game.pachinko.PachinkoRouting(250);
        var engine=new PachinkoGameEngine(new java.util.Random(11),alwaysStart);
        var transition=engine.plan(before,db.state().machine(id),PacketType.PACHINKO_FIRE,1,NOW+1,0,0,null);
        var saved=store.commit(transition);var runtime=PachinkoRuntime.fromJson(saved.machineState().toString());
        assertEquals(1,runtime.totalFired());assertEquals(1,runtime.totalStarts());
        assertEquals(PachinkoRuntime.Presentation.LEFT_KURUN,runtime.presentation());
        assertEquals(0,runtime.currentPayout());assertEquals(0,runtime.cumulativePayout());
        var event=transition.packets().stream().filter(p->p.packetType()==PacketType.PACHINKO_EVENT).findFirst().orElseThrow();
        assertEquals(runtime.ballSequenceId(),event.payload().get("ballSequenceId").getAsLong());
        assertEquals(runtime.initialHitCommitted()?"V":"OUT",event.payload().get("outcome").getAsString());
        assertEquals(PachinkoGameEngine.presentationSeed(saved.id(),id,runtime.ballSequenceId()),event.payload().get("seed").getAsLong());
        var resumed=engine.resume(saved,0).orElseThrow();
        assertEquals(event.payload().get("outcome"),resumed.payload().get("outcome"));
        assertEquals(event.payload().get("seed"),resumed.payload().get("seed"));
        assertEquals(event.payload().get("startTime"),resumed.payload().get("startTime"));
    }

    @Test void forcedInitialAllocationMaps450ToNormalAnd1500ToRush() throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",27,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        UUID player=UUID.randomUUID();db.seat(player,id,NOW);
        var base=PachinkoBallAccounting.validStart(PachinkoBallAccounting.fire(PachinkoBallAccounting.lend(PachinkoRuntime.initial(),NOW),NOW+1),NOW+2);
        var v=new PachinkoRuntime(base.mode(),base.ballsHeld(),base.ballsLoaned(),base.totalFired(),base.totalStarts(),base.ballSequenceId(),
                PachinkoRuntime.Presentation.LEFT_KURUN,true,PachinkoRuntime.InitialOutcome.NONE,false,0,PachinkoRuntime.RightOutcome.NONE,0,0,NOW+2);
        db.sql("UPDATE player_sessions SET machine_state_json=? WHERE player_uuid=?",v.toJsonString(),player.toString());
        var before=db.state().session(player);var engine=new PachinkoGameEngine(new java.util.Random(1));
        var normal=engine.initialPayout(before,v,1,id,NOW+3,false);var nr=PachinkoRuntime.fromJson(normal.after().machineState().toString());
        assertEquals(PachinkoRuntime.InitialOutcome.NORMAL_450,nr.initialOutcome());assertFalse(nr.rushActive());assertEquals(450,nr.currentPayout());
        var rush=engine.initialPayout(before,v,1,id,NOW+3,true);var rr=PachinkoRuntime.fromJson(rush.after().machineState().toString());
        assertEquals(PachinkoRuntime.InitialOutcome.RUSH_1500,rr.initialOutcome());assertTrue(rr.rushActive());assertEquals(1500,rr.currentPayout());
    }

    @Test void reconnectAfterCommittedInitialPayoutCannotPayAgain() throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",28,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        UUID player=UUID.randomUUID();db.seat(player,id,NOW);
        var base=PachinkoBallAccounting.validStart(PachinkoBallAccounting.fire(PachinkoBallAccounting.lend(PachinkoRuntime.initial(),NOW),NOW+1),NOW+2);
        var v=new PachinkoRuntime(base.mode(),base.ballsHeld(),base.ballsLoaned(),base.totalFired(),base.totalStarts(),base.ballSequenceId(),PachinkoRuntime.Presentation.LEFT_KURUN,true,PachinkoRuntime.InitialOutcome.NONE,false,0,PachinkoRuntime.RightOutcome.NONE,0,0,NOW+2);
        db.sql("UPDATE player_sessions SET machine_state_json=? WHERE player_uuid=?",v.toJsonString(),player.toString());
        var engine=new PachinkoGameEngine(new java.util.Random(1));var paid=engine.initialPayout(db.state().session(player),v,1,id,NOW+3,true);var saved=store.commit(paid);
        long held=PachinkoRuntime.fromJson(saved.machineState().toString()).ballsHeld();long cumulative=PachinkoRuntime.fromJson(saved.machineState().toString()).cumulativePayout();
        assertTrue(engine.resume(saved,0).isEmpty());
        var duplicate=engine.plan(saved,db.state().machine(id),PacketType.PACHINKO_PRESENTATION,2,NOW+4,0,0,null);var after=store.commit(duplicate);
        var restored=PachinkoRuntime.fromJson(after.machineState().toString());assertEquals(held,restored.ballsHeld());assertEquals(cumulative,restored.cumulativePayout());assertTrue(restored.rushActive());
    }

    @Test void forcedRightOutEndsRushAndReconnectCannotGrantAnotherDecision() throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",29,64,0,"NORTH"),MachineType.PACHINKO,NOW);UUID player=UUID.randomUUID();db.seat(player,id,NOW);
        var rush=new PachinkoRuntime(PachinkoRuntime.Mode.RUSH,1500,250,0,0,1,PachinkoRuntime.Presentation.IDLE,true,PachinkoRuntime.InitialOutcome.RUSH_1500,true,0,PachinkoRuntime.RightOutcome.NONE,1500,1500,NOW);
        db.sql("UPDATE player_sessions SET machine_state_json=? WHERE player_uuid=?",rush.toJsonString(),player.toString());
        var engine=new PachinkoGameEngine(new java.util.Random(1),jp.pirijuggler.paper.game.pachinko.PachinkoRouting.reference(),r->PachinkoRuntime.RightOutcome.OUT);
        var started=store.commit(engine.plan(db.state().session(player),db.state().machine(id),PacketType.PACHINKO_FIRE,1,NOW+1,0,0,null));
        var pending=PachinkoRuntime.fromJson(started.machineState().toString());assertEquals(PachinkoRuntime.Presentation.RIGHT_KURUN,pending.presentation());assertEquals(PachinkoRuntime.RightOutcome.OUT,pending.rightOutcome());
        assertEquals("OUT",engine.resume(started,0).orElseThrow().payload().get("outcome").getAsString());
        var ended=store.commit(engine.plan(started,db.state().machine(id),PacketType.PACHINKO_PRESENTATION,2,NOW+2,0,0,null));var finalState=PachinkoRuntime.fromJson(ended.machineState().toString());
        assertFalse(finalState.rushActive());assertEquals(PachinkoRuntime.Mode.NORMAL,finalState.mode());assertEquals(0,finalState.rushWins());assertEquals(1500,finalState.cumulativePayout());assertTrue(engine.resume(ended,0).isEmpty());
    }

    @Test void forcedRightWinsPayExactly1500Or3000AndStayInRush() throws Exception {
        for(var outcome:java.util.List.of(PachinkoRuntime.RightOutcome.WIN_1500,PachinkoRuntime.RightOutcome.WIN_3000)){
            int id=db.create(new Machine.Location(UUID.randomUUID(),"world",30+outcome.ordinal(),64,0,"NORTH"),MachineType.PACHINKO,NOW);UUID player=UUID.randomUUID();db.seat(player,id,NOW);
            var rush=new PachinkoRuntime(PachinkoRuntime.Mode.RUSH,1500,250,0,0,1,PachinkoRuntime.Presentation.IDLE,true,PachinkoRuntime.InitialOutcome.RUSH_1500,true,0,PachinkoRuntime.RightOutcome.NONE,1500,1500,NOW);
            db.sql("UPDATE player_sessions SET machine_state_json=? WHERE player_uuid=?",rush.toJsonString(),player.toString());
            var engine=new PachinkoGameEngine(new java.util.Random(1),jp.pirijuggler.paper.game.pachinko.PachinkoRouting.reference(),r->outcome);
            var started=store.commit(engine.plan(db.state().session(player),db.state().machine(id),PacketType.PACHINKO_FIRE,1,NOW+1,0,0,null));
            var paid=store.commit(engine.plan(started,db.state().machine(id),PacketType.PACHINKO_PRESENTATION,2,NOW+2,0,0,null));var state=PachinkoRuntime.fromJson(paid.machineState().toString());
            int payout=outcome==PachinkoRuntime.RightOutcome.WIN_3000?3000:1500;assertTrue(state.rushActive());assertEquals(1,state.rushWins());assertEquals(payout,state.currentPayout());assertEquals(1500+payout,state.cumulativePayout());
        }
    }


    @Test void lockedRightEconomicsConvergeTo81PercentAnd97To3() {
        int decisions=1_000_000;
        var random=new java.util.Random(31981L);
        long wins=0, wins1500=0, wins3000=0;
        long payout=0;
        for(int i=0;i<decisions;i++){
            if(random.nextDouble()>=jp.pirijuggler.paper.game.pachinko.PachinkoSpec.RUSH_CONTINUATION_RATE)continue;
            wins++;
            if(random.nextDouble()<jp.pirijuggler.paper.game.pachinko.PachinkoSpec.RIGHT_3000_RATE){wins3000++;payout+=3000;}
            else {wins1500++;payout+=1500;}
        }
        assertEquals(0.81,(double)wins/decisions,0.002);
        assertEquals(0.03,(double)wins3000/wins,0.001);
        assertEquals(0.97,(double)wins1500/wins,0.001);
        assertEquals(1545.0,(double)payout/wins,3.0);
    }
    @Test void twoPachinkoMachinesRemainIndependentAndSlotMachineTypeIsUntouched() throws Exception {
        int a=db.create(new Machine.Location(UUID.randomUUID(),"world",40,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        int b=db.create(new Machine.Location(UUID.randomUUID(),"world",41,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        int slot=db.create(new Machine.Location(UUID.randomUUID(),"world",42,64,0,"NORTH"),MachineType.JUGGLER,NOW);
        UUID pa=UUID.randomUUID(),pb=UUID.randomUUID();db.seat(pa,a,NOW);db.seat(pb,b,NOW);
        var loanA=PachinkoBallAccounting.lend(PachinkoRuntime.initial(),NOW);
        db.sql("UPDATE player_sessions SET machine_state_json=? WHERE player_uuid=?",loanA.toJsonString(),pa.toString());
        var engine=new PachinkoGameEngine(new java.util.Random(17),new jp.pirijuggler.paper.game.pachinko.PachinkoRouting(250));
        var sa=db.state().session(pa);store.commit(engine.plan(sa,db.state().machine(a),PacketType.PACHINKO_FIRE,sa.sequence()+1,NOW+1,0,0,null));
        var ra=PachinkoRuntime.fromJson(db.state().session(pa).machineState().toString());
        var rb=PachinkoRuntime.fromJson(db.state().session(pb).machineState()==null?null:db.state().session(pb).machineState().toString());
        assertEquals(1,ra.totalFired());assertEquals(1,ra.totalStarts());
        assertEquals(PachinkoRuntime.initial(),rb);
        assertEquals(MachineType.JUGGLER,db.state().machine(slot).type());
        assertNull(db.state().machine(slot).runtimeJson());
    }


    @Test void productionStatisticsSurviveDatabaseRestart() throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",43,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        UUID player=UUID.randomUUID();db.seat(player,id,NOW);
        var base=PachinkoBallAccounting.validStart(PachinkoBallAccounting.fire(PachinkoBallAccounting.lend(PachinkoRuntime.initial(),NOW),NOW+1),NOW+2);
        var v=new PachinkoRuntime(base.mode(),base.ballsHeld(),base.ballsLoaned(),base.totalFired(),base.totalStarts(),base.ballSequenceId(),
                PachinkoRuntime.Presentation.LEFT_KURUN,true,PachinkoRuntime.InitialOutcome.NONE,false,0,PachinkoRuntime.RightOutcome.NONE,0,0,NOW+2);
        db.sql("UPDATE player_sessions SET machine_state_json=? WHERE player_uuid=?",v.toJsonString(),player.toString());
        var engine=new PachinkoGameEngine(new java.util.Random(1));
        var committed=store.commit(engine.initialPayout(db.state().session(player),v,1,id,NOW+3,true));
        var expected=PachinkoRuntime.fromJson(committed.machineState().toString());
        assertEquals(1,expected.statistics().initialJackpots());
        assertEquals(1,expected.statistics().initial1500());
        assertEquals(1,expected.statistics().rushEntries());
        assertEquals(expected,PachinkoRuntime.fromJson(db.state().machine(id).runtimeJson()));

        db.close();
        db=new jp.pirijuggler.paper.database.PiriDatabase(directory.resolve("piri.db"));
        db.open(2,NOW+10_000,config,new java.util.SplittableRandom(2),ignored->{});
        store=new jp.pirijuggler.paper.database.GameStore(db);

        var restored=PachinkoRuntime.fromJson(db.state().machine(id).runtimeJson());
        assertEquals(expected,restored);
        assertEquals(expected.statistics(),restored.statistics());
        assertEquals(1,restored.statistics().initialJackpots());
        assertEquals(1,restored.statistics().initial1500());
        assertEquals(1,restored.statistics().rushEntries());
    }

}
