package jp.pirijuggler.paper.game.hyperdog;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** v7 isolated award calibration; no change to existing reels, BAR, CZ, or BET. */
class HyperDogV7StrengthTest {
    private static HyperDogAtGame upper(){
        var g=new HyperDogAtGame(1,20261109L,1000000,0,0,true);
        g.confirmRed7();
        assertEquals(HyperDogAtGame.Stage.UPPER_INITIAL,g.stage());
        for(int i=0;i<5;i++){
            var r=g.leverForced(HyperDogAtGame.Role.MISS);
            assertEquals(100,r.award(),"upper initial must keep the +100 guarantee");
            assertEquals(0,r.bet());
            assertEquals(0,r.payout());
            g.finish();
        }
        assertEquals(HyperDogAtGame.Stage.UPPER_AT,g.stage());
        assertEquals(1500,g.remaining()); // normal BAR +1000 plus five +100
        return g;
    }
    private static HyperDogAtGame.Round hit(HyperDogAtGame g,HyperDogAtGame.Role role){
        if(g.waitingForBet())g.bet();
        var r=g.leverForced(role);
        assertEquals(role,r.role());
        assertTrue(r.award()<=2000,"single award must stay capped at +2000");
        g.finish();
        return r;
    }
    @Test void weakAndStrongRoleScalesPreservePureNetAndZeroCzTickets(){
        var g=upper();
        var weak=hit(g,HyperDogAtGame.Role.WEAK_CHERRY);
        assertTrue(Set.of(80L,160L,240L).contains(weak.award()));
        var wm=hit(g,HyperDogAtGame.Role.WATERMELON);
        assertTrue(Set.of(80L,160L,240L).contains(wm.award()));
        var chance=hit(g,HyperDogAtGame.Role.CHANCE);
        assertTrue(Set.of(270L,360L,450L,720L,900L).contains(chance.award()));
        var strong=hit(g,HyperDogAtGame.Role.STRONG_CHERRY);
        assertTrue(Set.of(500L,700L,1000L,1500L,2000L).contains(strong.award()));
        var strongChance=hit(g,HyperDogAtGame.Role.STRONG_CHANCE);
        assertTrue(Set.of(1000L,1500L,2000L).contains(strongChance.award()));
        assertEquals(0,g.czTickets());
        var before=g.remaining();
        var grape=hit(g,HyperDogAtGame.Role.GRAPE);
        assertEquals(30,grape.payout());
        assertEquals(0,grape.award());
        assertEquals(before-27,g.remaining());
    }
    @Test void highVolumeUpperRewardsNeverExceedTwoThousandAndRemainReproducible(){
        var g=upper();
        for(int i=0;i<600;i++){
            var r=hit(g,i%3==0?HyperDogAtGame.Role.STRONG_CHANCE:
                i%3==1?HyperDogAtGame.Role.STRONG_CHERRY:HyperDogAtGame.Role.CHANCE);
            assertTrue(r.award()>=270&&r.award()<=2000);
            assertEquals(0,r.ticket());
            if(i%40==0)assertEquals(g.snapshot(),HyperDogAtGame.restore(g.snapshot()).snapshot());
        }
    }
}
