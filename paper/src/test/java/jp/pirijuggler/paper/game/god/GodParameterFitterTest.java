package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodParameterFitterTest {
    private static final GodPayoutCurve CURVE=GodPayoutCurve.kisekiBenchmark();

    @Test void solvesNormalGgRateFromResidualEv() {
        var inputs=new GodParameterFitter.Inputs(-1.5,700,2800,.05,.10,.02);
        var r=GodParameterFitter.fit(CURVE,1,inputs);
        double reconstructed=r.fixedNetPerGame()+r.normalGgInitialRatePerGame()*inputs.expectedNetPerNormalGgInitial();
        assertEquals(r.targetNetPerGame(),reconstructed,1e-12);
        assertEquals(1.0/r.normalGgInitialRatePerGame(),r.normalGgInitialOdds(),1e-12);
    }

    @Test void higherTargetNeedsHigherInitialRateWhenOtherBudgetsAreEqual() {
        var inputs=new GodParameterFitter.Inputs(-1.5,700,2800,.05,.10,.02);
        var low=GodParameterFitter.fit(CURVE,1,inputs);
        var high=GodParameterFitter.fit(CURVE,6,inputs);
        assertTrue(high.normalGgInitialRatePerGame()>low.normalGgInitialRatePerGame());
        assertTrue(high.normalGgInitialOdds()<low.normalGgInitialOdds());
    }

    @Test void callerCanUseDifferentTargetCurve() {
        var inputs=new GodParameterFitter.Inputs(-1.5,700,2800,.05,.10,.02);
        var a=GodParameterFitter.fit(GodPayoutCurve.of(97,99,101,103,105,107),1,inputs);
        var b=GodParameterFitter.fit(GodPayoutCurve.of(99,101,103,105,107,109),1,inputs);
        assertTrue(b.normalGgInitialRatePerGame()>a.normalGgInitialRatePerGame());
    }

    @Test void rejectsImpossibleBudget() {
        var inputs=new GodParameterFitter.Inputs(10,700,2800,0,0,0);
        assertThrows(IllegalArgumentException.class,()->GodParameterFitter.fit(CURVE,1,inputs));
    }
}
