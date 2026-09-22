package jp.pirijuggler.paper.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JugglerGodTheoryTest {
    @Test void bonusStockBranchingRemainsSubcriticalAcrossAllSettings() throws Exception {
        var weights=new RoleWeights(GameFixture.defaults());
        double previous=0;
        for(int setting=1;setting<=6;setting++){
            var r=JugglerGodTheory.calculate(weights,setting,1_000_000);
            assertTrue(r.reproductionRadius()<1.0,"setting "+setting);
            assertTrue(r.bigsFromOneGodBigStock()>1.0);
            assertTrue(r.regsFromOneGodBigStock()>0.0);
            assertTrue(r.godInGodHitsFromOneGodBigStock()>0.0);
            assertTrue(r.outerGodExpectedTotalBigs()>=r.outerGodExpectedBaseBigSeeds());
            assertTrue(r.outerGodExpectedNetBeforeHeaven()>0.0);
            assertTrue(r.reproductionRadius()>=previous);
            previous=r.reproductionRadius();
        }
    }

    @Test void godInGodSevenStockMeaningfullyRaisesOneGodBigExpectation() throws Exception {
        var weights=new RoleWeights(GameFixture.defaults());
        var r=JugglerGodTheory.calculate(weights,1,1_000_000);
        assertTrue(r.bigsFromOneGodBigStock()>1.10);
        assertTrue(r.godInGodHitsFromOneGodBigStock()<0.01);
    }
}
