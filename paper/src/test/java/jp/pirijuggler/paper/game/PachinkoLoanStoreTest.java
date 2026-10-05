package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.economy.EconomyStore;
import jp.pirijuggler.paper.game.pachinko.PachinkoRuntime;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PachinkoLoanStoreTest extends GameFixture {
    @Test void oneVaultLoanCreditsExactly250DurableBallsWithoutSlotMedals() throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",22,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        UUID player=UUID.randomUUID();db.seat(player,id,NOW);
        var before=db.state().session(player);var economy=new EconomyStore(db);
        var plan=economy.prepareLoan(player,before.id(),id,1,46,1000,10000,NOW+1);
        assertEquals(250,plan.borrow());
        economy.markLoanCallStarted(plan.transactionId(),NOW+2);
        var after=economy.applyLoan(player,before.id(),id,1,plan,NOW+3);
        var runtime=PachinkoRuntime.fromJson(after.machineState().toString());
        assertEquals(250,runtime.ballsHeld());assertEquals(250,runtime.ballsLoaned());
        assertEquals(0,after.number("credit"));assertEquals(0,after.number("held_medals"));
        assertEquals(runtime,PachinkoRuntime.fromJson(db.state().machine(id).runtimeJson()));
    }
    @Test void duplicatePachinkoLoanSequenceCannotCreditAnother250Balls() throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",26,64,0,"NORTH"),MachineType.PACHINKO,NOW);
        UUID player=UUID.randomUUID();db.seat(player,id,NOW);var economy=new EconomyStore(db);
        var before=db.state().session(player);var plan=economy.prepareLoan(player,before.id(),id,1,46,1000,10000,NOW+1);
        economy.markLoanCallStarted(plan.transactionId(),NOW+2);var once=economy.applyLoan(player,before.id(),id,1,plan,NOW+3);
        assertEquals(250,PachinkoRuntime.fromJson(once.machineState().toString()).ballsHeld());
        var duplicate=economy.prepareLoan(player,before.id(),id,1,46,1000,10000,NOW+4);
        assertEquals(EconomyStore.JournalState.APPLIED,duplicate.state());
        var still=db.state().session(player);
        assertEquals(250,PachinkoRuntime.fromJson(still.machineState().toString()).ballsHeld());
        assertEquals(1,((Number)db.rows("SELECT count(*) FROM economy_transactions WHERE operation='PACHINKO_LOAN'").getFirst().values().iterator().next()).intValue());
    }

}
