package jp.pirijuggler.common.reel;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GodStopControlTest {
    private static final String[] LOCKED_FORMS={
            "UPPER_BLUE7","MIDDLE_BLUE7","ORDERED_YELLOW7","ORDERED_YELLOW7_ONE","LOWER_YELLOW7",
            "RISING_YELLOW7","MIDDLE_YELLOW7","COMMON_YELLOW7","GAIA_BELL",
            "RED7","GOD","SP"
    };

    @Test
    void everyFixedRoleAlwaysResolvesToItsLockedVisibleForm(){
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
    void ordinaryFixedFormsStayInsideFourFrameSlip(){
        String[] roles={
                "UPPER_BLUE7","MIDDLE_BLUE7","ORDERED_YELLOW7","ORDERED_YELLOW7_ONE","LOWER_YELLOW7",
                "RISING_YELLOW7","MIDDLE_YELLOW7","COMMON_YELLOW7","GAIA_BELL"
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
    void missCatalogueIsExactlyAllSafePhysicalStopCombinations(){
        int safe=0;
        for(int l=0;l<GodReelStrip.STOPS;l++)
            for(int c=0;c<GodReelStrip.STOPS;c++)
                for(int r=0;r<GodReelStrip.STOPS;r++)
                    if(GodStopControl.isSafeMiss(l,c,r))safe++;

        assertEquals(safe,GodStopControl.missCandidates().size());
        assertTrue(safe>4,"MISS must no longer be a four-pattern fixed presentation");
        assertTrue(GodStopControl.missCandidates().stream()
                .allMatch(s->GodStopControl.isSafeMiss(s.left(),s.center(),s.right())));
    }

    @Test
    void contextualMissControlNeverNeedsMoreThanFourFramesAndAlwaysFinishesSafe(){
        Set<GodStopControl.MissStop> observed=new LinkedHashSet<>();
        int[][] secondOrders={{1,2},{2,1}};

        for(int leftPress=0;leftPress<GodReelStrip.STOPS;leftPress++){
            int left=GodStopControl.targetFor("MISS",0,leftPress,0,0,0,0);
            assertTrue(GodReelStrip.slip(leftPress,left)<=4);

            for(int[] order:secondOrders){
                int second=order[0],third=order[1];
                for(int secondPress=0;secondPress<GodReelStrip.STOPS;secondPress++){
                    int[] stops={left,0,0};
                    int secondTarget=GodStopControl.targetFor(
                            "MISS",second,secondPress,1,stops[0],stops[1],stops[2]);
                    assertTrue(GodReelStrip.slip(secondPress,secondTarget)<=4);
                    stops[second]=secondTarget;
                    int secondMask=1|(1<<second);

                    for(int thirdPress=0;thirdPress<GodReelStrip.STOPS;thirdPress++){
                        int thirdTarget=GodStopControl.targetFor(
                                "MISS",third,thirdPress,secondMask,stops[0],stops[1],stops[2]);
                        assertTrue(GodReelStrip.slip(thirdPress,thirdTarget)<=4);
                        int[] finalStops=stops.clone();
                        finalStops[third]=thirdTarget;
                        assertTrue(GodStopControl.isSafeMiss(finalStops[0],finalStops[1],finalStops[2]),
                                "unsafe MISS for presses "+leftPress+","+secondPress+","+thirdPress);
                        observed.add(new GodStopControl.MissStop(finalStops[0],finalStops[1],finalStops[2]));
                    }
                }
            }
        }
        assertTrue(observed.size()>4,
                "contextual MISS control must expose multiple safe final windows");
    }


    @Test
    void missSelectorVariesIdenticalPressTimingAcrossSpins(){
        Set<GodStopControl.MissStop> observed=new LinkedHashSet<>();
        int lp=7,cp=11,rp=3;
        for(long seed=1;seed<=128;seed++){
            int l=GodStopControl.targetFor("MISS",0,lp,0,0,0,0,seed);
            int c=GodStopControl.targetFor("MISS",1,cp,1,l,0,0,seed);
            int r=GodStopControl.targetFor("MISS",2,rp,3,l,c,0,seed);
            assertTrue(GodStopControl.isSafeMiss(l,c,r));
            observed.add(new GodStopControl.MissStop(l,c,r));
        }
        assertTrue(observed.size()>1,"identical press timing must not collapse MISS to one visible result");
    }

    @Test
    void fakeRedUsesVariableSafeReplayFormsWithinFourFrames(){
        Set<GodStopControl.MissStop> observed=new LinkedHashSet<>();
        boolean sawRepresentative=false;
        int[][] secondOrders={{1,2},{2,1}};

        for(long seed=1;seed<=16;seed++){
            for(int leftPress=0;leftPress<GodReelStrip.STOPS;leftPress++){
                int left=GodStopControl.targetFor("RED7_FAKE",0,leftPress,0,0,0,0,seed);
                assertTrue(GodReelStrip.slip(leftPress,left)<=4);

                for(int[] order:secondOrders){
                    int second=order[0],third=order[1];
                    for(int secondPress=0;secondPress<GodReelStrip.STOPS;secondPress++){
                        int[] stops={left,0,0};
                        int secondTarget=GodStopControl.targetFor(
                                "RED7_FAKE",second,secondPress,1,stops[0],stops[1],stops[2],seed);
                        assertTrue(GodReelStrip.slip(secondPress,secondTarget)<=4);
                        stops[second]=secondTarget;
                        int secondMask=1|(1<<second);

                        for(int thirdPress=0;thirdPress<GodReelStrip.STOPS;thirdPress++){
                            int thirdTarget=GodStopControl.targetFor(
                                    "RED7_FAKE",third,thirdPress,secondMask,stops[0],stops[1],stops[2],seed);
                            assertTrue(GodReelStrip.slip(thirdPress,thirdTarget)<=4);
                            int[] fin=stops.clone();fin[third]=thirdTarget;
                            assertTrue(GodStopControl.isSafeFakeRed(fin[0],fin[1],fin[2]));
                            sawRepresentative|=GodStopControl.matchesFakeRedRepresentative(fin[0],fin[1],fin[2]);
                            observed.add(new GodStopControl.MissStop(fin[0],fin[1],fin[2]));
                        }
                    }
                }
            }
        }
        assertTrue(sawRepresentative,"source representative RED7/RED7/miss form should occur when reachable");
        assertTrue(observed.size()>1,"fake RED must not collapse to one fixed visible result");
    }

    @Test
    void everyDeclaredVisibleStopPatternIsActuallyReachable(){
        var expectedFixed=new LinkedHashMap<String,Integer>();
        expectedFixed.put("UPPER_BLUE7",2);
        expectedFixed.put("MIDDLE_BLUE7",4);
        expectedFixed.put("ORDERED_YELLOW7",2);
        expectedFixed.put("ORDERED_YELLOW7_ONE",64);
        expectedFixed.put("LOWER_YELLOW7",2);
        expectedFixed.put("RISING_YELLOW7",2);
        expectedFixed.put("MIDDLE_YELLOW7",8);
        expectedFixed.put("COMMON_YELLOW7",2);
        expectedFixed.put("GAIA_BELL",4);
        expectedFixed.put("RED7",1);
        expectedFixed.put("GOD",1);
        expectedFixed.put("SP",1);

        for(var entry:expectedFixed.entrySet()){
            String role=entry.getKey();
            Set<String> seen=new LinkedHashSet<>();
            for(int lp=0;lp<GodReelStrip.STOPS;lp++){
                int l=GodStopControl.targetFor(role,0,lp);
                for(int cp=0;cp<GodReelStrip.STOPS;cp++){
                    int m=GodStopControl.targetFor(role,1,cp);
                    for(int rp=0;rp<GodReelStrip.STOPS;rp++){
                        int r=GodStopControl.targetFor(role,2,rp);
                        assertTrue(GodStopControl.matchesPublishedForm(role,l,m,r),role);
                        seen.add(visibleSignature(l,m,r));
                    }
                }
            }
            assertEquals(entry.getValue(),seen.size(),role+" reachable visible stop-pattern count");
        }

        Set<String> allSafeMissVisible=new LinkedHashSet<>();
        for(var s:GodStopControl.missCandidates())
            allSafeMissVisible.add(visibleSignature(s.left(),s.center(),s.right()));
        assertEquals(339,allSafeMissVisible.size(),"declared MISS visible-pattern universe");

        Set<String> reachedMiss=new LinkedHashSet<>();
        Set<String> reachedFake=new LinkedHashSet<>();
        for(long seed=1;seed<=4;seed++){
            collectVariableRoleVisiblePatterns("MISS",seed,reachedMiss);
            collectVariableRoleVisiblePatterns("RED7_FAKE",seed,reachedFake);
        }

        assertEquals(allSafeMissVisible,reachedMiss,
                "every one of the 339 safe MISS visible patterns must actually be stoppable");
        assertEquals(99,reachedFake.size(),
                "every declared RED7_FAKE visible pattern must actually be stoppable");
    }

    private static void collectVariableRoleVisiblePatterns(String role,long seed,Set<String> out){
        for(int lp=0;lp<GodReelStrip.STOPS;lp++){
            int l=GodStopControl.targetFor(role,0,lp,0,0,0,0,seed);

            for(int cp=0;cp<GodReelStrip.STOPS;cp++){
                int m=GodStopControl.targetFor(role,1,cp,1,l,0,0,seed);
                for(int rp=0;rp<GodReelStrip.STOPS;rp++){
                    int r=GodStopControl.targetFor(role,2,rp,3,l,m,0,seed);
                    if("MISS".equals(role))assertTrue(GodStopControl.isSafeMiss(l,m,r));
                    else assertTrue(GodStopControl.isSafeFakeRed(l,m,r));
                    out.add(visibleSignature(l,m,r));
                }
            }

            for(int rp=0;rp<GodReelStrip.STOPS;rp++){
                int r=GodStopControl.targetFor(role,2,rp,1,l,0,0,seed);
                for(int cp=0;cp<GodReelStrip.STOPS;cp++){
                    int m=GodStopControl.targetFor(role,1,cp,5,l,0,r,seed);
                    if("MISS".equals(role))assertTrue(GodStopControl.isSafeMiss(l,m,r));
                    else assertTrue(GodStopControl.isSafeFakeRed(l,m,r));
                    out.add(visibleSignature(l,m,r));
                }
            }
        }
    }

    private static String visibleSignature(int left,int center,int right){
        StringBuilder b=new StringBuilder();
        int[] stops={left,center,right};
        GodReelStrip.VisibleRow[] rows={
                GodReelStrip.VisibleRow.TOP,
                GodReelStrip.VisibleRow.MIDDLE,
                GodReelStrip.VisibleRow.BOTTOM
        };
        for(var row:rows)
            for(int reel=0;reel<3;reel++)
                b.append(GodReelStrip.visibleSymbol(reel,stops[reel],row).name()).append('|');
        return b.toString();
    }

    @Test
    void everyFixedRoleHasOnlyItsIntendedWinningLookingStraightOrDiagonalLines(){
        var expected=new LinkedHashMap<String,Set<String>>();
        expected.put("UPPER_BLUE7",Set.of("TOP:BLUE7"));
        expected.put("MIDDLE_BLUE7",Set.of("MIDDLE:BLUE7"));
        expected.put("ORDERED_YELLOW7",Set.of("BOTTOM:YELLOW7"));
        expected.put("ORDERED_YELLOW7_ONE",Set.of());
        expected.put("LOWER_YELLOW7",Set.of("BOTTOM:YELLOW7"));
        expected.put("RISING_YELLOW7",Set.of("RISING:YELLOW7"));
        expected.put("MIDDLE_YELLOW7",Set.of("MIDDLE:YELLOW7"));
        expected.put("COMMON_YELLOW7",Set.of("BOTTOM:YELLOW7"));
        expected.put("GAIA_BELL",Set.of());
        expected.put("RED7",Set.of("MIDDLE:RED7"));
        expected.put("GOD",Set.of("MIDDLE:GOD"));
        expected.put("SP",Set.of());

        GodReelStrip.VisibleRow[][] rows={
                {GodReelStrip.VisibleRow.TOP,GodReelStrip.VisibleRow.TOP,GodReelStrip.VisibleRow.TOP},
                {GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.MIDDLE},
                {GodReelStrip.VisibleRow.BOTTOM,GodReelStrip.VisibleRow.BOTTOM,GodReelStrip.VisibleRow.BOTTOM},
                {GodReelStrip.VisibleRow.BOTTOM,GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.TOP},
                {GodReelStrip.VisibleRow.TOP,GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.BOTTOM}
        };
        String[] names={"TOP","MIDDLE","BOTTOM","RISING","FALLING"};

        for(var entry:expected.entrySet()){
            String role=entry.getKey();
            for(int lp=0;lp<GodReelStrip.STOPS;lp++){
                int l=GodStopControl.targetFor(role,0,lp);
                for(int cp=0;cp<GodReelStrip.STOPS;cp++){
                    int c=GodStopControl.targetFor(role,1,cp);
                    for(int rp=0;rp<GodReelStrip.STOPS;rp++){
                        int r=GodStopControl.targetFor(role,2,rp);
                        int[] stops={l,c,r};
                        var actual=new LinkedHashSet<String>();
                        for(int i=0;i<rows.length;i++){
                            var a=GodReelStrip.visibleSymbol(0,stops[0],rows[i][0]);
                            var b=GodReelStrip.visibleSymbol(1,stops[1],rows[i][1]);
                            var d=GodReelStrip.visibleSymbol(2,stops[2],rows[i][2]);
                            if(a==b&&b==d&&(a==GodReelStrip.Symbol.GOD||a==GodReelStrip.Symbol.RED7||
                                    a==GodReelStrip.Symbol.BLUE7||a==GodReelStrip.Symbol.YELLOW7))
                                actual.add(names[i]+":"+a.name());
                        }
                        assertEquals(entry.getValue(),actual,
                                role+" unexpected visible line(s) for presses "+lp+","+cp+","+rp);
                    }
                }
            }
        }
    }
}
