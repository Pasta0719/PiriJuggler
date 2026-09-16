package jp.pirijuggler.paper.session;

import jp.pirijuggler.paper.machine.DomainException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owned by the Paper main thread; never persisted. */
public final class AdminSessions {
    public record Entry(UUID id, UUID player, int machine, long lastSequence, long expiresAt) {}
    private final Map<UUID, Entry> entries = new HashMap<>();
    public Entry open(UUID player, int machine, long now) {
        Entry entry = new Entry(UUID.randomUUID(), player, machine, 0, now + 300_000);
        entries.put(player, entry); return entry;
    }
    public Entry accept(UUID player, boolean op, UUID id, int machine, long sequence, long now) {
        if (!op) { close(player); throw new DomainException("NOT_OP"); }
        Entry entry = entries.get(player);
        if (entry == null || entry.expiresAt <= now || !entry.id.equals(id) || entry.machine != machine) {
            if (entry != null && entry.expiresAt <= now) close(player);
            throw new DomainException("SESSION_MISMATCH");
        }
        if (sequence <= entry.lastSequence) throw new DomainException("SEQUENCE_OLD");
        Entry updated = new Entry(id, player, machine, sequence, now + 300_000);
        entries.put(player, updated); return updated;
    }
    public Entry current(UUID player, long now) {
        Entry entry = entries.get(player);
        if (entry != null && entry.expiresAt <= now) { entries.remove(player); return null; }
        return entry;
    }
    public void close(UUID player) { entries.remove(player); }
    public void expire(long now) { entries.values().removeIf(entry -> entry.expiresAt <= now); }
    public void clear() { entries.clear(); }
}
