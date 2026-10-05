package jp.pirijuggler.paper.game.pachinko;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PachinkoInitialOddsTest {
    @Test void initialVOddsRemainOneIn319IndependentOfRouting() {
        var random=new Random(319_2026L);
        int trials=3_190_000,hits=0;
        for(int i=0;i<trials;i++)if(PachinkoGameEngine.rollInitialV(random))hits++;
        double observed=(double)hits/trials;
        assertEquals(1.0/319.0,observed,0.00012);
        assertEquals(17.0,PachinkoRouting.reference().expectedSpinsPer1000Yen(),0.0);
        assertEquals(25.0,new PachinkoRouting(25).expectedSpinsPer1000Yen(),0.0);
    }
}
