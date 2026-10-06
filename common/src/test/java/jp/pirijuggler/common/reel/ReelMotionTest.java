package jp.pirijuggler.common.reel;
import com.google.gson.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReelMotionTest {
    @Test void premiumStartsOppositeNormalAndThenReturnsToNormalDirection(){
        assertTrue(ReelMotion.delta(ReelMotion.Profile.NORMAL,.4)<ReelMotion.delta(ReelMotion.Profile.NORMAL,.2));
        assertTrue(ReelMotion.delta(ReelMotion.Profile.REVERSE_500MS,.4)>ReelMotion.delta(ReelMotion.Profile.REVERSE_500MS,.2));
        for(var profile:ReelMotion.Profile.values())assertEquals(-2.1,ReelMotion.delta(profile,1.1)-ReelMotion.delta(profile,1),1e-9);
        assertEquals(20,ReelMotion.slip(1,0));assertEquals(1,ReelMotion.slip(20,0));
    }
    @Test void normalStopEndpointNeverReversesAndLatePacketsCannotForceVisualOverspeed(){
        assertEquals(-1,ReelMotion.normalStopEndpoint(.5,20),1e-9);
        assertEquals(5,ReelMotion.normalStopEndpoint(5.2,5),1e-9);
        assertEquals(0,ReelMotion.normalStopEndpoint(20.1,0),1e-9);
        for(int target=0;target<21;target++)for(int step=0;step<210;step++){
            double from=step/10.0,endpoint=ReelMotion.normalStopEndpoint(from,target);
            assertTrue(endpoint<=from);assertEquals(target,ReelMotion.wrap(endpoint),1e-9);
            int ms=ReelMotion.visualDurationMs(from,endpoint,80);
            assertTrue((from-endpoint)/(ms/1000.0)<=ReelMotion.NORMAL_SPEED+1e-7);
        }
        assertEquals(958,ReelMotion.visualDurationMs(20.1,0,80));
        assertEquals(380,ReelMotion.visualDurationMs(8,2,380));
    }
    @Test void exactCompiledArraysAndThreeRowsMatchSpec() throws Exception {
        var lock=JsonParser.parseString(Files.readString(Path.of(System.getProperty("piri.specRoot"),"docs/spec-lock.json"))).getAsJsonObject().getAsJsonObject("reelArrays");
        for(var reel:Reel.values()){var a=lock.getAsJsonArray(reel.name()+"_REEL");assertEquals(21,FixedReels.sequence(reel).size());for(int i=0;i<21;i++)assertEquals(a.get(i).getAsString(),FixedReels.at(reel,i).name());
            assertEquals(FixedReels.at(reel,20),FixedReels.row(reel,0,-1));assertEquals(FixedReels.at(reel,0),FixedReels.row(reel,20,1));assertThrows(UnsupportedOperationException.class,()->FixedReels.sequence(reel).set(0,Symbol.BAR));}
    }
    @Test void motionBoundariesAndReverseWrapAreExact(){
        assertEquals(0,ReelMotion.delta(ReelMotion.Profile.NORMAL,.150));assertEquals(-3.675,ReelMotion.delta(ReelMotion.Profile.NORMAL,.500),1e-12);
        assertEquals(6,ReelMotion.delta(ReelMotion.Profile.REVERSE_500MS,.5),1e-12);assertEquals(2.85,ReelMotion.delta(ReelMotion.Profile.REVERSE_500MS,.8),1e-12);
        assertEquals(6,ReelMotion.phase(ReelMotion.Profile.REVERSE_500MS,0,.5),1e-12);assertEquals(6,ReelMotion.phase(ReelMotion.Profile.RESUME_NORMAL,6,1),1e-12);
        for(var p:ReelMotion.Profile.values())for(double boundary:new double[]{.150,.500,.800})assertEquals(ReelMotion.delta(p,boundary-1e-10),ReelMotion.delta(p,boundary+1e-10),1e-7);
    }
    @Test void rttUsesClampedFullPingAndCannotGoBeforeStart(){
        assertEquals(1000,ReelMotion.effectiveMillis(0,1_000_000_000,-100));assertEquals(750,ReelMotion.effectiveMillis(0,1_000_000_000,10000));assertEquals(0,ReelMotion.effectiveMillis(0,100_000_000,250));
        assertEquals(8,ReelMotion.pressedIndex(ReelMotion.Profile.NORMAL,8,0,400_000_000,250));assertEquals(4,ReelMotion.pressedIndex(ReelMotion.Profile.NORMAL,8,0,600_000_000,100));
        assertEquals(1,ReelMotion.slip(0,1));assertEquals(6,ReelMotion.slip(2,8));assertEquals(380,ReelMotion.durationMs(6));assertEquals(1080,ReelMotion.durationMs(20));assertThrows(IllegalArgumentException.class,()->ReelMotion.durationMs(21));
    }
}
