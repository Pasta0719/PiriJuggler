package jp.pirijuggler.common.reel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodStopControlTest {
    private static final String[] LOCKED_FORMS={
            "MISS","UPPER_BLUE7","MIDDLE_BLUE7","ORDERED_YELLOW7","LOWER_YELLOW7",
            "RISING_YELLOW7","MIDDLE_YELLOW7","COMMON_YELLOW7","GAIA_BELL",
            "RED7_FAKE","RED7","GOD","SP"
    };

    @Test
    void everyLockedRoleAlwaysResolvesToItsLockedVisibleForm(){
        for(String role:LOCKED_FORMS){
            assertTrue(GodStopControl.publishedRule(role).isPresent(),role);
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
    void ordinaryLockedFormsStayInsideFourFrameSlip(){
        String[] roles={
                "MISS","UPPER_BLUE7","MIDDLE_BLUE7","ORDERED_YELLOW7","LOWER_YELLOW7",
                "RISING_YELLOW7","MIDDLE_YELLOW7","COMMON_YELLOW7","GAIA_BELL","RED7_FAKE"
        };
        for(String role:roles){
            assertFalse(GodStopControl.isPremiumLongSlipRole(role));
            for(int reel=0;reel<3;reel++)for(int press=0;press<GodReelStrip.STOPS;press++){
                int target=GodStopControl.targetFor(role,reel,press);
                assertTrue(GodReelStrip.slip(press,target)<=4,
                        role+" reel="+reel+" press="+press);
            }
        }
    }

    @Test
    void premiumRolesAlwaysShowTheirFormAndMayUseLongSlip(){
        for(String role:new String[]{"GOD","RED7","SP"}){
            assertTrue(GodStopControl.isPremiumLongSlipRole(role));
            boolean sawLongSlip=false;
            for(int reel=0;reel<3;reel++)for(int press=0;press<GodReelStrip.STOPS;press++){
                int target=GodStopControl.targetFor(role,reel,press);
                int slip=GodReelStrip.slip(press,target);
                if(slip>4)sawLongSlip=true;

                var requirement=GodStopControl.publishedRule(role).orElseThrow().requirement(reel);
                int symbolIndex=Math.floorMod(target+requirement.row().offset(),GodReelStrip.STOPS);
                assertEquals(requirement.symbol(),GodReelStrip.symbol(reel,symbolIndex),
                        role+" reel="+reel+" press="+press+" slip="+slip);
            }
            if(!"GOD".equals(role))
                assertTrue(sawLongSlip,role+" should exercise the accepted >4-frame premium exception when required");
        }
    }

    @Test
    void lowerYellowThreeAndFifteenMedalFormsAreVisuallyDistinct(){
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

            assertNotEquals(lowerCenter,commonCenter,
                    "3-medal and 15-medal yellow must not collapse to the same center stop");
        }
    }

    @Test
    void missAndRed7FakeCannotMasqueradeAsPremiumOrPayingYellow(){
        for(String role:new String[]{"MISS","RED7_FAKE"}){
            int l=GodStopControl.targetFor(role,0,0);
            int c=GodStopControl.targetFor(role,1,0);
            int r=GodStopControl.targetFor(role,2,0);
            assertFalse(GodStopControl.matchesPublishedForm("GOD",l,c,r));
            assertFalse(GodStopControl.matchesPublishedForm("RED7",l,c,r));
            assertFalse(GodStopControl.matchesPublishedForm("SP",l,c,r));
            assertFalse(GodStopControl.matchesPublishedForm("LOWER_YELLOW7",l,c,r));
            assertFalse(GodStopControl.matchesPublishedForm("COMMON_YELLOW7",l,c,r));
        }
    }
    @Test
    void missNeverFormsAnyStraightOrDiagonalGodRedBlueOrYellowLine(){
        GodReelStrip.VisibleRow[][] lines={
                {GodReelStrip.VisibleRow.TOP,GodReelStrip.VisibleRow.TOP,GodReelStrip.VisibleRow.TOP},
                {GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.MIDDLE},
                {GodReelStrip.VisibleRow.BOTTOM,GodReelStrip.VisibleRow.BOTTOM,GodReelStrip.VisibleRow.BOTTOM},
                {GodReelStrip.VisibleRow.BOTTOM,GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.TOP},
                {GodReelStrip.VisibleRow.TOP,GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.BOTTOM}
        };
        for(int lp=0;lp<GodReelStrip.STOPS;lp++){
            int l=GodStopControl.targetFor("MISS",0,lp);
            for(int cp=0;cp<GodReelStrip.STOPS;cp++){
                int m=GodStopControl.targetFor("MISS",1,cp);
                for(int rp=0;rp<GodReelStrip.STOPS;rp++){
                    int r=GodStopControl.targetFor("MISS",2,rp);
                    int[] stops={l,m,r};
                    for(var line:lines){
                        var a=GodReelStrip.visibleSymbol(0,stops[0],line[0]);
                        var b=GodReelStrip.visibleSymbol(1,stops[1],line[1]);
                        var d=GodReelStrip.visibleSymbol(2,stops[2],line[2]);
                        if(a==b&&b==d){
                            assertFalse(
                                    a==GodReelStrip.Symbol.GOD||
                                    a==GodReelStrip.Symbol.RED7||
                                    a==GodReelStrip.Symbol.BLUE7||
                                    a==GodReelStrip.Symbol.YELLOW7,
                                    "MISS formed winning-looking line "+a+" for presses "+lp+","+cp+","+rp
                            );
                        }
                    }
                }
            }
        }
    }

}
