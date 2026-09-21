package jp.pirijuggler.common.reel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodStopControlTest {
    @Test
    void publishedRolesAlwaysLandOnTheirPublishedRepresentativeForm(){
        String[] roles={
                "UPPER_BLUE7","MIDDLE_BLUE7","ORDERED_YELLOW7","LOWER_YELLOW7",
                "RISING_YELLOW7","MIDDLE_YELLOW7","COMMON_YELLOW7",
                "GAIA_BELL","GOD"
        };
        for(String role:roles){
            for(int leftPress=0;leftPress<GodReelStrip.STOPS;leftPress++){
                int left=GodStopControl.targetFor(role,0,leftPress);
                for(int centerPress=0;centerPress<GodReelStrip.STOPS;centerPress++){
                    int center=GodStopControl.targetFor(role,1,centerPress);
                    for(int rightPress=0;rightPress<GodReelStrip.STOPS;rightPress++){
                        int right=GodStopControl.targetFor(role,2,rightPress);
                        assertTrue(
                                GodStopControl.matchesPublishedForm(role,left,center,right),
                                role+" failed for presses "+leftPress+","+centerPress+","+rightPress
                        );
                    }
                }
            }
        }
    }

    @Test
    void ordinaryPublishedFormsNeedAtMostFourFrameSlip(){
        String[] roles={
                "UPPER_BLUE7","MIDDLE_BLUE7","ORDERED_YELLOW7","LOWER_YELLOW7",
                "RISING_YELLOW7","MIDDLE_YELLOW7","COMMON_YELLOW7","GAIA_BELL","GOD"
        };
        for(String role:roles){
            for(int reel=0;reel<3;reel++){
                for(int press=0;press<GodReelStrip.STOPS;press++){
                    int target=GodStopControl.targetFor(role,reel,press);
                    assertTrue(GodReelStrip.slip(press,target)<=4,
                            role+" reel="+reel+" press="+press);
                }
            }
        }
    }

    @Test
    void lowerYellowThreeAndFifteenMedalFormsAreVisuallyDistinct(){
        assertTrue(GodStopControl.publishedRule("ORDERED_YELLOW7").isPresent());
        assertTrue(GodStopControl.publishedRule("LOWER_YELLOW7").isPresent());
        assertTrue(GodStopControl.publishedRule("COMMON_YELLOW7").isPresent());

        for(int press=0;press<GodReelStrip.STOPS;press++){
            int lowerLeft=GodStopControl.targetFor("LOWER_YELLOW7",0,press);
            int lowerCenter=GodStopControl.targetFor("LOWER_YELLOW7",1,press);
            int lowerRight=GodStopControl.targetFor("LOWER_YELLOW7",2,press);
            assertEquals(GodReelStrip.Symbol.YELLOW7,GodReelStrip.symbol(0,lowerLeft-1));
            assertEquals(GodReelStrip.Symbol.RED7,GodReelStrip.symbol(1,lowerCenter));
            assertEquals(GodReelStrip.Symbol.YELLOW7,GodReelStrip.symbol(2,lowerRight-1));

            int commonLeft=GodStopControl.targetFor("COMMON_YELLOW7",0,press);
            int commonCenter=GodStopControl.targetFor("COMMON_YELLOW7",1,press);
            int commonRight=GodStopControl.targetFor("COMMON_YELLOW7",2,press);
            assertEquals(GodReelStrip.Symbol.YELLOW7,GodReelStrip.symbol(0,commonLeft-1));
            assertEquals(GodReelStrip.Symbol.BLUE7,GodReelStrip.symbol(1,commonCenter));
            assertEquals(GodReelStrip.Symbol.YELLOW7,GodReelStrip.symbol(2,commonRight-1));
        }
    }

    @Test
    void genuinelyUnpublishedExactFormsRemainFallbacks(){
        assertTrue(GodStopControl.publishedRule("RED7_FAKE").isEmpty());
        assertTrue(GodStopControl.publishedRule("MISS").isEmpty());

        for(String role:new String[]{"RED7_FAKE","MISS"}){
            for(int reel=0;reel<3;reel++){
                for(int press=0;press<GodReelStrip.STOPS;press++){
                    int target=GodStopControl.targetFor(role,reel,press);
                    assertTrue(target>=0&&target<GodReelStrip.STOPS);
                }
            }
        }
    }
    @Test
    void red7AndSpUseRepresentativeFormOnlyWhenReachableWithinFourFrames(){
        for(String role:new String[]{"RED7","SP"}){
            boolean sawRepresentative=false;
            boolean sawUnreachable=false;
            for(int reel=0;reel<3;reel++)for(int press=0;press<GodReelStrip.STOPS;press++){
                int target=GodStopControl.targetFor(role,reel,press);
                int slip=GodReelStrip.slip(press,target);
                assertTrue(slip<=4,role+" reel="+reel+" press="+press);
                var rule=GodStopControl.publishedRule(role).orElseThrow();
                var requirement=rule.requirement(reel);
                int symbolIndex=Math.floorMod(target+requirement.row().offset(),GodReelStrip.STOPS);
                boolean matches=GodReelStrip.symbol(reel,symbolIndex)==requirement.symbol();
                if(matches)sawRepresentative=true; else sawUnreachable=true;
            }
            assertTrue(sawRepresentative,role+" should show the representative form from reachable press positions");
            assertTrue(sawUnreachable,role+" should not be force-aligned from every press position");
        }
    }

    @Test
    void red7AndSpNeverUseAnImpossibleLongSlip(){
        for(String role:new String[]{"RED7","SP"}){
            boolean sawRepresentativeMiss=false;
            for(int reel=0;reel<3;reel++)for(int press=0;press<GodReelStrip.STOPS;press++){
                int target=GodStopControl.targetFor(role,reel,press);
                assertTrue(GodReelStrip.slip(press,target)<=4,role+" reel="+reel+" press="+press);
                if(target==press)sawRepresentativeMiss=true;
            }
            assertTrue(sawRepresentativeMiss,role+" should not be force-aligned from every press position");
        }
    }

}
