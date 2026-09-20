package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodParameterFitterTest {
    @Test void solvesNormalGgRateFromResidualEv() {
        var inputs=new GodParameterFitter.Inputs(
                -1.5,
                700,
                2800,
                .05,
                .03,
                .10,
                .02
        );
        var r=GodParameterFitter.fit(1,inputs);
        double reconstructed=r.fixedNetPerGame()+r.normalGgInitialRatePerGame()*inputs.expectedNetPerNormalGgInitial();
        assertEquals(r.targetNetPerGame(),reconstructed,1e-12);
        assertEquals(1.0/r.normalGgInitialRatePerGame(),r.normalGgInitialOdds(),1e-12);
    }

    @Test void higherTargetNeedsHigherInitialRateWhenOtherBudgetsAreEqual() {
        var inputs=new GodParameterFitter.Inputs(-1.5,700,2800,.05,.03,.10,.02);
        var low=GodParameterFitter.fit(1,inputs);
        var high=GodParameterFitter.fit(6,inputs);
        assertTrue(high.normalGgInitialRatePerGame()>low.normalGgInitialRatePerGame());
        assertTrue(high.normalGgInitialOdds()<low.normalGgInitialOdds());
    }

    @Test void rejectsImpossibleBudget() {
        var inputs=new GodParameterFitter.Inputs(10,700,2800,0,0,0,0);
        assertThrows(IllegalArgumentException.class,()->GodParameterFitter.fit(1,inputs));
    }
}
