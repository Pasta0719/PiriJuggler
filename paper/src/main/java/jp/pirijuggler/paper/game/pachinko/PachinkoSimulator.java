package jp.pirijuggler.paper.game.pachinko;

import java.util.random.RandomGenerator;

/** Phase 09 simulator/reporting model. Uses only production PachinkoSpec probabilities. */
public final class PachinkoSimulator {
    private PachinkoSimulator(){}

    public record Result(long initialJackpots,long initial450,long initial1500,long rushEntries,long rightWins,long right1500,long right3000,long payoutBalls,double rotation,double theoreticalBorder,double expectedPayoutRatePercent,double observedPayoutRatePercent,double differenceBalls){}

    public static Result simulate(long initialJackpots,double measuredSpinsPer1000Yen,RandomGenerator random){
        if(initialJackpots<0)throw new IllegalArgumentException("initialJackpots");
        double expected=PachinkoSpec.payoutRatePercent(measuredSpinsPer1000Yen);
        long n450=0,n1500=0,rush=0,rw=0,r1500=0,r3000=0,payout=0;
        for(long i=0;i<initialJackpots;i++){
            if(random.nextDouble()<PachinkoSpec.RUSH_ENTRY_RATE){
                n1500++;rush++;payout+=PachinkoSpec.RUSH_INITIAL_PAYOUT;
                while(PachinkoRushPresentation.selectedContinues(random)){
                    rw++;
                    if(random.nextDouble()<PachinkoSpec.RIGHT_3000_RATE){r3000++;payout+=PachinkoSpec.RIGHT_3000_PAYOUT;}
                    else {r1500++;payout+=PachinkoSpec.RIGHT_1500_PAYOUT;}
                }
            } else {n450++;payout+=PachinkoSpec.NORMAL_INITIAL_PAYOUT;}
        }
        double netCost=PachinkoSpec.BALLS_PER_1000_YEN/measuredSpinsPer1000Yen-PachinkoSpec.START_PRIZE_BALLS;
        double input=initialJackpots*PachinkoSpec.INITIAL_JACKPOT_DENOMINATOR*netCost;
        double observed=input==0?0:payout/input*100.0;
        return new Result(initialJackpots,n450,n1500,rush,rw,r1500,r3000,payout,measuredSpinsPer1000Yen,PachinkoSpec.equivalentBorderSpinsPer1000Yen(),expected,observed,payout-input);
    }

    public static double observedDifferenceBalls(PachinkoRuntime runtime){
        if(runtime.totalStarts()==0)return runtime.cumulativePayout();
        double rotation=runtime.measuredSpinsPer1000Yen();
        double netCost=PachinkoSpec.BALLS_PER_1000_YEN/rotation-PachinkoSpec.START_PRIZE_BALLS;
        return runtime.cumulativePayout()-runtime.totalStarts()*netCost;
    }
}
