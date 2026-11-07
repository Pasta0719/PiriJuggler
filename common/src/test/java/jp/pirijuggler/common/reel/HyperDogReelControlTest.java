package jp.pirijuggler.common.reel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogReelControlTest {
    @Test void allStopOrdersAllInputIndicesAndAllRoles() {
        HyperDogReelAcceptance.run();
    }
    @Test void physicalStripSeamAndFixedSpecialStops() {
        for (Reel reel : Reel.values()) {
            assertEquals(HyperDogReelControl.icon(reel,20),
                    HyperDogReelControl.visible(reel,0,-1));
            assertEquals(HyperDogReelControl.icon(reel,0),
                    HyperDogReelControl.visible(reel,20,1));
        }
        assertEquals(HyperDogReelControl.Icon.RED7,HyperDogReelControl.icon(Reel.LEFT,0));
        assertEquals(HyperDogReelControl.Icon.RED7,HyperDogReelControl.icon(Reel.CENTER,1));
        assertEquals(HyperDogReelControl.Icon.RED7,HyperDogReelControl.icon(Reel.RIGHT,15));
        assertTrue(HyperDogReelControl.legalFinal(
                HyperDogReelControl.Role.AT_ENTRY_RED7,new int[]{0,1,15}));
        assertTrue(HyperDogReelControl.legalFinal(
                HyperDogReelControl.Role.CZ_WIN,new int[]{3,9,3}));
        assertTrue(HyperDogReelControl.legalFinal(
                HyperDogReelControl.Role.BAR,new int[]{9,5,7}));
        assertFalse(HyperDogReelControl.legalFinal(
                HyperDogReelControl.Role.MISS,new int[]{0,1,15}));
    }
    @Test void rejectInvalidDoubleStopsAndIndices() {
        var control=new HyperDogReelControl();
        var empty=SkillStopHistory.empty();
        assertThrows(IllegalArgumentException.class,()->
                control.choose(HyperDogReelControl.Role.GRAPE,empty,Reel.LEFT,21));
        assertThrows(IllegalArgumentException.class,()->
                control.choose(HyperDogReelControl.Role.GRAPE,empty,Reel.LEFT,-1));
        var initial=control.choose(HyperDogReelControl.Role.GRAPE,empty,Reel.LEFT,1);
        var stopped=empty.append(Reel.LEFT,1,initial.stopIndex());
        assertThrows(IllegalArgumentException.class,()->
                control.choose(HyperDogReelControl.Role.GRAPE,stopped,Reel.LEFT,1));
    }
}
