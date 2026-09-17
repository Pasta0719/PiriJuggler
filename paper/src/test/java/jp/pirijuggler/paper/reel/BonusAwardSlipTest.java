package jp.pirijuggler.paper.reel;

import jp.pirijuggler.common.reel.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BonusAwardSlipTest {
    private static final StopSolver SOLVER=new StopSolver(new StopCatalogue());
    private static final List<List<Reel>> ORDERS=List.of(
            List.of(Reel.LEFT,Reel.CENTER,Reel.RIGHT),
            List.of(Reel.LEFT,Reel.RIGHT,Reel.CENTER),
            List.of(Reel.CENTER,Reel.LEFT,Reel.RIGHT),
            List.of(Reel.CENTER,Reel.RIGHT,Reel.LEFT),
            List.of(Reel.RIGHT,Reel.LEFT,Reel.CENTER),
            List.of(Reel.RIGHT,Reel.CENTER,Reel.LEFT));

    private record Pair(DisplayRole base,DisplayRole entry){}

    @Test void awardGameNeverPullsSevenOrBarBeyondFourFrames() {
        var pairs=List.of(
                new Pair(DisplayRole.BONUS,DisplayRole.BIG_ENTRY),
                new Pair(DisplayRole.BONUS,DisplayRole.REG_ENTRY),
                new Pair(DisplayRole.BONUS_CHERRY,DisplayRole.BIG_ENTRY),
                new Pair(DisplayRole.BONUS_CHERRY,DisplayRole.REG_ENTRY),
                new Pair(DisplayRole.PIERO_BONUS,DisplayRole.BIG_ENTRY),
                new Pair(DisplayRole.PIERO_BONUS,DisplayRole.REG_ENTRY));

        for(var pair:pairs)for(var order:ORDERS)for(int p1=0;p1<21;p1++)for(int p2=0;p2<21;p2++)for(int p3=0;p3<21;p3++){
            int[] presses={p1,p2,p3};
            StopTriplet stopped=new StopTriplet(0,0,0);int mask=0;
            for(int i=0;i<3;i++){
                Reel reel=order.get(i);
                var choice=SOLVER.choose(pair.base(),pair.entry(),mask,stopped,reel,presses[i],false,false,false,true);
                if(choice.slip()>4){
                    assertFalse(showsBonusSymbol(reel,choice.stopIndex()),
                            pair+" order="+order+" press="+presses[i]+" slip="+choice.slip()+" stop="+choice.stopIndex());
                }
                stopped=stopped.with(reel,choice.stopIndex());mask|=reel.bit();
            }
        }
    }

    private static boolean showsBonusSymbol(Reel reel,int stopIndex){
        for(int row=-1;row<=1;row++){
            Symbol symbol=FixedReels.row(reel,stopIndex,row);
            if(symbol==Symbol.SEVEN||symbol==Symbol.BAR)return true;
        }
        return false;
    }
}
