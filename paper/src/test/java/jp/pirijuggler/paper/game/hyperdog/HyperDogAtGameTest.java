package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class HyperDogAtGameTest {
    private static HyperDogAtGame at(long seed,boolean direct) {
        return new HyperDogAtGame(3,seed,1_000_000,0,0,direct);
    }
    private static HyperDogAtGame.Round play(HyperDogAtGame g,HyperDogAtGame.Role role) {
        if(g.waitingForBet())g.bet();
        var round=g.leverForced(role);
        assertEquals(round,g.pending());
        assertEquals(round,g.finish());
        assertNull(g.pending());
        return round;
    }
    private static void lower(HyperDogAtGame g) {
        g.confirmRed7();
        assertEquals(HyperDogAtGame.Stage.LOWER_INITIAL,g.stage());
        var round=play(g,HyperDogAtGame.Role.MISS);
        assertEquals(0,round.bet());assertEquals(0,round.payout());
        assertTrue(round.award()>=150);
        assertEquals(round.award(),g.lowerInitialTotal());
        assertEquals(HyperDogAtGame.Stage.LOWER_AT,g.stage());
    }
    private static void upper(HyperDogAtGame g) {
        if(g.stage()==HyperDogAtGame.Stage.RED7_CONFIRM)g.confirmRed7();
        assertEquals(HyperDogAtGame.Stage.UPPER_INITIAL,g.stage());
        long oldCredit=g.credit(),oldBet=g.totalIn(),oldPay=g.totalOut(),total=0;
        for(int i=0;i<5;i++){
            var round=play(g,HyperDogAtGame.Role.MISS);
            assertEquals(0,round.bet());
            assertEquals(0,round.payout());
            assertTrue(round.award()>=100 && round.award()<=2000);
            total+=round.award();
        }
        assertTrue(total>=500);
        assertEquals(total,g.upperInitialTotal());
        assertEquals(HyperDogAtGame.Stage.UPPER_AT,g.stage());
        assertEquals(oldCredit,g.credit());
        assertEquals(oldBet,g.totalIn());
        assertEquals(oldPay,g.totalOut());
    }

    @Test void allSpecialStagesAreTrulyFreeAndInitialAmountsAreGuaranteed(){
        var normal=at(20261109,false);
        assertEquals(HyperDogAtGame.Stage.RED7_CONFIRM,normal.stage());
        assertFalse(normal.canLever());
        normal.confirmRed7();
        assertThrows(IllegalStateException.class,normal::confirmRed7);
        var first=play(normal,HyperDogAtGame.Role.REPLAY);
        assertEquals(0,first.bet());assertEquals(0,first.payout());
        assertTrue(normal.remaining()>=150);
        assertFalse(normal.replayFree(),"free lower initial REPLAY may not affect paid game");
        var paid=play(normal,HyperDogAtGame.Role.REPLAY);
        assertEquals(3,paid.bet());
        assertTrue(normal.replayFree());
        var free=play(normal,HyperDogAtGame.Role.MISS);
        assertEquals(0,free.bet());
        assertFalse(normal.replayFree());
        assertEquals(2,normal.paidGames());
        assertEquals(1,normal.freeGames());

        var direct=at(20261109,true);
        assertEquals(1000,direct.remaining());
        upper(direct);
        assertTrue(direct.remaining()>=1500);
        assertEquals(5,direct.freeGames());
    }

    @Test void lowerPaidGrapeAccountingAndTicketBranches(){
        var game=at(91,false);lower(game);
        long base=game.remaining(),credit=game.credit(),in=game.totalIn(),out=game.totalOut();
        var grape=play(game,HyperDogAtGame.Role.GRAPE);
        assertEquals(3,grape.bet());assertEquals(30,grape.payout());
        assertEquals(0,grape.award());
        assertEquals(base-27,game.remaining());
        assertEquals(credit+27,game.credit());
        assertEquals(in+3,game.totalIn());assertEquals(out+30,game.totalOut());
        // An explicitly awarded ticket cannot be silently consumed during lower AT.
        for(int i=0;i<10000 && game.czTickets()==0;i++){
            var rare=play(game,HyperDogAtGame.Role.STRONG_CHANCE);
            assertTrue(rare.award()>=100);
            assertEquals(1,rare.ticket());
        }
        assertEquals(1,game.czTickets());
    }

    @Test void lowerTicketsGuaranteeCzAndExtraTicketsForceUpperRareSlots(){
        var game=at(93,false);lower(game);
        for(int i=0;i<4;i++){
            var result=play(game,HyperDogAtGame.Role.STRONG_CHANCE);
            assertEquals(1,result.ticket());
        }
        assertEquals(4,game.czTickets());
        // Pump GRAPE until lower balance is spent, with no extra tickets.
        for(int i=0;i<5000&&game.stage()==HyperDogAtGame.Stage.LOWER_AT;i++)
            play(game,HyperDogAtGame.Role.GRAPE);
        assertEquals(HyperDogAtGame.Stage.UPPER_CZ,game.stage());
        boolean succeeded=false;
        for(int i=0;i<5&&game.stage()==HyperDogAtGame.Stage.UPPER_CZ;i++){
            var round=play(game,HyperDogAtGame.Role.MISS);
            assertEquals(0,round.bet());assertEquals(0,round.payout());
            if(round.czSuccess())succeeded=true;
        }
        assertTrue(succeeded,"second ticket must guarantee success within 5 free games");
        assertEquals(HyperDogAtGame.Stage.UPPER_INITIAL,game.stage());
        var one=play(game,HyperDogAtGame.Role.MISS);
        var two=play(game,HyperDogAtGame.Role.MISS);
        assertTrue(one.role()!=HyperDogAtGame.Role.MISS && two.role()!=HyperDogAtGame.Role.MISS,
                "third and fourth ticket force rare in first two upper initial slots");
        for(int i=2;i<5;i++)play(game,HyperDogAtGame.Role.MISS);
        assertEquals(HyperDogAtGame.Stage.UPPER_AT,game.stage());
    }

    @Test void lowerBarCarriesRemainingAndConvertsTicketsWithoutDiscarding(){
        var game=at(521,false);lower(game);
        play(game,HyperDogAtGame.Role.STRONG_CHANCE);
        play(game,HyperDogAtGame.Role.STRONG_CHANCE);
        assertEquals(2,game.czTickets());
        long before=game.remaining(),bet=game.totalIn();
        var result=play(game,HyperDogAtGame.Role.BAR);
        assertEquals(15,result.payout());
        assertEquals(HyperDogAtGame.Stage.UPPER_INITIAL,game.stage());
        assertEquals(0,game.czTickets());
        assertEquals(before-(15-3)+1000,game.remaining());
        assertEquals(bet+3,game.totalIn());
        upper(game);
        // Upper BAR guarantees the next upper CZ and stacks forcing charges.
        play(game,HyperDogAtGame.Role.BAR);
        play(game,HyperDogAtGame.Role.BAR);
        assertEquals(2,game.barCharges());
        assertEquals(HyperDogAtGame.Stage.UPPER_AT,game.stage());
    }

    @Test void upperPaidGrapeIsThirtyAndRemainingPaysByNet(){
        var game=at(609,true);upper(game);
        long before=game.remaining(),credit=game.credit();
        var grape=play(game,HyperDogAtGame.Role.GRAPE);
        assertEquals(3,grape.bet());
        assertEquals(30,grape.payout());
        assertEquals(before-27,game.remaining());
        assertEquals(credit+27,game.credit());
        assertEquals(1,game.paidGames());
    }

    @Test void upperPremiumNeverExceeds2000AndCannotLeakCzTickets(){
        var game=at(700,true);upper(game);
        boolean seen=false;
        for(int i=0;i<10000 && !seen;i++){
            if(game.stage()==HyperDogAtGame.Stage.UPPER_CZ){
                while(game.stage()==HyperDogAtGame.Stage.UPPER_CZ)
                    play(game,HyperDogAtGame.Role.MISS);
                if(game.stage()==HyperDogAtGame.Stage.COMPLETE)break;
                while(game.stage()==HyperDogAtGame.Stage.UPPER_INITIAL)
                    play(game,HyperDogAtGame.Role.MISS);
            }
            var x=play(game,HyperDogAtGame.Role.STRONG_CHERRY);
            assertEquals(0,x.ticket());
            assertTrue(x.award()<=2000);
            if(x.premium()){seen=true;assertEquals(2000,x.award());}
        }
        assertTrue(seen,"independent upper 2% premium draw must be reachable");
        assertEquals(0,game.czTickets(),"upper rare branch cannot earn lower tickets");
    }

    @Test void lowerCzFailureReinitializesLowerWithoutComeback(){
        boolean found=false;
        for(long seed=0;seed<100 && !found;seed++){
            var game=at(seed,false);lower(game);
            play(game,HyperDogAtGame.Role.STRONG_CHANCE); // exactly one ticket
            for(int i=0;i<1000&&game.stage()==HyperDogAtGame.Stage.LOWER_AT;i++)
                play(game,HyperDogAtGame.Role.GRAPE);
            assertEquals(HyperDogAtGame.Stage.UPPER_CZ,game.stage());
            for(int i=0;i<5 && game.stage()==HyperDogAtGame.Stage.UPPER_CZ;i++)
                play(game,HyperDogAtGame.Role.MISS);
            if(game.stage()==HyperDogAtGame.Stage.LOWER_INITIAL){
                found=true;
                assertEquals(0,game.czTickets());
                assertEquals(0,game.remaining(),
                        "negative balance of the previous lower AT cannot reduce the new 150 guarantee");
                assertFalse(game.replayFree());
                var next=play(game,HyperDogAtGame.Role.MISS);
                assertTrue(next.award()>=150);
                assertEquals(next.award(),game.remaining(),
                        "CZ-loss restart must not carry debt into a fresh lower initial");
                assertEquals(HyperDogAtGame.Stage.LOWER_AT,game.stage());
            }
        }
        assertTrue(found,"non-guaranteed 5G CZ has losing outcomes");
    }

    @Test void upperCzFailureCompletesAtAndReturnsNoFreeReplay(){
        boolean found=false;
        for(long seed=0;seed<100 && !found;seed++){
            var game=at(seed,true);upper(game);
            for(int i=0;i<5000&&game.stage()==HyperDogAtGame.Stage.UPPER_AT;i++)
                play(game,HyperDogAtGame.Role.GRAPE);
            assertEquals(HyperDogAtGame.Stage.UPPER_CZ,game.stage());
            for(int i=0;i<5&&game.stage()==HyperDogAtGame.Stage.UPPER_CZ;i++)
                play(game,HyperDogAtGame.Role.REPLAY);
            if(game.stage()==HyperDogAtGame.Stage.COMPLETE){
                found=true;
                assertFalse(game.replayFree());
                assertFalse(game.canLever());
                assertEquals(0,game.czTickets());
            }
        }
        assertTrue(found);
    }

    @Test void snapshotAtAllBoundariesHasNoDuplicateFreeAwardOrPayout(){
        var game=at(42,false);
        var initial=game.snapshot();
        assertEquals(initial,HyperDogAtGame.restore(initial).snapshot());
        game.confirmRed7();
        var lever=game.leverForced(HyperDogAtGame.Role.WEAK_CHERRY);
        assertEquals(0,lever.bet());
        var restored=HyperDogAtGame.restore(game.snapshot());
        assertEquals(lever,restored.pending());
        assertEquals(game.finish(),restored.finish());
        assertEquals(game.snapshot(),restored.snapshot());
        assertThrows(IllegalStateException.class,restored::finish);
        restored.bet();
        restored.leverForced(HyperDogAtGame.Role.REPLAY);
        assertEquals(restored.snapshot(),HyperDogAtGame.restore(restored.snapshot()).snapshot());
        restored.finish();
        assertTrue(restored.replayFree());
        assertEquals(0,restored.leverForced(HyperDogAtGame.Role.MISS).bet());
        restored.finish();
        assertFalse(restored.replayFree());
    }

    @Test void overflowInPreparedSettlementKeepsEntireUnfinishedRound(){
        var game=at(91,false);lower(game);game.bet();
        game.leverForced(HyperDogAtGame.Role.GRAPE);
        JsonObject broken=game.snapshot();
        broken.addProperty("out",Long.MAX_VALUE-29);
        var at=HyperDogAtGame.restore(broken);
        var before=at.snapshot();
        assertThrows(ArithmeticException.class,at::finish);
        assertEquals(before,at.snapshot());
        assertNotNull(at.pending());
    }

    @Test void invalidBarInFreeStageRejectedAndNoMutation(){
        var game=at(45,false);game.confirmRed7();
        JsonObject before=game.snapshot();
        assertThrows(IllegalArgumentException.class,()->game.leverForced(HyperDogAtGame.Role.BAR));
        assertEquals(before,game.snapshot());
    }
}
