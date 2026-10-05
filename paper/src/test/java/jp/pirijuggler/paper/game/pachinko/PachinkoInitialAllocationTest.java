package jp.pirijuggler.paper.game.pachinko;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PachinkoInitialAllocationTest {
    @Test void rushAllocationConvergesToLockedFortySixtySplit() {
        var random=new Random(600_2026L);int rush=0,trials=1_000_000;
        for(int i=0;i<trials;i++)if(PachinkoGameEngine.rollRushEntry(random))rush++;
        assertEquals(0.60,(double)rush/trials,0.002);
        assertEquals(450,PachinkoSpec.NORMAL_INITIAL_PAYOUT);
        assertEquals(1500,PachinkoSpec.RUSH_INITIAL_PAYOUT);
    }
    @Test void rightEconomicsConvergeToLockedRatesAndMeanPayout() {
        var continuationRandom=new Random(810_2026L);var payoutRandom=new Random(30_2026L);
        int trials=1_000_000,continued=0,w3000=0;
        long payout=0;
        for(int i=0;i<trials;i++){
            if(PachinkoGameEngine.rollRushContinuation(continuationRandom)){
                continued++;
                if(PachinkoGameEngine.rollRight3000(payoutRandom)){w3000++;payout+=3000;}else payout+=1500;
            }
        }
        assertEquals(0.81,(double)continued/trials,0.002);
        assertEquals(0.03,(double)w3000/continued,0.001);
        assertEquals(1545.0,(double)payout/continued,2.0);
        assertEquals(1545.0,PachinkoSpec.averageRightPayout(),0.0);
    }

}
