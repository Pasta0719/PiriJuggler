package jp.pirijuggler.paper.session;

import jp.pirijuggler.paper.machine.DomainException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdminSessionsTest {
    @Test void validatesOwnerMachineSequenceOpAndExpiryEveryTime() {
        AdminSessions sessions = new AdminSessions(); UUID player = UUID.randomUUID(); var entry = sessions.open(player, 1, 100);
        assertEquals(0, entry.lastSequence()); assertEquals(300100, entry.expiresAt());assertEquals(entry.id(),sessions.current(player,100).id());
        assertThrows(DomainException.class, () -> sessions.accept(UUID.randomUUID(), true, entry.id(), 1, 1, 101));
        assertThrows(DomainException.class, () -> sessions.accept(player, true, entry.id(), 2, 1, 101));
        assertEquals(1, sessions.accept(player, true, entry.id(), 1, 1, 101).lastSequence());
        assertThrows(DomainException.class, () -> sessions.accept(player, true, entry.id(), 1, 1, 102));
        assertThrows(DomainException.class, () -> sessions.accept(player, false, entry.id(), 1, 2, 102));
        assertThrows(DomainException.class, () -> sessions.accept(player, true, entry.id(), 1, 2, 102));
        var next = sessions.open(player, 1, 200); assertNull(sessions.current(player,300200));
        assertThrows(DomainException.class, () -> sessions.accept(player, true, next.id(), 1, 1, 300200));
    }
    @Test void newEntryCloseAndRestartInvalidateOldId() {
        AdminSessions sessions = new AdminSessions(); UUID player = UUID.randomUUID(); var old = sessions.open(player, 1, 0);
        var fresh = sessions.open(player, 1, 1); assertNotEquals(old.id(), fresh.id());
        assertThrows(DomainException.class, () -> sessions.accept(player, true, old.id(), 1, 1, 2));
        sessions.close(player);assertNull(sessions.current(player,2));assertThrows(DomainException.class, () -> sessions.accept(player, true, fresh.id(), 1, 1, 2));
        var restart = sessions.open(player, 1, 3); sessions.clear(); assertThrows(DomainException.class, () -> sessions.accept(player, true, restart.id(), 1, 1, 4));
    }
}
