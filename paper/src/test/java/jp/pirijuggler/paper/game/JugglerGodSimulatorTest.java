package jp.pirijuggler.paper.game;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JugglerGodSimulatorTest {
    @Test void fixedSeedWholeMachineSimulationIsDeterministicAndEconomicallyClosed() {
        var weights=new RoleWeights(GameFixture.defaults());
        var a=JugglerGodSimulator.run(weights,4,250_000,120_000,650_000,new SplittableRandom(0x4A5547474C455247L));
        var b=JugglerGodSimulator.run(weights,4,250_000,120_000,650_000,new SplittableRandom(0x4A5547474C455247L));
        assertEquals(a,b);
        assertTrue(a.leverGames()>=a.requestedLeverGames());
        assertTrue(a.paidLeverGames()<=a.leverGames());
        assertEquals(a.totalPayout()-a.totalBet(),a.net());
        assertEquals(100.0*a.totalPayout()/a.totalBet(),a.payoutPercent(),1e-12);
        assertTrue(a.godBig()>=a.god()*5);
        assertTrue(a.heavenEntries()>=a.god());
        assertTrue(a.heavenContinues()<=a.heavenEntries());
    }

    @Test void boundsRejectInvalidPhase03Inputs() {
        var weights=new RoleWeights(GameFixture.defaults());
        assertThrows(IllegalArgumentException.class,()->JugglerGodSimulator.run(weights,0,1,0,0,new SplittableRandom(1)));
        assertThrows(IllegalArgumentException.class,()->JugglerGodSimulator.run(weights,7,1,0,0,new SplittableRandom(1)));
        assertThrows(IllegalArgumentException.class,()->JugglerGodSimulator.run(weights,1,0,0,0,new SplittableRandom(1)));
        assertThrows(IllegalArgumentException.class,()->JugglerGodSimulator.run(weights,1,1,-1,0,new SplittableRandom(1)));
        assertThrows(IllegalArgumentException.class,()->JugglerGodSimulator.run(weights,1,1,0,1_000_001,new SplittableRandom(1)));
    }
}
