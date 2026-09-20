package jp.pirijuggler.common.reel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodStopControlTest {
    @Test
    void publishedRolesAlwaysLandOnTheirPublishedRepresentativeForm(){
        String[] roles={
                "UPPER_BLUE7","MIDDLE_BLUE7","LOWER_YELLOW7","RISING_YELLOW7",
                "MIDDLE_YELLOW7","GAIA_BELL","SP","RED7","GOD"
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
    void ordinaryPublishedSmallRolesNeedAtMostFourFrameSlip(){
        String[] roles={
                "UPPER_BLUE7","MIDDLE_BLUE7","LOWER_YELLOW7",
                "RISING_YELLOW7","MIDDLE_YELLOW7","GAIA_BELL","GOD"
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
    void unpublishedRoleDetailsRemainExplicitFallbacks(){
        assertTrue(GodStopControl.publishedRule("ORDERED_YELLOW7").isEmpty());
        assertTrue(GodStopControl.publishedRule("COMMON_YELLOW7").isEmpty());
        assertTrue(GodStopControl.publishedRule("RED7_FAKE").isEmpty());
        assertTrue(GodStopControl.publishedRule("MISS").isEmpty());

        for(String role:new String[]{"ORDERED_YELLOW7","COMMON_YELLOW7","RED7_FAKE","MISS"}){
            for(int reel=0;reel<3;reel++){
                for(int press=0;press<GodReelStrip.STOPS;press++){
                    int target=GodStopControl.targetFor(role,reel,press);
                    assertTrue(target>=0&&target<GodReelStrip.STOPS);
                }
            }
        }
    }
}
