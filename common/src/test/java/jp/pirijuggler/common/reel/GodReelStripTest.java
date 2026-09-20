package jp.pirijuggler.common.reel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodReelStripTest {
    @Test void targetAlwaysShowsRequestedSymbol(){
        for(int reel=0;reel<3;reel++)for(var symbol:GodReelStrip.Symbol.values())for(int pressed=0;pressed<21;pressed++){
            int target=GodReelStrip.targetFor(reel,symbol,pressed);
            assertEquals(symbol,GodReelStrip.symbol(reel,target));
            assertTrue(GodReelStrip.slip(pressed,target)<=8);
        }
    }
}
