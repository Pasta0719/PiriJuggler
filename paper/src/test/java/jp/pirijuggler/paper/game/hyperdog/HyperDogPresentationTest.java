package jp.pirijuggler.paper.game.hyperdog;

import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogPresentationTest {
    @Test void allGuaranteesAndMonotonicStagesHoldForIndependentSeeds() {
        long[] awards={10,19,20,29,30,49,50,99,100,199,200,499,500,999,1999,2000,2001,50000};
        for(long award:awards)for(int seed=0;seed<3000;seed++){
            var result=HyperDogPresentation.choose(17,award,seed*9973L+award);
            assertTrue(HyperDogPresentation.eligible(result.finalColor(),award));
            assertTrue(HyperDogPresentation.eligible(result.flashColor(),award));
            assertTrue(result.flashColor().ordinal()>=result.finalColor().ordinal());
            assertTrue(result.finalCount()>=1&&result.finalCount()<=3);
            assertEquals(1,result.lampCount(0));
            assertTrue(result.lampCount(1)>=result.lampCount(0));
            assertTrue(result.lampCount(2)>=result.lampCount(1));
            assertEquals(result.finalCount(),result.lampCount(3));
            assertTrue(result.lampColor(0).ordinal()<=result.lampColor(1).ordinal());
            assertTrue(result.lampColor(1).ordinal()<=result.lampColor(2).ordinal());
            if(award!=2000){
                assertNotEquals(HyperDogPresentation.Color.RAINBOW,result.finalColor());
                assertNotEquals(HyperDogPresentation.Color.RAINBOW,result.flashColor());
            }
            var before=result.publicStage(2,false,0);
            assertFalse(before.has("newAward"));
            assertFalse(before.has("flashColor"));
            assertFalse(before.has("flashEventId"));
            var after=result.publicStage(3,true,1000);
            assertEquals(award,after.get("newAward").getAsLong());
            assertEquals(result.id(),after.get("flashEventId").getAsLong());
            assertEquals(after.toString(),result.publicStage(3,true,1000).toString());
        }
    }
    @Test void distributionsMatchApprovedTablesWithinSamplingError(){
        int[] redAndRainbow=new int[7];
        int[] count=new int[3];
        int total=50000;
        for(int i=0;i<total;i++){
            var p=HyperDogPresentation.choose(i+1,2000,i*32887L+123);
            redAndRainbow[p.finalColor().ordinal()]++;
            count[p.finalCount()-1]++;
        }
        assertEquals(0,redAndRainbow[0]+redAndRainbow[1]+redAndRainbow[2]+redAndRainbow[3]);
        assertEquals(0.05,redAndRainbow[4]/(double)total,0.012);
        assertEquals(0.85,redAndRainbow[5]/(double)total,0.020);
        assertEquals(0.10,redAndRainbow[6]/(double)total,0.015);
        assertEquals(0.15,count[0]/(double)total,0.02);
        assertEquals(0.35,count[1]/(double)total,0.02);
        assertEquals(0.50,count[2]/(double)total,0.02);
    }
    @Test void zeroAndNegativeCannotCreateFalseAwardPresentation(){
        assertThrows(IllegalArgumentException.class,()->HyperDogPresentation.choose(1,0,9));
        assertThrows(IllegalArgumentException.class,()->HyperDogPresentation.choose(1,9,9));
        assertThrows(IllegalArgumentException.class,()->HyperDogPresentation.choose(0,500,9));
    }
}
