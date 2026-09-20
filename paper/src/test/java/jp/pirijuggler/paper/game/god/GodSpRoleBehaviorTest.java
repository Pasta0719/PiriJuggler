package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodSpRoleBehaviorTest {
    @Test void spBaselineIsLocked() {
        assertEquals(65536.0, GodKisekiRoleTable.piriDenominator(GodRole.SP));
        assertEquals(0.50, GodSpRoleBehavior.STOCK_AWARD_CHANCE);
        assertEquals(0.80, GodSpRoleBehavior.LOOP_CONTINUATION);
    }

    @Test void spExpectedValueUsesOneStockPlusEightyPercentLoopWhenAwarded() {
        assertEquals(4.0, GodSpRoleBehavior.expectedLoopStocksConditionalOnAward(),1e-12);
        assertEquals(2.5, GodSpRoleBehavior.expectedTotalStocksPerSp(),1e-12);
        assertEquals(875.0, GodSpRoleBehavior.expectedNetMedalsPerSp(),1e-9);
        assertEquals(875.0/65536.0, GodSpRoleBehavior.expectedNetContributionPerEligibleGame(),1e-12);
    }
}
