package jp.pirijuggler.paper.game.pachinko;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PachinkoRoutingTest {
    @Test void referenceTargetIs17AndTuningOnlyChangesEntryFrequency() {
        assertEquals(17.0,PachinkoRouting.reference().expectedSpinsPer1000Yen(),1e-9);
        assertEquals(14.0,new PachinkoRouting(14).expectedSpinsPer1000Yen(),1e-9);
        assertEquals(18.0,new PachinkoRouting(18).expectedSpinsPer1000Yen(),1e-9);
        assertEquals(319.0,PachinkoSpec.INITIAL_JACKPOT_DENOMINATOR,0.0);
    }

    @Test void seededLongRunRoutingTracksPhysicalTarget() {
        var routing=PachinkoRouting.reference();
        var rng=new Random(1234567L);
        int starts=0;
        int fired=250_000;
        for(int i=0;i<fired;i++)if(routing.entersStart(rng))starts++;
        assertEquals(17.0,starts*250.0/fired,0.7);
    }

    @Test void rejectsInvalidTargets() {
        assertThrows(IllegalArgumentException.class,()->new PachinkoRouting(-1));
        assertThrows(IllegalArgumentException.class,()->new PachinkoRouting(251));
        assertThrows(IllegalArgumentException.class,()->new PachinkoRouting(Double.NaN));
    }
}
