package jp.pirijuggler.paper.action;

import jp.pirijuggler.common.protocol.ErrorCode;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.threading.MainThread;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class ActionGateTest {
    private final MainThread main = new MainThread() {
        private final Thread owner = Thread.currentThread();
        public boolean isMainThread() { return Thread.currentThread() == owner; }
        public void execute(Runnable command) { requireMainThread(); command.run(); }
    };

    @Test void oldAndDuplicateSequencesAreRejectedWithoutSideEffects() {
        ActionGate gate = new ActionGate(main, 10);
        for (long old : new long[]{Long.MIN_VALUE, -1, 0, 9, 10}) {
            var result = gate.accept(old);
            assertEquals(ActionGate.Disposition.REJECTED, result.disposition());
            assertEquals(PacketType.ACTION_REJECTED, result.responseType());
            assertEquals(ErrorCode.SEQUENCE_OLD, result.error());
            assertEquals(10, gate.lastClientSequence());
        }
        assertEquals(PacketType.ACTION_ACCEPTED, gate.accept(11).responseType());
        assertEquals(11, gate.lastClientSequence());
        assertEquals(ActionGate.Disposition.REJECTED, gate.accept(11).disposition());
    }

    @Test void busyRejectsWithoutQueueingAndLeaseCannotClearSubsequentWork() {
        ActionGate gate = new ActionGate(main, 0);
        var first = gate.beginBusy();
        var rejection = gate.accept(1);
        assertEquals(PacketType.ACTION_REJECTED, rejection.responseType());
        assertEquals(ErrorCode.BUSY, rejection.error());
        assertEquals(0, gate.lastClientSequence());
        assertThrows(IllegalStateException.class, gate::beginBusy);
        first.close();
        assertFalse(gate.actionBusy());
        var second = gate.beginBusy();
        first.close();
        assertTrue(gate.actionBusy());
        second.close();
        assertEquals(ActionGate.Disposition.ACCEPTED, gate.accept(1).disposition());
    }

    @Test void stopRequiresCurrentSpinAndCannotConsumeSequenceOnMismatch() {
        ActionGate gate = new ActionGate(main, 0);
        UUID spin = UUID.randomUUID();
        assertEquals(ErrorCode.SPIN_MISMATCH, gate.acceptStop(1, UUID.randomUUID(), spin).error());
        assertEquals(ErrorCode.SPIN_MISMATCH, gate.acceptStop(1, null, null).error());
        assertEquals(0, gate.lastClientSequence());
        assertEquals(ActionGate.Disposition.ACCEPTED, gate.acceptStop(1, spin, spin).disposition());
    }

    @Test void stateCannotBeTouchedFromWorkerThread() {
        ActionGate gate = new ActionGate(main, 0);
        CompletableFuture.runAsync(() -> assertThrows(IllegalStateException.class, () -> gate.accept(1))).join();
        assertEquals(0, gate.lastClientSequence());
    }
}
