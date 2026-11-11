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
        for(Reel reel:Reel.values()){
            assertEquals(HyperDogReelControl.Icon.RED7,HyperDogReelControl.icon(reel,0));
            assertEquals(HyperDogReelControl.Icon.BAR,HyperDogReelControl.icon(reel,7));
            assertEquals(HyperDogReelControl.Icon.RAINBOW7,HyperDogReelControl.icon(reel,14));
            for(HyperDogReelControl.Icon premium : new HyperDogReelControl.Icon[]{
                    HyperDogReelControl.Icon.RED7,
                    HyperDogReelControl.Icon.BAR,
                    HyperDogReelControl.Icon.RAINBOW7}) {
                int count=0;
                for(int i=0;i<21;i++)if(HyperDogReelControl.icon(reel,i)==premium)count++;
                assertEquals(1,count,"one premium of each kind per physical reel");
            }
        }
        assertTrue(HyperDogReelControl.legalFinal(
                HyperDogReelControl.Role.AT_ENTRY_RED7,new int[]{0,0,0}));
        assertTrue(HyperDogReelControl.legalFinal(
                HyperDogReelControl.Role.CZ_WIN,new int[]{14,14,14}));
        assertTrue(HyperDogReelControl.legalFinal(
                HyperDogReelControl.Role.BAR,new int[]{7,7,7}));
        assertTrue(HyperDogReelControl.legalFinal(
                HyperDogReelControl.Role.BAR,new int[]{6,6,6}),
                "BAR lower-line hit allows left-middle CHERRY");
        assertEquals(HyperDogReelControl.Icon.CHERRY,HyperDogReelControl.icon(Reel.LEFT,6));
        assertTrue(HyperDogReelControl.legalFinal(
                HyperDogReelControl.Role.CHANCE,new int[]{0,7,0}));
        assertTrue(HyperDogReelControl.legalFinal(
                HyperDogReelControl.Role.STRONG_CHANCE,new int[]{0,7,14}));
        assertFalse(HyperDogReelControl.legalFinal(
                HyperDogReelControl.Role.MISS,new int[]{0,0,0}));
    }
    @Test void forbidMixedPremiumTriplesOnEveryPaylineAndMiddleCherryOutsideBar() {
        int[][] lines={{-1,-1,-1},{0,0,0},{1,1,1},{-1,0,1},{1,0,-1}};
        var roles=HyperDogReelControl.Role.values();
        var premium=java.util.EnumSet.of(
                HyperDogReelControl.Icon.RED7,HyperDogReelControl.Icon.BAR,
                HyperDogReelControl.Icon.RAINBOW7);
        boolean seenBarCherry=false,seenTopCherry=false,seenBottomCherry=false;
        for(int l=0;l<21;l++)for(int m=0;m<21;m++)for(int r=0;r<21;r++){
            int[] stops={l,m,r};
            for(var role:roles)if(HyperDogReelControl.legalFinal(role,stops)){
                var middle=HyperDogReelControl.icon(Reel.LEFT,l);
                if(middle==HyperDogReelControl.Icon.CHERRY) {
                    assertEquals(HyperDogReelControl.Role.BAR,role);
                    seenBarCherry=true;
                }
                if(role==HyperDogReelControl.Role.WEAK_CHERRY ||
                        role==HyperDogReelControl.Role.STRONG_CHERRY) {
                    if(HyperDogReelControl.visible(Reel.LEFT,l,-1)==HyperDogReelControl.Icon.CHERRY)
                        seenTopCherry=true;
                    if(HyperDogReelControl.visible(Reel.LEFT,l,1)==HyperDogReelControl.Icon.CHERRY)
                        seenBottomCherry=true;
                }
                int premiumLines=0;
                for(int[] line:lines) {
                    var a=HyperDogReelControl.visible(Reel.LEFT,l,line[0]);
                    var b=HyperDogReelControl.visible(Reel.CENTER,m,line[1]);
                    var c=HyperDogReelControl.visible(Reel.RIGHT,r,line[2]);
                    if(premium.contains(a)&&premium.contains(b)&&premium.contains(c)){
                        premiumLines++;
                        switch(role){
                            case BAR -> assertEquals(
                                    java.util.List.of(HyperDogReelControl.Icon.BAR,
                                            HyperDogReelControl.Icon.BAR,HyperDogReelControl.Icon.BAR),
                                    java.util.List.of(a,b,c));
                            case AT_ENTRY_RED7 -> {
                                assertArrayEquals(new int[]{0,0,0},line);
                                assertEquals(a,HyperDogReelControl.Icon.RED7);
                                assertEquals(b,HyperDogReelControl.Icon.RED7);
                                assertEquals(c,HyperDogReelControl.Icon.RED7);
                            }
                            case CZ_WIN, AT_ENTRY_RAINBOW7 -> {
                                assertArrayEquals(new int[]{0,0,0},line);
                                assertEquals(a,HyperDogReelControl.Icon.RAINBOW7);
                                assertEquals(b,HyperDogReelControl.Icon.RAINBOW7);
                                assertEquals(c,HyperDogReelControl.Icon.RAINBOW7);
                            }
                            case CHANCE, STRONG_CHANCE -> {
                                assertArrayEquals(new int[]{0,0,0},line);
                                assertEquals(HyperDogReelControl.Icon.RED7,a);
                                assertEquals(HyperDogReelControl.Icon.BAR,b);
                                assertEquals(role==HyperDogReelControl.Role.CHANCE
                                        ?HyperDogReelControl.Icon.RED7
                                        :HyperDogReelControl.Icon.RAINBOW7,c);
                            }
                            default -> fail("fake mixed premium line "+role+
                                    " "+java.util.Arrays.toString(stops));
                        }
                    }
                }
                if(java.util.EnumSet.of(HyperDogReelControl.Role.BAR,
                        HyperDogReelControl.Role.AT_ENTRY_RED7,
                        HyperDogReelControl.Role.CZ_WIN,
                        HyperDogReelControl.Role.AT_ENTRY_RAINBOW7,
                        HyperDogReelControl.Role.CHANCE,
                        HyperDogReelControl.Role.STRONG_CHANCE).contains(role))
                    assertEquals(1,premiumLines,role+" single paid/confirmation premium line");
                else assertEquals(0,premiumLines,"no mixed premiums for "+role);
            }
        }
        assertTrue(seenBarCherry,"at least one valid BAR line has left-middle cherry");
        assertTrue(seenTopCherry,"weak/strong cherry can hit on upper row");
        assertTrue(seenBottomCherry,"weak/strong cherry can hit on lower row");
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

    @Test void ordinaryLeftReelNeverShowsAnUnearnedCherryOrWatermelon() {
        var noTriggerRoles=java.util.EnumSet.of(
                HyperDogReelControl.Role.MISS,
                HyperDogReelControl.Role.REPLAY,
                HyperDogReelControl.Role.GRAPE);
        for(var role:new HyperDogReelControl.Role[]{
                HyperDogReelControl.Role.MISS,
                HyperDogReelControl.Role.REPLAY,
                HyperDogReelControl.Role.GRAPE,
                HyperDogReelControl.Role.WATERMELON,
                HyperDogReelControl.Role.WEAK_CHERRY,
                HyperDogReelControl.Role.STRONG_CHERRY}) {
            for(int l=0;l<21;l++)for(int m=0;m<21;m++)for(int r=0;r<21;r++) {
                int[] stops={l,m,r};
                if(!HyperDogReelControl.legalFinal(role,stops))continue;
                for(int offset=-1;offset<=1;offset++) {
                    var icon=HyperDogReelControl.visible(Reel.LEFT,l,offset);
                    if(noTriggerRoles.contains(role)) {
                        assertNotEquals(HyperDogReelControl.Icon.CHERRY,icon,
                                role+" left window "+java.util.Arrays.toString(stops));
                        assertNotEquals(HyperDogReelControl.Icon.WATERMELON,icon,
                                role+" left window "+java.util.Arrays.toString(stops));
                    } else if(role==HyperDogReelControl.Role.WATERMELON) {
                        assertNotEquals(HyperDogReelControl.Icon.CHERRY,icon,
                                role+" left window "+java.util.Arrays.toString(stops));
                    } else {
                        assertNotEquals(HyperDogReelControl.Icon.WATERMELON,icon,
                                role+" left window "+java.util.Arrays.toString(stops));
                    }
                }
            }
        }
        assertEquals(6,HyperDogReelControl.maxSlip(HyperDogReelControl.Role.MISS,Reel.LEFT));
        assertEquals(6,HyperDogReelControl.maxSlip(HyperDogReelControl.Role.MISS,Reel.CENTER));
        assertEquals(6,HyperDogReelControl.maxSlip(HyperDogReelControl.Role.MISS,Reel.RIGHT));
    }
}
