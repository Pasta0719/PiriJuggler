package jp.pirijuggler.paper.economy;

import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.database.RecoveryStore;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.reel.StopCatalogue;
import jp.pirijuggler.paper.reel.StopSolver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EconomyStoreTest {
    @TempDir Path directory;
    PiriDatabase db; EconomyStore store; UUID player=UUID.randomUUID(), world=UUID.randomUUID();
    int machine; static final long NOW=1_700_000_000_000L; Map<String,Object> config;

    @BeforeEach void open() throws Exception {
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"))){config=ConfigValidation.load(reader).values();}
        db=new PiriDatabase(directory.resolve("piri.db"));db.open(1,NOW,config,new SplittableRandom(1),ignored->{});
        machine=db.create(new Machine.Location(world,"world",0,64,0,"NORTH"),NOW);db.seat(player,machine,NOW);store=new EconomyStore(db);
    }
    @AfterEach void close() throws Exception {db.close();}
    void code(String expected,org.junit.jupiter.api.function.Executable action){assertEquals(expected,assertThrows(DomainException.class,action).getMessage());}

    @Test void loanJournalIsDeterministicAndAlwaysPaysConfiguredBundleWithOverflow() throws Exception {
        var session=db.state().session(player);db.sql("UPDATE player_sessions SET credit=40,held_medals=3");session=db.state().session(player);
        var plan=store.prepareLoan(player,session.id(),machine,1,46,1000,10_000,NOW+1);
        assertEquals(EconomyStore.JournalState.PREPARED,plan.state());assertEquals(46,plan.borrow());assertEquals(1000,plan.vaultAmount());
        store.markLoanCallStarted(plan.transactionId(),NOW+2);var after=store.applyLoan(player,session.id(),machine,1,plan,NOW+3);
        assertEquals(50,after.number("credit"));assertEquals(39,after.number("held_medals"));assertEquals(1,after.sequence());
        var duplicate=store.prepareLoan(player,session.id(),machine,1,46,1000,10_000,NOW+4);
        assertEquals(EconomyStore.JournalState.APPLIED,duplicate.state());assertEquals(46,duplicate.borrow());assertEquals(1000,duplicate.vaultAmount());
        assertEquals(1,((Number)db.rows("SELECT count(*) FROM economy_transactions").getFirst().values().iterator().next()).intValue());
    }

    @Test void fixedLoanRequiresFundsForWholeConfiguredBundle() throws Exception {
        var session=db.state().session(player);db.sql("UPDATE player_sessions SET credit=40");session=db.state().session(player);UUID sid=session.id();
        code("NOT_ENOUGH_VAULT",()->store.prepareLoan(player,sid,machine,1,46,1000,999,NOW+1));
    }

    @Test void callStartedAtBootBecomesReviewRequiredAndBlocksNewLoan() throws Exception {
        var session=db.state().session(player);var plan=store.prepareLoan(player,session.id(),machine,1,46,1000,1000,NOW+1);store.markLoanCallStarted(plan.transactionId(),NOW+2);
        assertEquals(1,store.quarantineStartedVaultTransactions(NOW+3).size());assertTrue(store.playerEconomyBlocked(player));
        code("VAULT_ERROR",()->store.prepareLoan(player,session.id(),machine,2,46,1000,1000,NOW+4));
    }

    @Test void cashout662CreatesOne662Token() throws Exception {
        db.sql("UPDATE player_sessions SET credit=50,held_medals=612");var session=db.state().session(player);
        var plan=store.prepareCashout(player,session.id(),machine,1,NOW+1);
        assertEquals(662,plan.amount());assertEquals(List.of(662),plan.bundles().stream().map(EconomyStore.Bundle::amount).toList());
        assertEquals(0,plan.session().number("credit"));assertEquals(0,plan.session().number("held_medals"));
        UUID delivered=plan.bundles().getFirst().id();store.finishCashout(player,plan.transactionId(),Set.of(delivered),NOW+2);
        assertTrue(store.validActiveBundle(delivered,662));
        assertTrue(db.rows("SELECT * FROM player_wallet WHERE player_uuid=?",player.toString()).isEmpty());
        assertEquals("COMPLETED",db.rows("SELECT status FROM cashout_transactions").getFirst().get("status"));
    }

    @Test void cashout5000StillCreatesExactlyOneToken() throws Exception {
        db.sql("UPDATE player_sessions SET credit=0,held_medals=5000");var session=db.state().session(player);
        var plan=store.prepareCashout(player,session.id(),machine,1,NOW+1);
        assertEquals(5000,plan.amount());assertEquals(1,plan.bundles().size());assertEquals(5000,plan.bundles().getFirst().amount());
        UUID delivered=plan.bundles().getFirst().id();store.finishCashout(player,plan.transactionId(),Set.of(delivered),NOW+2);
        assertTrue(store.validActiveBundle(delivered,5000));
    }

    @Test void undeliveredSingleTokenMovesWholeAmountToPendingWallet() throws Exception {
        db.sql("UPDATE player_sessions SET credit=50,held_medals=612");var session=db.state().session(player);
        var plan=store.prepareCashout(player,session.id(),machine,1,NOW+1);
        store.finishCashout(player,plan.transactionId(),Set.of(),NOW+2);
        assertEquals(662,((Number)db.rows("SELECT pending_medals FROM player_wallet WHERE player_uuid=?",player.toString()).getFirst().get("pending_medals")).intValue());
    }

    @Test void recoveryCashoutForceSettlesGraceAndCombinesWalletPending() throws Exception {
        db.sql("UPDATE player_sessions SET game_state='BONUS_PENDING_REG',credit=20,held_medals=30,bonus_type='REG'");
        db.disconnect(player,NOW+1,60_000);db.sql("INSERT INTO player_wallet(player_uuid,pending_medals,updated_at) VALUES(?,?,?)",player.toString(),7,NOW);
        var status=store.recoveryStatus(player);assertEquals("SUSPENDED_GRACE",status.lifecycle());assertEquals(7,status.pending());
        var plan=store.prepareRecoveryCashout(player,new RecoveryStore(db,config,new StopSolver(new StopCatalogue())),NOW+2);
        assertEquals(152,plan.amount());assertEquals(1,plan.bundles().size());assertEquals(152,plan.bundles().getFirst().amount());
        assertNull(db.state().session(player));assertTrue(db.rows("SELECT * FROM player_wallet WHERE player_uuid=?",player.toString()).isEmpty());
        UUID delivered=plan.bundles().getFirst().id();store.finishRecoveryCashout(player,plan.transactionId(),Set.of(delivered),NOW+3);
        assertTrue(store.validActiveBundle(delivered,152));assertEquals("COMPLETED",db.rows("SELECT status FROM cashout_transactions WHERE transaction_id=?",plan.transactionId()).getFirst().get("status"));
    }

    @Test void recoveryCashoutRejectsActiveSessionAndUndeliveredReturnsToWallet() throws Exception {
        code("INVALID_STATE",()->store.prepareRecoveryCashout(player,new RecoveryStore(db,config,new StopSolver(new StopCatalogue())),NOW+1));
        db.sql("UPDATE player_sessions SET lifecycle='SUSPENDED_SAFE',credit=5,held_medals=6");
        var plan=store.prepareRecoveryCashout(player,new RecoveryStore(db,config,new StopSolver(new StopCatalogue())),NOW+2);
        store.finishRecoveryCashout(player,plan.transactionId(),Set.of(),NOW+3);
        assertEquals(11,((Number)db.rows("SELECT pending_medals FROM player_wallet WHERE player_uuid=?",player.toString()).getFirst().get("pending_medals")).longValue());
    }

    @Test void insertionPartiallyConsumesLegacyTokenAndCreatesUnlimitedRemainder() throws Exception {
        UUID bundle=UUID.randomUUID();db.sql("INSERT INTO medal_tokens(bundle_id,amount,state,created_at,updated_at) VALUES(?,500,'ACTIVE',?,?)",bundle.toString(),NOW,NOW);
        var session=db.state().session(player);var plan=store.prepareInsert(player,session.id(),machine,1,List.of(new EconomyStore.InsertCandidate(0,bundle,500)),NOW+1);
        assertEquals(50,plan.inserted());assertEquals(50,plan.session().number("credit"));assertEquals(1,plan.replacements().size());
        var replacement=plan.replacements().getFirst();assertEquals(450,replacement.newAmount());assertNotNull(replacement.newBundleId());
        assertEquals("RETIRED",db.rows("SELECT state FROM medal_tokens WHERE bundle_id=?",bundle.toString()).getFirst().get("state"));
        assertTrue(store.validActiveBundle(replacement.newBundleId(),450));
        store.markInsertApplied(plan.transactionId(),NOW+2);
        assertEquals("APPLIED",db.rows("SELECT status FROM medal_inventory_transactions WHERE transaction_id=?",plan.transactionId()).getFirst().get("status"));
    }

    @Test void insertionPartiallyConsumesLargeSingleToken() throws Exception {
        db.sql("CREATE TABLE IF NOT EXISTS medal_tokens_unlimited(bundle_id TEXT PRIMARY KEY,amount INTEGER NOT NULL CHECK(amount>=1),state TEXT NOT NULL CHECK(state IN ('PENDING_DELIVERY','ACTIVE','RETIRED')),source_transaction_id TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        UUID bundle=UUID.randomUUID();db.sql("INSERT INTO medal_tokens_unlimited VALUES(?,10000,'ACTIVE',NULL,?,?)",bundle.toString(),NOW,NOW);
        var session=db.state().session(player);var plan=store.prepareInsert(player,session.id(),machine,1,List.of(new EconomyStore.InsertCandidate(0,bundle,10000)),NOW+1);
        var replacement=plan.replacements().getFirst();assertEquals(9950,replacement.newAmount());assertTrue(store.validActiveBundle(replacement.newBundleId(),9950));
    }

    @Test void insertionFullyConsumesTokenWithoutCreatingZeroValueReplacement() throws Exception {
        UUID bundle=UUID.randomUUID();db.sql("INSERT INTO medal_tokens(bundle_id,amount,state,created_at,updated_at) VALUES(?,50,'ACTIVE',?,?)",bundle.toString(),NOW,NOW);
        var session=db.state().session(player);var plan=store.prepareInsert(player,session.id(),machine,1,List.of(new EconomyStore.InsertCandidate(0,bundle,50)),NOW+1);
        assertEquals(50,plan.inserted());assertEquals(50,plan.session().number("credit"));assertEquals(1,plan.replacements().size());
        var replacement=plan.replacements().getFirst();assertNull(replacement.newBundleId());assertEquals(0,replacement.newAmount());
        assertEquals("RETIRED",db.rows("SELECT state FROM medal_tokens WHERE bundle_id=?",bundle.toString()).getFirst().get("state"));
    }

    @Test void retiredDuplicateBundleCannotBeSpentAgain() throws Exception {
        UUID bundle=UUID.randomUUID();db.sql("INSERT INTO medal_tokens(bundle_id,amount,state,created_at,updated_at) VALUES(?,20,'ACTIVE',?,?)",bundle.toString(),NOW,NOW);
        var session=db.state().session(player);var first=store.prepareInsert(player,session.id(),machine,1,List.of(new EconomyStore.InsertCandidate(0,bundle,20)),NOW+1);store.markInsertApplied(first.transactionId(),NOW+2);
        db.sql("UPDATE player_sessions SET credit=0");session=db.state().session(player);
        UUID sid=session.id();code("INVALID_ITEM",()->store.prepareInsert(player,sid,machine,2,List.of(new EconomyStore.InsertCandidate(1,bundle,20)),NOW+3));
    }

    @Test void economyActionsOnlyAllowedInSixReadyStates() {
        assertTrue(EconomyStore.allowed(jp.pirijuggler.paper.session.Session.GameState.SEATED_READY));
        assertTrue(EconomyStore.allowed(jp.pirijuggler.paper.session.Session.GameState.REPLAY_READY));
        assertTrue(EconomyStore.allowed(jp.pirijuggler.paper.session.Session.GameState.BONUS_PENDING_BIG));
        assertTrue(EconomyStore.allowed(jp.pirijuggler.paper.session.Session.GameState.BONUS_PENDING_REG));
        assertTrue(EconomyStore.allowed(jp.pirijuggler.paper.session.Session.GameState.BIG_READY));
        assertTrue(EconomyStore.allowed(jp.pirijuggler.paper.session.Session.GameState.REG_READY));
        assertFalse(EconomyStore.allowed(jp.pirijuggler.paper.session.Session.GameState.NORMAL_BETTED));
        assertFalse(EconomyStore.allowed(jp.pirijuggler.paper.session.Session.GameState.NORMAL_SPINNING));
    }
}
