package jp.pirijuggler.common.reel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodReelStripTest {
    @Test void publishedStripHasTwentyStops(){
        assertEquals(20,GodReelStrip.STOPS);
        assertArrayEquals(
                new GodReelStrip.Symbol[]{GodReelStrip.Symbol.GOD,GodReelStrip.Symbol.BLUE7,GodReelStrip.Symbol.RED7,GodReelStrip.Symbol.MILLION,GodReelStrip.Symbol.YELLOW7},
                new GodReelStrip.Symbol[]{GodReelStrip.symbol(0,0),GodReelStrip.symbol(0,1),GodReelStrip.symbol(0,2),GodReelStrip.symbol(0,3),GodReelStrip.symbol(0,4)}
        );
        assertEquals(GodReelStrip.Symbol.DEKA_MILLION_BOTTOM,GodReelStrip.symbol(0,12));
        assertEquals(GodReelStrip.Symbol.DEKA_MILLION_TOP,GodReelStrip.symbol(0,13));
        assertEquals(GodReelStrip.Symbol.DEKA_MILLION_BOTTOM,GodReelStrip.symbol(2,2));
        assertEquals(GodReelStrip.Symbol.DEKA_MILLION_TOP,GodReelStrip.symbol(2,3));
    }

    @Test void centerStripRepeatsPublishedFiveStopPattern(){
        var pattern=new GodReelStrip.Symbol[]{GodReelStrip.Symbol.GOD,GodReelStrip.Symbol.YELLOW7,GodReelStrip.Symbol.BLUE7,GodReelStrip.Symbol.YELLOW7,GodReelStrip.Symbol.RED7};
        for(int block=0;block<4;block++)for(int i=0;i<5;i++)assertEquals(pattern[i],GodReelStrip.symbol(1,block*5+i));
    }

    @Test void requestedTargetsAlwaysLandOnAnExistingActualStripSymbol(){
        for(int reel=0;reel<3;reel++)for(int pressed=0;pressed<GodReelStrip.STOPS;pressed++){
            var desired=GodReelStrip.symbolForRole("GOD",reel);
            int target=GodReelStrip.targetFor(reel,desired,pressed);
            assertEquals(desired,GodReelStrip.symbol(reel,target));
            assertTrue(GodReelStrip.slip(pressed,target)<GodReelStrip.STOPS);
        }
    }
}
