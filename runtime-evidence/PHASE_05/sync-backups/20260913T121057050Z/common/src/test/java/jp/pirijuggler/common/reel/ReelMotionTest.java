package jp.pirijuggler.common.reel;
import com.google.gson.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReelMotionTest {
    @Test void exactCompiledArraysAndThreeRowsMatchSpec() throws Exception {
        var lock=JsonParser.parseString(Files.readString(Path.of(System.getProperty("piri.specRoot"),"docs/spec-lock.json"))).getAsJsonObject().getAsJsonObject("reelArrays");
        for(var reel:Reel.values()){var a=lock.getAsJsonArray(reel.name()+"_REEL");assertEquals(21,FixedReels.sequence(reel).size());for(int i=0;i<21;i++)assertEquals(a.get(i).getAsString(),FixedReels.at(reel,i).name());
            assertEquals(FixedReels.at(reel,20),FixedReels.row(reel,0,-1));assertEquals(FixedReels.at(reel,0),FixedReels.row(reel,20,1));assertThrows(UnsupportedOperationException.class,()->FixedReels.sequence(reel).set(0,Symbol.BAR));}
    }
    @Test void motionBoundariesAndReverseWrapAreExact(){
        assertEquals(0,ReelMotion.delta(ReelMotion.Profile.NORMAL,.150));assertEquals(3.15,ReelMotion.delta(ReelMotion.Profile.NORMAL,.500),1e-12);
        assertEquals(-6,ReelMotion.delta(ReelMotion.Profile.REVERSE_500MS,.5),1e-12);assertEquals(-3.3,ReelMotion.delta(ReelMotion.Profile.REVERSE_500MS,.8),1e-12);
        assertEquals(15,ReelMotion.phase(ReelMotion.Profile.REVERSE_500MS,0,.5),1e-12);assertEquals(3,ReelMotion.phase(ReelMotion.Profile.RESUME_NORMAL,6,1),1e-12);
        for(var p:ReelMotion.Profile.values())for(double boundary:new double[]{.150,.500,.800})assertEquals(ReelMotion.delta(p,boundary-1e-10),ReelMotion.delta(p,boundary+1e-10),1e-7);
    }
    @Test void rttUsesClampedFullPingAndCannotGoBeforeStart(){
        assertEquals(1000,ReelMotion.effectiveMillis(0,1_000_000_000,-100));assertEquals(750,ReelMotion.effectiveMillis(0,1_000_000_000,10000));assertEquals(0,ReelMotion.effectiveMillis(0,100_000_000,250));
        assertEquals(8,ReelMotion.pressedIndex(ReelMotion.Profile.NORMAL,8,0,400_000_000,250));assertEquals(11,ReelMotion.pressedIndex(ReelMotion.Profile.NORMAL,8,0,600_000_000,100));
        assertEquals(20,ReelMotion.slip(0,1));assertEquals(6,ReelMotion.slip(14,8));assertEquals(380,ReelMotion.durationMs(6));assertEquals(1080,ReelMotion.durationMs(20));assertThrows(IllegalArgumentException.class,()->ReelMotion.durationMs(21));
    }
}
