package jp.pirijuggler.paper.game.pachinko;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PachinkoPhase09Test {
    @Test void lockedBorderAndReferenceRotationReportExpectedRates(){
        double border=PachinkoSpec.equivalentBorderSpinsPer1000Yen();
        assertEquals(14.9039029,border,1e-6);
        assertEquals(100.0,PachinkoSpec.payoutRatePercent(border),1e-9);
        assertEquals(115.09,PachinkoSpec.payoutRatePercent(17.0),0.02);
    }
    @Test void simulatorUsesProductionProbabilities(){
        var r=PachinkoSimulator.simulate(1_000_000,17.0,new Random(907319L));
        assertEquals(.60,(double)r.rushEntries()/r.initialJackpots(),.002);
        assertEquals(.81,(double)r.rightWins()/(r.rightWins()+r.rushEntries()),.002);
        assertEquals(.03,(double)r.right3000()/r.rightWins(),.001);
        assertEquals(PachinkoSpec.payoutRatePercent(17.0),r.observedPayoutRatePercent(),.5);
        assertEquals(PachinkoSpec.equivalentBorderSpinsPer1000Yen(),r.theoreticalBorder(),1e-12);
        assertEquals(17.0,r.rotation(),1e-12);
    }
    @Test void statisticsRoundTripAndLegacyJsonDefaultsToZero(){
        var s=new PachinkoStatistics(9,3,6,6,20,19,1);
        var r=new PachinkoRuntime(PachinkoRuntime.Mode.RUSH,10,250,250,17,4,PachinkoRuntime.Presentation.IDLE,true,PachinkoRuntime.InitialOutcome.RUSH_1500,true,20,PachinkoRuntime.RightOutcome.NONE,1500,33000,s,123);
        assertEquals(r,PachinkoRuntime.fromJson(r.toJsonString()));
        assertEquals(PachinkoStatistics.empty(),PachinkoRuntime.fromJson("{}").statistics());
    }
    @Test void dataUiSeparatesObservedRotationFromTheoreticalBorder(){
        var runtime=new PachinkoRuntime(PachinkoRuntime.Mode.NORMAL,10,250,250,17,4,PachinkoRuntime.Presentation.IDLE,false,PachinkoRuntime.InitialOutcome.NONE,false,0,PachinkoRuntime.RightOutcome.NONE,0,0,new PachinkoStatistics(2,1,1,1,3,3,0),123);
        var lines=PachinkoDataText.detail(44,runtime);
        assertTrue(lines.stream().anyMatch(x->x.equals("OBSERVED ROTATION 17.0000 starts/1000")));
        assertTrue(lines.stream().anyMatch(x->x.startsWith("THEORETICAL BORDER 14.9039")));
    }
    @Test void productionCountersResetOnlyThroughExplicitReset(){
        var runtime=new PachinkoRuntime(PachinkoRuntime.Mode.RUSH,10,250,250,17,4,PachinkoRuntime.Presentation.IDLE,true,PachinkoRuntime.InitialOutcome.RUSH_1500,true,20,PachinkoRuntime.RightOutcome.NONE,0,33000,new PachinkoStatistics(9,3,6,6,20,19,1),123);
        assertEquals(runtime.statistics(),PachinkoRuntime.fromJson(runtime.toJsonString()).statistics());
        var reset=runtime.resetStatistics(456);
        assertEquals(PachinkoStatistics.empty(),reset.statistics());
        assertEquals(0,reset.totalStarts());
        assertEquals(0,reset.totalFired());
        assertEquals(0,reset.cumulativePayout());
        assertEquals(4,reset.ballSequenceId());
        assertEquals(456,reset.lastActivity());
    }
}
