package jp.pirijuggler.paper.action;

import jp.pirijuggler.common.protocol.ErrorCode;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.threading.MainThread;
import java.util.Objects;
import java.util.UUID;

/** One gate belongs to one session. Rejections never mutate accepted sequence state. */
public final class ActionGate {
    public enum Disposition { ACCEPTED, REJECTED, DISCARDED }
    public record Decision(Disposition disposition, ErrorCode error) {
        public PacketType responseType() {
            return switch (disposition) {
                case ACCEPTED -> PacketType.ACTION_ACCEPTED;
                case REJECTED -> PacketType.ACTION_REJECTED;
                case DISCARDED -> null;
            };
        }
    }

    private final MainThread mainThread;
    private long lastClientSequence;
    private Lease active;

    public ActionGate(MainThread mainThread, long lastClientSequence) {
        this.mainThread = Objects.requireNonNull(mainThread);
        this.lastClientSequence = lastClientSequence;
    }

    public Decision accept(long sequence) {
        mainThread.requireMainThread();
        if (sequence <= lastClientSequence) return new Decision(Disposition.REJECTED, ErrorCode.SEQUENCE_OLD);
        if (actionBusy()) return new Decision(Disposition.REJECTED, ErrorCode.BUSY);
        lastClientSequence = sequence;
        return new Decision(Disposition.ACCEPTED, null);
    }

    public Decision acceptStop(long sequence, UUID sessionSpin, UUID currentSpin) {
        mainThread.requireMainThread();
        if (sequence <= lastClientSequence) return new Decision(Disposition.REJECTED, ErrorCode.SEQUENCE_OLD);
        if (actionBusy()) return new Decision(Disposition.REJECTED, ErrorCode.BUSY);
        // Both IDs are server-owned; the client STOP payload has no spinId field.
        if (currentSpin == null || !currentSpin.equals(sessionSpin)) return new Decision(Disposition.REJECTED, ErrorCode.SPIN_MISMATCH);
        return accept(sequence);
    }

    public Lease beginBusy() {
        mainThread.requireMainThread();
        if (active != null) throw new IllegalStateException("Action already BUSY");
        active = new Lease();
        return active;
    }

    public boolean actionBusy() { mainThread.requireMainThread(); return active != null; }
    public long lastClientSequence() { mainThread.requireMainThread(); return lastClientSequence; }

    public final class Lease implements AutoCloseable {
        private boolean closed;
        private Lease() { }
        @Override public void close() {
            mainThread.requireMainThread();
            if (!closed) {
                closed = true;
                if (active == this) active = null;
            }
        }
    }
}
