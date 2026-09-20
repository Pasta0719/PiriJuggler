package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodNormalBaseEconomyTest {
    @Test void publishedFiftyMedalBaseConvertsExactly() {
        assertEquals(50.0/30.8,
                GodNormalBaseEconomy.netLossPerNormalGameSetting1(),1e-12);
        assertEquals(3.0-50.0/30.8,
                GodNormalBaseEconomy.grossReturnPerNormalGameSetting1(),1e-12);
        assertEquals((3.0-50.0/30.8)/3.0*100.0,
                GodNormalBaseEconomy.normalPlayReturnPercentSetting1(),1e-12);
    }

    @Test void normalBaseIsClearlyNotWholeMachinePayout() {
        assertTrue(GodNormalBaseEconomy.normalPlayReturnPercentSetting1()
                < GodProductionTarget.curve().payoutPercent(1));
    }
}
