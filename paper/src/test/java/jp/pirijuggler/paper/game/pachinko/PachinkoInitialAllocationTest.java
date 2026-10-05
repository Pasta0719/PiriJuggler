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
}
