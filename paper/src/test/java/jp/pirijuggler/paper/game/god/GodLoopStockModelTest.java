package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodLoopStockModelTest {
    @Test void geometricLoopExpectationsAreExact() {
        assertEquals(.01/.99,GodLoopStockModel.expectedAdditionalStocks(GodLoopStockModel.Loop.A_1),1e-15);
        assertEquals(1.0/3.0,GodLoopStockModel.expectedAdditionalStocks(GodLoopStockModel.Loop.B_25),1e-15);
        assertEquals(1.0,GodLoopStockModel.expectedAdditionalStocks(GodLoopStockModel.Loop.C_50),1e-15);
        assertEquals(4.0,GodLoopStockModel.expectedAdditionalStocks(GodLoopStockModel.Loop.D_80),1e-12);
    }

    @Test void publishedHeavenMixProducesWeightedExpectation() {
        var mix=GodLoopStockModel.heavenMix();
        double expected=.250*(.01/.99)+.250*(.25/.75)+.469*1.0+.031*4.0;
        assertEquals(expected,GodLoopStockModel.expectedAdditionalStocks(mix),1e-12);
        assertEquals(expected*350.0,GodLoopStockModel.expectedNetMedals(mix),1e-9);
    }

    @Test void superHeavenMixIsMuchMoreValuable() {
        assertEquals(1.75,GodLoopStockModel.expectedAdditionalStocks(GodLoopStockModel.superHeavenMix()),1e-12);
        assertEquals(612.5,GodLoopStockModel.expectedNetMedals(GodLoopStockModel.superHeavenMix()),1e-9);
    }

    @Test void historyMixesAreValid() {
        assertTrue(GodLoopStockModel.expectedAdditionalStocks(GodLoopStockModel.blue7ThreeToFourMix()) > 0);
        assertTrue(GodLoopStockModel.expectedAdditionalStocks(GodLoopStockModel.yellow7HistoryMix())
                > GodLoopStockModel.expectedAdditionalStocks(GodLoopStockModel.blue7ThreeToFourMix()));
    }
}
