package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.reel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarConfirmationTest extends GameFixture {
    @Test void formalReachEyesAreReservedForBonusOrPremiumBCandidatesOnAllFivePaylines(){
        int[] perLine=new int[5];int raw=0,pure=0,cherry=0,premiumB=0;
        for(var e:SOLVER.catalogue().evaluations()){
            int reach=StopCatalogue.reachLines(e.stops());assertEquals(reach,e.winningReachLines());
            if(reach!=0){
                raw++;for(var line:Payline.values())if((reach&(1<<line.ordinal()))!=0)perLine[line.ordinal()]++;
                for(var role:DisplayRole.values())if(role!=DisplayRole.BONUS&&role!=DisplayRole.BONUS_CHERRY&&role!=DisplayRole.PREMIUM_B)assertFalse(e.valid(role),e.stops()+" "+role);
                if(e.valid(DisplayRole.BONUS))pure++;
                if(e.valid(DisplayRole.BONUS_CHERRY))cherry++;
                if(e.valid(DisplayRole.PREMIUM_B))premiumB++;
            }
        }
        assertEquals(160,raw);assertEquals(124,pure);assertEquals(12,cherry);assertTrue(premiumB>0);for(int count:perLine)assertTrue(count>0);
        assertEquals(5126,SOLVER.catalogue().candidates(DisplayRole.MISS).size());assertEquals(1287,SOLVER.catalogue().candidates(DisplayRole.CHERRY).size());assertEquals(124,SOLVER.catalogue().candidates(DisplayRole.BONUS).size());assertEquals(12,SOLVER.catalogue().candidates(DisplayRole.BONUS_CHERRY).size());assertEquals(782,SOLVER.catalogue().candidates(DisplayRole.PREMIUM_B).size());
        assertFalse(StopCatalogue.isReachPattern(Symbol.SEVEN,Symbol.SEVEN,Symbol.SEVEN));assertFalse(StopCatalogue.isReachPattern(Symbol.SEVEN,Symbol.SEVEN,Symbol.BAR));
        assertTrue(StopCatalogue.isReachPattern(Symbol.BAR,Symbol.BAR,Symbol.BAR));assertTrue(StopCatalogue.isReachPattern(Symbol.PIERO,Symbol.BAR,Symbol.PIERO));
    }

    @Test void barBarBarIsFilteredForOrdinaryBonusButAvailableForPremiumAndPremiumF(){
        assertFalse(SOLVER.catalogue().candidates(DisplayRole.BONUS).stream().filter(e->e.winningBarConfirmationLines()!=0).toList().isEmpty());
        boolean premiumBarSeen=false,premiumFBarSeen=false;
        for(var order:ReelVerification.orders())for(int p1=0;p1<21;p1++)for(int p2=0;p2<21;p2++)for(int p3=0;p3<21;p3++){
            int[] presses={p1,p2,p3};
            StopTriplet ordinary=run(order,presses,false,false);
            assertEquals(0,SOLVER.catalogue().evaluation(ordinary).winningBarConfirmationLines(),"ordinary bonus must not stop BAR-BAR-BAR: "+order+" "+p1+","+p2+","+p3);
            if(!premiumBarSeen){
                StopTriplet premium=run(order,presses,false,true);
                premiumBarSeen=SOLVER.catalogue().evaluation(premium).winningBarConfirmationLines()!=0;
            }
            if(!premiumFBarSeen){
                StopTriplet premiumF=run(order,presses,true,true);
                premiumFBarSeen=SOLVER.catalogue().evaluation(premiumF).winningBarConfirmationLines()!=0;
            }
        }
        assertTrue(premiumBarSeen,"premium A-E path must retain a BAR-BAR-BAR stop path");
        assertTrue(premiumFBarSeen,"premium F must retain a BAR-BAR-BAR stop path while still enforcing its SEVEN non-tenpai rule");
    }

    private static StopTriplet run(java.util.List<Reel> order,int[] presses,boolean premiumF,boolean allowBarConfirmation){
        StopTriplet stopped=new StopTriplet(0,0,0);int mask=0;
        for(int i=0;i<3;i++){
            Reel reel=order.get(i);
            var choice=SOLVER.choose(DisplayRole.BONUS,null,mask,stopped,reel,presses[i],premiumF,allowBarConfirmation,false);
            stopped=stopped.with(reel,choice.stopIndex());mask|=reel.bit();
        }
        return stopped;
    }
}
