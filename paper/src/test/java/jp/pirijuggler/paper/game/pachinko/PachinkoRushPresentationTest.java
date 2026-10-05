package jp.pirijuggler.paper.game.pachinko;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PachinkoRushPresentationTest {
    @Test void selectedProbabilityLoopIsExactlyLocked81Percent() {
        var selected=PachinkoRushPresentation.selected();
        assertInstanceOf(PachinkoRushPresentation.ProbabilityLoop.class,selected);
        assertEquals(PachinkoSpec.RUSH_CONTINUATION_RATE,selected.effectiveContinuation(),1e-12);
        assertEquals(0.81,selected.effectiveContinuation(),1e-12);
    }

    @Test void abstractionSupportsFiniteStAndLtChallengeFormulas() {
        var st=new PachinkoRushPresentation.FiniteSt(10,0.1);
        assertEquals(1-Math.pow(0.9,10),st.effectiveContinuation(),1e-12);
        var lt=new PachinkoRushPresentation.LtChallenge(0.6,0.5,0.7);
        assertEquals(0.6+0.4*0.5*0.7,lt.effectiveContinuation(),1e-12);
    }

    @Test void simulationUsesTheSameSelectedProductionParameters() {
        var random=new Random(810319L);
        int trials=1_000_000,wins=0;
        for(int i=0;i<trials;i++)if(PachinkoRushPresentation.selectedContinues(random))wins++;
        assertEquals(PachinkoRushPresentation.selected().effectiveContinuation(),(double)wins/trials,0.002);
    }

    @Test void productionLockedStrategyHasNoAlternativeContinuationRate() {
        var random=new Random(810320L);
        int trials=1_000_000,wins=0;
        for(int i=0;i<trials;i++)if(PachinkoGameEngine.LOCKED_RUSH_STRATEGY.decide(random)!=PachinkoRuntime.RightOutcome.OUT)wins++;
        assertEquals(PachinkoRushPresentation.selected().effectiveContinuation(),(double)wins/trials,0.002);
    }
}
