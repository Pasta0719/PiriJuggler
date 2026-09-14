package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.paper.reel.InternalRole;
import jp.pirijuggler.paper.session.Session;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NormalGameTest extends GameFixture {
    @Test void publicStatesHaveExplicitExhaustiveMappingAndDoNotExposePendingBonusType() throws Exception {
        var lock=JsonParser.parseString(Files.readString(Path.of(System.getProperty("piri.specRoot"),"docs/spec-lock.json"))).getAsJsonObject().getAsJsonObject("publicGameStateMapping");assertEquals(6,lock.size());
        Session s=seat(0,0);
        for(var state:Session.GameState.values()) {
            var values=new LinkedHashMap<>(s.snapshot());values.put("game_state",state.name());values.put("internal_role","CHERRY_BIG");values.put("premium_type","B");values.put("bonus_type","BIG");
            Session snapshot=new Session(values);String wire=snapshot.publicState().get("gameState").getAsString();
            assertEquals(lock.has(state.name())?lock.get(state.name()).getAsString():state.name(),wire);
            assertDoesNotThrow(()->PublicGameState.valueOf(wire));assertEquals(state,snapshot.state());
            assertFalse(snapshot.publicState().has("internalRole"));assertFalse(snapshot.publicState().has("bonusType"));assertFalse(snapshot.publicState().has("setting"));
            if(state.name().startsWith("BONUS_"))assertFalse(wire.contains("BIG")||wire.contains("REG"));
        }
    }
    @Test void everyInternalRoleHasOneCorrectPayoutAndNormalTransition() throws Exception {
        for(var role:InternalRole.values()) {
            NormalGame game=game(role);Session s=seat(2,99);s=spin(game,s,0);
            int expected=switch(role){case GRAPE->8;case CHERRY,CHERRY_BIG,CHERRY_REG->2;case BELL,PIERO,PIERO_BIG,PIERO_REG->14;default->0;};
            assertEquals(98+expected,s.number("credit")+s.number("held_medals"));assertEquals(expected,s.number("pay_display"));
            String bonus=GameRules.bonus(role);assertEquals(bonus!=null?"BONUS_PENDING_"+bonus:role==InternalRole.REPLAY?"REPLAY_READY":"SEATED_READY",s.state().name());
            assertEquals(role==InternalRole.REPLAY?3:0,s.number("current_bet"));assertNull(s.text("internal_role"));assertEquals(7,s.number("stopped_mask"));
            assertEquals(1,scalar("SELECT total_games FROM machine_period_stats WHERE machine_id=?",s.machine()));assertEquals(expected-3,scalar("SELECT today_difference FROM machine_period_stats WHERE machine_id=?",s.machine()));
            assertEquals(2,scalar("SELECT count(*) FROM graph_points WHERE machine_id=?",s.machine()));
        }
    }
    @Test void roleIsDrawnOnlyAtLeverAndPersistedBeforeStop() throws Exception {
        var game=game(InternalRole.CHERRY_BIG);Session s=seat(50,0);s=action(game,s,PacketType.SPACE_ACTION,0);assertNull(s.text("internal_role"));
        var lever=game.plan(s,PacketType.SPACE_ACTION,s.sequence()+1,1,NOW,0,0);assertEquals("CHERRY_BIG",lever.after().text("internal_role"));
        assertEquals(Session.GameState.NORMAL_BETTED,db.state().session(s.player()).state());
        s=store.commit(lever);var packets=game.committed(lever,10_000_000);
        assertEquals("CHERRY_BIG",db.state().session(s.player()).text("internal_role"));
        for(var p:packets){String json=p.payload().toString();assertFalse(json.contains("CHERRY_BIG"));assertFalse(json.contains("internalRole"));assertFalse(json.contains("seed"));assertFalse(json.contains("setting"));}
        var stopped=action(game,s,PacketType.STOP_RIGHT,1_000_000_000);assertEquals(s.text("internal_role"),stopped.text("internal_role"));assertEquals(s.text("spin_id"),stopped.text("spin_id"));
    }
    @Test void repeatedReplayKeepsThreeLampsAndChargesOnlyFirstGame() throws Exception {
        var game=game(InternalRole.REPLAY);Session s=seat(2,100);
        for(int i=0;i<8;i++){s=spin(game,s,i*2_000_000_000L);assertEquals(Session.GameState.REPLAY_READY,s.state());assertEquals(3,s.number("current_bet"));assertEquals(0,s.number("pay_display"));}
        assertEquals(99,s.number("credit")+s.number("held_medals"));assertEquals(-3,scalar("SELECT today_difference FROM machine_period_stats"));assertEquals(8,scalar("SELECT total_games FROM machine_period_stats"));
    }
    @Test void insufficientCreditPersistsOnlyRefillAndConsumesSequence() throws Exception {
        var game=game(InternalRole.MISS);Session s=seat(0,2);var t=game.plan(s,PacketType.SPACE_ACTION,1,1,NOW,0,0);
        assertEquals("NOT_ENOUGH_CREDIT",t.packets().getFirst().payload().get("errorCode").getAsString());s=store.commit(t);
        assertEquals(2,s.number("credit"));assertEquals(0,s.number("held_medals"));assertEquals(Session.GameState.SEATED_READY,s.state());assertEquals(1,s.sequence());
        assertEquals(0,scalar("SELECT today_difference FROM machine_period_stats"));assertEquals(0,scalar("SELECT total_games FROM machine_period_stats"));
        Session copy=s;assertThrows(RuntimeException.class,()->game.plan(copy,PacketType.SPACE_ACTION,1,1,NOW,0,0));
    }
    @Test void duplicateTransactionCannotDebitOrPayTwice() throws Exception {
        var game=game(InternalRole.GRAPE);Session s=seat(50,5);var bet=game.plan(s,PacketType.SPACE_ACTION,1,1,NOW,0,0);
        s=store.commit(bet);assertEquals(s.publicState(),store.commit(bet).publicState());game.committed(bet,0);
        s=action(game,s,PacketType.SPACE_ACTION,0);s=action(game,s,PacketType.STOP_LEFT,1_000_000_000);s=action(game,s,PacketType.STOP_CENTER,1_000_000_000);
        var last=game.plan(s,PacketType.STOP_RIGHT,s.sequence()+1,1,NOW,1_000_000_000,0);var after=store.commit(last);store.commit(last);
        assertEquals(60,after.number("credit")+after.number("held_medals"));assertEquals(5,scalar("SELECT today_difference FROM machine_period_stats"));assertEquals(2,scalar("SELECT count(*) FROM graph_points"));
    }
    @Test void failedResultTransactionRollsBackSessionStatsGraphAndCanRetrySameDraw() throws Exception {
        var game=game(InternalRole.GRAPE);Session s=seat(48,5);s=action(game,s,PacketType.SPACE_ACTION,0);s=action(game,s,PacketType.SPACE_ACTION,0);
        s=action(game,s,PacketType.STOP_LEFT,1_000_000_000);s=action(game,s,PacketType.STOP_CENTER,1_000_000_000);
        var last=game.plan(s,PacketType.STOP_RIGHT,s.sequence()+1,1,NOW,1_000_000_000,0);
        db.sql("CREATE TRIGGER fail_result BEFORE INSERT ON graph_points BEGIN SELECT RAISE(ABORT,'test rollback'); END");assertThrows(Exception.class,()->store.commit(last));
        Session unchanged=db.state().session(s.player());assertEquals(s.sequence(),unchanged.sequence());assertEquals(s.number("credit"),unchanged.number("credit"));assertEquals(s.number("stopped_mask"),unchanged.number("stopped_mask"));
        assertEquals(-3,scalar("SELECT today_difference FROM machine_period_stats"));assertEquals(1,scalar("SELECT count(*) FROM graph_points"));
        db.sql("DROP TRIGGER fail_result");Session after=store.commit(last);game.committed(last,1_000_000_000);
        assertEquals(58,after.number("credit")+after.number("held_medals"));assertEquals(2,scalar("SELECT count(*) FROM graph_points"));
    }
    @Test void payoutUsesCreditFirstAndNeverOverflowsLong() {
        assertEquals(new GameRules.Balance(50,17),new GameRules.Balance(47,6).payout(14));
        assertThrows(ArithmeticException.class,()->new GameRules.Balance(50,Long.MAX_VALUE).payout(1));
        assertThrows(IllegalArgumentException.class,()->new GameRules.Balance(51,0));
    }
    @Test void earlyStopConsumesSequenceAndSpaceStopsRemainingReels() throws Exception {
        var game=game(InternalRole.BELL);Session s=seat(50,0);s=action(game,s,PacketType.SPACE_ACTION,0);s=action(game,s,PacketType.SPACE_ACTION,0);
        var early=game.plan(s,PacketType.STOP_LEFT,s.sequence()+1,1,NOW,100_000_000,0);assertEquals("STOP_TOO_EARLY",early.packets().getFirst().payload().get("errorCode").getAsString());s=store.commit(early);
        assertEquals(0,s.number("stopped_mask"));s=action(game,s,PacketType.STOP_CENTER,1_000_000_000);s=action(game,s,PacketType.SPACE_ACTION,1_000_000_000);assertEquals(3,s.number("stopped_mask"));
        s=action(game,s,PacketType.SPACE_ACTION,1_000_000_000);assertEquals(14,s.number("pay_display"));
    }
    @Test void closeAndResumePreserveExactMotionStoppedReelAndUnresolvedRole() throws Exception {
        var game=game(InternalRole.GRAPE);Session s=seat(50,0);s=action(game,s,PacketType.SPACE_ACTION,0);s=action(game,s,PacketType.SPACE_ACTION,0);s=action(game,s,PacketType.STOP_LEFT,700_000_000);
        Session motion=game.capture(s,900_000_000);String role=s.text("internal_role"),spin=s.text("spin_id");
        db.closeSession(s.player(),s.id(),s.machine(),s.sequence()+1,NOW+10,60_000,motion);s=db.seat(s.player(),s.machine(),NOW+20);
        var resumed=game.resume(s,2_000_000_000).orElseThrow();assertEquals("RESUME_NORMAL",resumed.payload().get("animation").getAsString());
        assertEquals(spin,s.text("spin_id"));assertEquals(role,s.text("internal_role"));assertEquals(1,s.number("stopped_mask"));
        s=action(game,s,PacketType.STOP_CENTER,2_500_000_000L);s=action(game,s,PacketType.STOP_RIGHT,2_500_000_000L);assertEquals(Session.GameState.SEATED_READY,s.state());assertEquals(8,s.number("pay_display"));
    }
}
