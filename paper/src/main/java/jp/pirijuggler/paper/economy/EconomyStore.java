package jp.pirijuggler.paper.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.session.Session;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** SQLite-side Phase07 journal/ledger operations. Must run on the dedicated DB executor. */
public final class EconomyStore {
    public enum JournalState { PREPARED, CALL_STARTED, APPLIED, ROLLED_BACK, REVIEW_REQUIRED }
    public record LoanPlan(String transactionId, int borrow, double vaultAmount, double balanceBefore, JournalState state) { }
    public record Bundle(UUID id, int amount) { }
    public record CashoutPlan(String transactionId, long amount, List<Bundle> bundles, boolean alreadyCompleted, Session session) {
        public CashoutPlan { bundles = List.copyOf(bundles); }
    }
    public record InsertCandidate(int slot, UUID bundleId, int amount) { }
    public record InsertReplacement(int slot, UUID oldBundleId, int oldAmount, UUID newBundleId, int newAmount, int consumed) { }
    public record InsertPlan(String transactionId, int inserted, int creditBefore, long sequenceBefore,
                             List<InsertReplacement> replacements, Session session) {
        public InsertPlan { replacements = List.copyOf(replacements); }
    }

    private static final String UNLIMITED_TABLE = "medal_tokens_unlimited";
    private static final EnumSet<Session.GameState> ECONOMY_STATES = EnumSet.of(
            Session.GameState.SEATED_READY, Session.GameState.REPLAY_READY,
            Session.GameState.BONUS_PENDING_BIG, Session.GameState.BONUS_PENDING_REG,
            Session.GameState.BIG_READY, Session.GameState.REG_READY);

    private final PiriDatabase db;
    public EconomyStore(PiriDatabase db) { this.db = db; }

    public static boolean allowed(Session.GameState state) { return ECONOMY_STATES.contains(state); }

    /** Convert an indeterminate external call left by a hard crash into an explicit manual-review stop. */
    public List<Map<String,Object>> quarantineStartedVaultTransactions(long now) throws Exception {
        return db.transaction(() -> {
            var rows = db.rows("SELECT * FROM economy_transactions WHERE status='CALL_STARTED'");
            for (var row : rows) db.sql("UPDATE economy_transactions SET status='REVIEW_REQUIRED',updated_at=? WHERE transaction_id=? AND status='CALL_STARTED'",
                    now, row.get("transaction_id"));
            return rows;
        });
    }

    public boolean playerEconomyBlocked(UUID player) throws Exception {
        return !db.rows("SELECT transaction_id FROM economy_transactions WHERE player_uuid=? AND status='REVIEW_REQUIRED' LIMIT 1", player.toString()).isEmpty();
    }

    public LoanPlan prepareLoan(UUID player, UUID sessionId, int machine, long sequence,
                                int borrow, int vaultPerMedal, double balanceBefore, long now) throws Exception {
        if (borrow < 1 || vaultPerMedal < 1) throw new DomainException("NOT_ENOUGH_VAULT");
        double vaultAmount = Math.multiplyExact((long) borrow, (long) vaultPerMedal);
        String transactionId = deterministic("LOAN", sessionId, sequence);
        return db.transaction(() -> {
            var existing = db.rows("SELECT * FROM economy_transactions WHERE transaction_id=?", transactionId);
            if (!existing.isEmpty()) {
                String status = (String) existing.getFirst().get("status");
                return new LoanPlan(transactionId, borrow, vaultAmount, balanceBefore, JournalState.valueOf(status));
            }
            requireNoEconomyReview(player);
            Session session = requireActive(player, sessionId, machine);
            if (!allowed(session.state())) throw new DomainException("INVALID_STATE");
            if (sequence <= session.sequence()) throw new DomainException("SEQUENCE_OLD");
            if (session.number("credit") + borrow > 50) throw new DomainException("INVALID_STATE");
            db.sql("INSERT INTO economy_transactions(transaction_id,player_uuid,operation,vault_amount,item_snapshot_json,balance_before,status,created_at,updated_at) VALUES(?,?,'LOAN',?,NULL,?,'PREPARED',?,?)",
                    transactionId, player.toString(), vaultAmount, balanceBefore, now, now);
            return new LoanPlan(transactionId, borrow, vaultAmount, balanceBefore, JournalState.PREPARED);
        });
    }

    public void markLoanCallStarted(String transactionId, long now) throws Exception {
        int changed = db.sql("UPDATE economy_transactions SET status='CALL_STARTED',updated_at=? WHERE transaction_id=? AND status='PREPARED'", now, transactionId);
        if (changed != 1) throw new DomainException("VAULT_ERROR");
    }

    public Session applyLoan(UUID player, UUID sessionId, int machine, long sequence, LoanPlan plan, long now) throws Exception {
        return db.transaction(() -> {
            var tx = requireRow("SELECT * FROM economy_transactions WHERE transaction_id=?", plan.transactionId());
            if ("APPLIED".equals(tx.get("status"))) return requireSession(player);
            if (!"CALL_STARTED".equals(tx.get("status"))) throw new DomainException("VAULT_ERROR");
            Session session = requireActive(player, sessionId, machine);
            if (sequence <= session.sequence()) throw new DomainException("SEQUENCE_OLD");
            long credit = Math.addExact(session.number("credit"), plan.borrow());
            if (credit > 50) throw new DomainException("INVALID_STATE");
            int changed = db.sql("UPDATE player_sessions SET credit=?,last_client_sequence=?,last_activity=? WHERE session_id=? AND last_client_sequence=? AND lifecycle='ACTIVE'",
                    credit, sequence, now, sessionId.toString(), session.sequence());
            if (changed != 1) throw new DomainException("SEQUENCE_OLD");
            db.sql("UPDATE economy_transactions SET status='APPLIED',updated_at=? WHERE transaction_id=?", now, plan.transactionId());
            return requireSession(player);
        });
    }

    public void rollbackLoan(String transactionId, long now) throws Exception {
        db.sql("UPDATE economy_transactions SET status='ROLLED_BACK',updated_at=? WHERE transaction_id=? AND status IN ('PREPARED','CALL_STARTED')", now, transactionId);
    }

    public CashoutPlan prepareCashout(UUID player, UUID sessionId, int machine, long sequence, long now) throws Exception {
        String transactionId = deterministic("CASHOUT", sessionId, sequence);
        return db.transaction(() -> {
            ensureUnlimitedTable();
            var existing = db.rows("SELECT * FROM cashout_transactions WHERE transaction_id=?", transactionId);
            if (!existing.isEmpty()) {
                var row = existing.getFirst();
                boolean completed = "COMPLETED".equals(row.get("status"));
                return new CashoutPlan(transactionId, ((Number) row.get("amount")).longValue(), bundlesFor(transactionId), completed, requireSession(player));
            }
            Session session = requireActive(player, sessionId, machine);
            if (!allowed(session.state())) throw new DomainException("INVALID_STATE");
            if (sequence <= session.sequence()) throw new DomainException("SEQUENCE_OLD");
            long amount = Math.addExact(session.number("credit"), session.number("held_medals"));
            if (amount > MedalToken.MAX_AMOUNT) throw new DomainException("MEDAL_AMOUNT_TOO_LARGE");
            db.sql("INSERT INTO cashout_transactions(transaction_id,player_uuid,amount,status,created_at,updated_at) VALUES(?,?,?,'PENDING',?,?)",
                    transactionId, player.toString(), amount, now, now);
            List<Bundle> bundles = new ArrayList<>();
            if (amount > 0) {
                int whole = Math.toIntExact(amount);
                UUID id = UUID.randomUUID();
                insertUnlimitedBundle(id, whole, "PENDING_DELIVERY", transactionId, now);
                bundles.add(new Bundle(id, whole));
            }
            int changed = db.sql("UPDATE player_sessions SET credit=0,held_medals=0,last_client_sequence=?,last_activity=? WHERE session_id=? AND last_client_sequence=? AND lifecycle='ACTIVE'",
                    sequence, now, sessionId.toString(), session.sequence());
            if (changed != 1) throw new DomainException("SEQUENCE_OLD");
            return new CashoutPlan(transactionId, amount, bundles, false, requireSession(player));
        });
    }

    public Session finishCashout(UUID player, String transactionId, Set<UUID> delivered, long now) throws Exception {
        return db.transaction(() -> {
            ensureUnlimitedTable();
            var cashout = requireRow("SELECT * FROM cashout_transactions WHERE transaction_id=? AND player_uuid=?", transactionId, player.toString());
            if ("COMPLETED".equals(cashout.get("status"))) return requireSession(player);
            long deliveredAmount = 0, pendingAmount = 0;
            for (Bundle bundle : bundlesFor(transactionId)) {
                if (delivered.contains(bundle.id())) {
                    setBundleState(bundle.id(), "PENDING_DELIVERY", "ACTIVE", now);
                    deliveredAmount = Math.addExact(deliveredAmount, bundle.amount());
                } else {
                    setBundleState(bundle.id(), "PENDING_DELIVERY", "RETIRED", now);
                    pendingAmount = Math.addExact(pendingAmount, bundle.amount());
                }
            }
            if (pendingAmount > 0) db.sql("INSERT INTO player_wallet(player_uuid,pending_medals,updated_at) VALUES(?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET pending_medals=player_wallet.pending_medals+excluded.pending_medals,updated_at=excluded.updated_at",
                    player.toString(), pendingAmount, now);
            db.sql("UPDATE cashout_transactions SET delivered_amount=?,pending_amount=?,status='COMPLETED',updated_at=? WHERE transaction_id=?",
                    deliveredAmount, pendingAmount, now, transactionId);
            return requireSession(player);
        });
    }

    public InsertPlan prepareInsert(UUID player, UUID sessionId, int machine, long sequence,
                                    List<InsertCandidate> candidates, long now) throws Exception {
        Objects.requireNonNull(candidates);
        String transactionId = deterministic("INSERT", sessionId, sequence);
        return db.transaction(() -> {
            ensureUnlimitedTable();
            Session session = requireActive(player, sessionId, machine);
            if (!allowed(session.state())) throw new DomainException("INVALID_STATE");
            if (sequence <= session.sequence()) throw new DomainException("SEQUENCE_OLD");
            int need = 50 - Math.toIntExact(session.number("credit"));
            if (need <= 0) throw new DomainException("INVALID_STATE");
            Set<UUID> seen = new HashSet<>();
            List<InsertReplacement> replacements = new ArrayList<>();
            int inserted = 0;
            for (InsertCandidate candidate : candidates) {
                if (inserted >= need) break;
                if (candidate.slot() < 0 || candidate.slot() > 35 || candidate.amount() < 1 || candidate.amount() > MedalToken.MAX_AMOUNT || !seen.add(candidate.bundleId()))
                    throw new DomainException("INVALID_ITEM");
                requireUsableBundle(candidate.bundleId(), candidate.amount());
                int consumed = Math.min(candidate.amount(), need - inserted);
                int remainder = candidate.amount() - consumed;
                UUID remainderId = remainder == 0 ? null : UUID.randomUUID();
                replacements.add(new InsertReplacement(candidate.slot(), candidate.bundleId(), candidate.amount(), remainderId, remainder, consumed));
                inserted += consumed;
            }
            if (inserted <= 0) throw new DomainException("NOT_ENOUGH_MEDALS");
            JsonArray beforeJson = new JsonArray(), afterJson = new JsonArray();
            for (InsertReplacement replacement : replacements) {
                beforeJson.add(bundleJson(replacement.oldBundleId(), replacement.oldAmount(), replacement.slot()));
                setBundleState(replacement.oldBundleId(), "ACTIVE", "RETIRED", now);
                if (replacement.newBundleId() != null) {
                    insertUnlimitedBundle(replacement.newBundleId(), replacement.newAmount(), "ACTIVE", null, now);
                    afterJson.add(bundleJson(replacement.newBundleId(), replacement.newAmount(), replacement.slot()));
                }
            }
            db.sql("INSERT INTO medal_inventory_transactions(transaction_id,player_uuid,operation,before_bundle_json,after_bundle_json,container_snapshot_json,status,created_at,updated_at) VALUES(?,?,'INSERT_MEDALS',?,?,?,'LEDGER_COMMITTED',?,?)",
                    transactionId, player.toString(), beforeJson.toString(), afterJson.toString(), "{\"container\":\"PLAYER_INVENTORY\"}", now, now);
            int creditBefore = Math.toIntExact(session.number("credit"));
            int changed = db.sql("UPDATE player_sessions SET credit=?,last_client_sequence=?,last_activity=? WHERE session_id=? AND last_client_sequence=? AND lifecycle='ACTIVE'",
                    creditBefore + inserted, sequence, now, sessionId.toString(), session.sequence());
            if (changed != 1) throw new DomainException("SEQUENCE_OLD");
            return new InsertPlan(transactionId, inserted, creditBefore, session.sequence(), replacements, requireSession(player));
        });
    }

    public void markInsertApplied(String transactionId, long now) throws Exception {
        db.sql("UPDATE medal_inventory_transactions SET status='APPLIED',updated_at=? WHERE transaction_id=? AND status='LEDGER_COMMITTED'", now, transactionId);
    }

    public Session rollbackInsert(UUID player, UUID sessionId, InsertPlan plan, long now) throws Exception {
        return db.transaction(() -> {
            ensureUnlimitedTable();
            for (InsertReplacement replacement : plan.replacements()) {
                setBundleStateAny(replacement.oldBundleId(), "ACTIVE", now);
                if (replacement.newBundleId() != null) setBundleStateAny(replacement.newBundleId(), "RETIRED", now);
            }
            db.sql("UPDATE player_sessions SET credit=?,last_client_sequence=?,last_activity=? WHERE session_id=?",
                    plan.creditBefore(), plan.sequenceBefore(), now, sessionId.toString());
            db.sql("UPDATE medal_inventory_transactions SET status='ROLLED_BACK',updated_at=? WHERE transaction_id=?", now, plan.transactionId());
            return requireSession(player);
        });
    }

    public boolean validActiveBundle(UUID id, int amount) throws Exception {
        ensureUnlimitedTable();
        try { requireUsableBundle(id, amount); return true; }
        catch (DomainException invalid) { return false; }
    }

    private void requireNoEconomyReview(UUID player) throws Exception {
        if (playerEconomyBlocked(player)) throw new DomainException("VAULT_ERROR");
    }

    private void requireUsableBundle(UUID id, int amount) throws Exception {
        String needle = "%" + id + "%";
        if (!db.rows("SELECT transaction_id FROM medal_inventory_transactions WHERE status='REVIEW_REQUIRED' AND (before_bundle_json LIKE ? OR after_bundle_json LIKE ?) LIMIT 1", needle, needle).isEmpty())
            throw new DomainException("TOKEN_REVIEW_REQUIRED");
        var current = bundleRow(id);
        if (current == null || !"ACTIVE".equals(current.get("state")) || ((Number) current.get("amount")).intValue() != amount)
            throw new DomainException("INVALID_ITEM");
    }

    private Session requireActive(UUID player, UUID sessionId, int machine) throws Exception {
        Session session = requireSession(player);
        if (!session.id().equals(sessionId) || session.machine() != machine || session.lifecycle() != Session.Lifecycle.ACTIVE)
            throw new DomainException("SESSION_MISMATCH");
        return session;
    }

    private Session requireSession(UUID player) throws Exception {
        var rows = db.rows("SELECT * FROM player_sessions WHERE player_uuid=?", player.toString());
        if (rows.size() != 1) throw new DomainException("SESSION_MISMATCH");
        return new Session(rows.getFirst());
    }

    private List<Bundle> bundlesFor(String sourceTransactionId) throws Exception {
        ensureUnlimitedTable();
        List<Bundle> result = new ArrayList<>();
        for (var row : db.rows("SELECT bundle_id,amount FROM medal_tokens WHERE source_transaction_id=? ORDER BY created_at,bundle_id", sourceTransactionId))
            result.add(new Bundle(UUID.fromString((String) row.get("bundle_id")), ((Number) row.get("amount")).intValue()));
        for (var row : db.rows("SELECT bundle_id,amount FROM " + UNLIMITED_TABLE + " WHERE source_transaction_id=? ORDER BY created_at,bundle_id", sourceTransactionId))
            result.add(new Bundle(UUID.fromString((String) row.get("bundle_id")), ((Number) row.get("amount")).intValue()));
        return result;
    }

    private void ensureUnlimitedTable() throws Exception {
        db.sql("CREATE TABLE IF NOT EXISTS " + UNLIMITED_TABLE + " (bundle_id TEXT PRIMARY KEY,amount INTEGER NOT NULL CHECK(amount>=1),state TEXT NOT NULL CHECK(state IN ('PENDING_DELIVERY','ACTIVE','RETIRED')),source_transaction_id TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
    }

    private void insertUnlimitedBundle(UUID id, int amount, String state, String sourceTransactionId, long now) throws Exception {
        if (amount < 1 || amount > MedalToken.MAX_AMOUNT) throw new DomainException("INVALID_ITEM");
        db.sql("INSERT INTO " + UNLIMITED_TABLE + "(bundle_id,amount,state,source_transaction_id,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                id.toString(), amount, state, sourceTransactionId, now, now);
    }

    private Map<String,Object> bundleRow(UUID id) throws Exception {
        var modern = db.rows("SELECT amount,state FROM " + UNLIMITED_TABLE + " WHERE bundle_id=?", id.toString());
        if (modern.size() == 1) return modern.getFirst();
        var legacy = db.rows("SELECT amount,state FROM medal_tokens WHERE bundle_id=?", id.toString());
        return legacy.size() == 1 ? legacy.getFirst() : null;
    }

    private void setBundleState(UUID id, String from, String to, long now) throws Exception {
        int changed = db.sql("UPDATE " + UNLIMITED_TABLE + " SET state=?,updated_at=? WHERE bundle_id=? AND state=?", to, now, id.toString(), from);
        if (changed == 0) changed = db.sql("UPDATE medal_tokens SET state=?,updated_at=? WHERE bundle_id=? AND state=?", to, now, id.toString(), from);
        if (changed != 1) throw new DomainException("INVALID_ITEM");
    }

    private void setBundleStateAny(UUID id, String to, long now) throws Exception {
        int changed = db.sql("UPDATE " + UNLIMITED_TABLE + " SET state=?,updated_at=? WHERE bundle_id=?", to, now, id.toString());
        if (changed == 0) changed = db.sql("UPDATE medal_tokens SET state=?,updated_at=? WHERE bundle_id=?", to, now, id.toString());
        if (changed != 1) throw new DomainException("INVALID_ITEM");
    }

    private Map<String,Object> requireRow(String sql, Object... args) throws Exception {
        var rows = db.rows(sql, args);
        if (rows.size() != 1) throw new DomainException("DB_ERROR");
        return rows.getFirst();
    }

    private static JsonObject bundleJson(UUID id, int amount, int slot) {
        JsonObject json = new JsonObject(); json.addProperty("bundleId", id.toString());
        json.addProperty("amount", amount); json.addProperty("slot", slot); return json;
    }

    private static String deterministic(String operation, UUID sessionId, long sequence) {
        return UUID.nameUUIDFromBytes((operation + ":" + sessionId + ":" + sequence).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
