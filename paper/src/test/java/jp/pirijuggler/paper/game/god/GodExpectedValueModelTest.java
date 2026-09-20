package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodExpectedValueModelTest {
    @Test void stationaryEconomicsAreComputedFromStateMix() {
        double[][] t={
                {.99,.01},
                {.02,.98}
        };
        var result=GodExpectedValueModel.solve(t,new double[]{3,3},new double[]{2.7,4.2});
        assertEquals(2.0/3.0,result.stationary()[0],1e-9);
        assertEquals(1.0/3.0,result.stationary()[1],1e-9);
        assertEquals(3.0,result.betPerGame(),1e-12);
        assertEquals(3.2,result.payoutPerGame(),1e-9);
        assertEquals(106.6666666667,result.payoutPercent(),1e-8);
    }

    @Test void rejectsBrokenProbabilityRows() {
        assertThrows(IllegalArgumentException.class,()->GodExpectedValueModel.solve(
                new double[][]{{.8,.3},{.2,.8}},new double[]{3,3},new double[]{2,4}));
    }
}
