package jp.pirijuggler.paper.reel;
import com.google.gson.*;
import jp.pirijuggler.common.reel.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StopSolverTest {
    private static final StopCatalogue CATALOGUE=new StopCatalogue();private static final StopSolver SOLVER=new StopSolver(CATALOGUE);
    private static final Set<DisplayRole> DIVERSIFIED=EnumSet.of(DisplayRole.GRAPE,DisplayRole.BELL,DisplayRole.PIERO,DisplayRole.PIERO_BONUS,DisplayRole.REPLAY,DisplayRole.BIG_ENTRY,DisplayRole.REG_ENTRY);
    @Test void everyTripletAndExactStrictCountsMatchIndependentEnumeration() throws Exception {
        assertEquals(9261,CATALOGUE.evaluations().size());assertEquals(9261,CATALOGUE.evaluations().stream().map(e->e.stops().id()).distinct().count());
        var expected=Map.ofEntries(
                Map.entry(DisplayRole.GRAPE,750),Map.entry(DisplayRole.BELL,50),Map.entry(DisplayRole.PIERO,20),Map.entry(DisplayRole.PIERO_BONUS,20),Map.entry(DisplayRole.REPLAY,525),
                Map.entry(DisplayRole.CHERRY,1287),Map.entry(DisplayRole.MISS,5126),Map.entry(DisplayRole.BONUS,124),Map.entry(DisplayRole.BONUS_CHERRY,12),
                Map.entry(DisplayRole.BIG_ENTRY,10),Map.entry(DisplayRole.REG_ENTRY,10),Map.entry(DisplayRole.PREMIUM_B,758));
        assertEquals(expected,CATALOGUE.counts());
        var lock=JsonParser.parseString(Files.readString(Path.of(System.getProperty("piri.specRoot"),"docs/spec-lock.json"))).getAsJsonObject();
        for(var role:DisplayRole.values()){assertTrue(CATALOGUE.candidates(role).size()>=role.minimum());for(var candidate:CATALOGUE.candidates(role))assertTrue(oracle(candidate.stops()).contains(role),candidate.stops()+" "+role);}
        for(var e:CATALOGUE.evaluations()){var roles=oracle(e.stops());for(var role:DisplayRole.values())assertEquals(roles.contains(role),e.valid(role),e.stops()+" "+role);}
    }
    // Independent oracle uses literal row triples and named reach patterns, not catalogue masks.
    private static Set<DisplayRole> oracle(StopTriplet s){
        int[][] rows={{0,0,0},{-1,-1,-1},{1,1,1},{-1,0,1},{1,0,-1}};var wins=new EnumMap<DisplayRole,Integer>(DisplayRole.class);int reach=0;boolean bonusPair=false;
        for(int[] row:rows){
            Symbol left=FixedReels.row(Reel.LEFT,s.left(),row[0]),center=FixedReels.row(Reel.CENTER,s.center(),row[1]),right=FixedReels.row(Reel.RIGHT,s.right(),row[2]);
            String shape=left+"/"+center+"/"+right;DisplayRole r=switch(shape){case "GRAPE/GRAPE/GRAPE"->DisplayRole.GRAPE;case "BELL/BELL/BELL"->DisplayRole.BELL;case "PIERO/PIERO/PIERO"->DisplayRole.PIERO;case "REPLAY/REPLAY/REPLAY"->DisplayRole.REPLAY;case "SEVEN/SEVEN/SEVEN"->DisplayRole.BIG_ENTRY;case "SEVEN/SEVEN/BAR"->DisplayRole.REG_ENTRY;default->null;};if(r!=null)wins.merge(r,1,Integer::sum);
            if(StopCatalogue.isReachPattern(left,center,right))reach++;
            int bonusSymbols=(left==Symbol.SEVEN||left==Symbol.BAR?1:0)+(center==Symbol.SEVEN||center==Symbol.BAR?1:0)+(right==Symbol.SEVEN||right==Symbol.BAR?1:0);if(bonusSymbols>=2)bonusPair=true;
        }
        boolean top=FixedReels.row(Reel.LEFT,s.left(),-1)==Symbol.CHERRY,middle=FixedReels.row(Reel.LEFT,s.left(),0)==Symbol.CHERRY,bottom=FixedReels.row(Reel.LEFT,s.left(),1)==Symbol.CHERRY;var result=EnumSet.noneOf(DisplayRole.class);
        boolean noCherry=!top&&!middle&&!bottom;
        if(noCherry&&wins.isEmpty()&&reach==0)result.add(DisplayRole.MISS);
        if(noCherry&&wins.isEmpty()&&reach==1)result.add(DisplayRole.BONUS);
        if(wins.isEmpty()&&reach==1&&!middle&&(top^bottom))result.add(DisplayRole.BONUS_CHERRY);
        if(noCherry&&reach==0&&wins.size()==1&&wins.values().iterator().next()==1){DisplayRole win=wins.keySet().iterator().next();result.add(win);if(win==DisplayRole.PIERO)result.add(DisplayRole.PIERO_BONUS);}
        if(wins.isEmpty()&&reach==0){if(!bonusPair&&!middle&&(top^bottom))result.add(DisplayRole.CHERRY);if(middle&&!top&&!bottom)result.add(DisplayRole.PREMIUM_B);}return result;
    }
    @Test void allOrdersAndAllPressedSequencesCompleteIncludingPremiumF(){
        var report=ReelVerification.verify(SOLVER);assertEquals(666792,report.allSequences());assertEquals(166698,report.premiumFSequences());assertEquals(7938,report.premiumFSecondChecks());
    }
    @Test void pureBonusAlwaysHasOneReachLineAndNoCherryWhileCherryBonusHasBoth(){
        for(var e:CATALOGUE.candidates(DisplayRole.BONUS)){
            assertEquals(1,Integer.bitCount(e.winningReachLines()));assertFalse(e.anyCherry());
        }
        for(var e:CATALOGUE.candidates(DisplayRole.BONUS_CHERRY)){
            assertEquals(1,Integer.bitCount(e.winningReachLines()));assertFalse(e.leftMiddleCherry());assertTrue(e.leftTopCherry()^e.leftBottomCherry());
        }
    }
    @Test void normalCherryNeverLeavesTwoBonusSymbolsOnAnyPayline(){
        for(var e:CATALOGUE.candidates(DisplayRole.CHERRY))assertEquals(0,StopCatalogue.bonusSymbolPairLines(e.stops()),e.stops().toString());
    }
    @Test void rightFirstGrapeSevenBarIsReservedForGrapeOrBonus(){
        assertEquals(1,java.util.stream.IntStream.range(0,21).filter(StopCatalogue::isRightGrapeSevenBarStop).count());
        assertTrue(StopCatalogue.isRightGrapeSevenBarStop(3));
        var blank=new StopTriplet(0,0,0);
        var forbidden=EnumSet.of(DisplayRole.BELL,DisplayRole.PIERO,DisplayRole.REPLAY,DisplayRole.CHERRY,DisplayRole.MISS);
        for(var role:forbidden)for(int press=0;press<21;press++)assertFalse(StopCatalogue.isRightGrapeSevenBarStop(SOLVER.choose(role,0,blank,Reel.RIGHT,press,false,true).stopIndex()),role+" press="+press);
        assertEquals(3,SOLVER.choose(DisplayRole.GRAPE,0,blank,Reel.RIGHT,3,false,false).stopIndex());
        assertEquals(3,SOLVER.choose(DisplayRole.BONUS,0,blank,Reel.RIGHT,3,false,false).stopIndex());
        assertEquals(3,SOLVER.choose(DisplayRole.BONUS_CHERRY,0,blank,Reel.RIGHT,3,false,false).stopIndex());
        assertEquals(3,SOLVER.choose(DisplayRole.PREMIUM_B,0,blank,Reel.RIGHT,3,false,false).stopIndex());
    }
    @Test void stopChoicesPreferTheNearestNaturalStopAndFallBackLongOnlyWhenRequired(){
        for(var role:DisplayRole.values()){
            var seen=new HashSet<Integer>();
            for(var e:CATALOGUE.candidates(role))for(int mask=0;mask<7;mask++)if(seen.add(e.stops().fixedKey(mask))){
                final int fixedMask=mask;var candidates=CATALOGUE.candidates(role,mask,e.stops());
                for(var reel:Reel.values())if((mask&reel.bit())==0)for(int p=0;p<21;p++){
                    final int press=p;
                    int minSlip=candidates.stream().mapToInt(c->ReelMotion.slip(c.stops().stop(reel),press)).min().orElseThrow();
                    boolean hasNatural=candidates.stream().anyMatch(c->{int slip=ReelMotion.slip(c.stops().stop(reel),press);return slip>=1&&slip<=4;});
                    var choice=SOLVER.choose(role,fixedMask,e.stops(),reel,p,false);
                    assertTrue(candidates.stream().anyMatch(c->c.stops().equals(choice.candidate())));
                    assertSame(choice,SOLVER.choose(role,fixedMask,e.stops(),reel,p,false));
                    if(!DIVERSIFIED.contains(role))assertEquals(minSlip,choice.slip(),role+" press="+p);
                    else if(minSlip==0)assertEquals(0,choice.slip(),"Exact eye-stop must remain exact");
                    else if(hasNatural)assertEquals(minSlip,choice.slip(),role+" should obey the actual press position inside 0..4 frames");
                    else assertTrue(choice.slip()>=minSlip&&choice.slip()<=Math.min(20,minSlip+2),role+" should use required long fallback");
                }
            }
        }
    }
    @Test void bigRightFirstCanBeAimedByVisibleSevenPosition(){
        var blank=new StopTriplet(0,0,0);
        assertEquals(2,SOLVER.choose(DisplayRole.BIG_ENTRY,0,blank,Reel.RIGHT,2,false).stopIndex(),"pressing on the bottom-row stop should keep SEVEN bottom");
        assertEquals(3,SOLVER.choose(DisplayRole.BIG_ENTRY,0,blank,Reel.RIGHT,3,false).stopIndex(),"pressing on the center-row stop should keep SEVEN center");
        for(int press=4;press<=8;press++){
            var choice=SOLVER.choose(DisplayRole.BIG_ENTRY,0,blank,Reel.RIGHT,press,false);
            assertEquals(4,choice.stopIndex(),"after SEVEN passes, the natural pull-in should place it on the top row at press="+press);
            assertEquals(press-4,choice.slip());
        }
    }
    @Test void ordinaryPressesCanFinishEveryPaylineForLineRoles(){
        for(var role:DIVERSIFIED){
            boolean[] seen=new boolean[5];int count=0;
            outer:for(int leftPress=0;leftPress<21;leftPress++)for(int centerPress=0;centerPress<21;centerPress++)for(int rightPress=0;rightPress<21;rightPress++){
                StopTriplet stopped=new StopTriplet(0,0,0);int mask=0;int[] presses={leftPress,centerPress,rightPress};
                for(var reel:Reel.values()){
                    var choice=SOLVER.choose(role,mask,stopped,reel,presses[reel.ordinal()],false);stopped=stopped.with(reel,choice.stopIndex());mask|=reel.bit();
                }
                int lineMask=CATALOGUE.evaluation(stopped).lineMask(role);assertEquals(1,Integer.bitCount(lineMask),role+" "+stopped);
                int line=Integer.numberOfTrailingZeros(lineMask);if(!seen[line]){seen[line]=true;count++;if(count==5)break outer;}
            }
            assertEquals(5,count,"All five paylines should appear for "+role);
        }
    }
    @Test void overlapsMapToBaseVisualAndPremiumBIsRestricted(){
        for(var role:InternalRole.values())assertFalse(CATALOGUE.candidates(role.display(false)).isEmpty());
        assertEquals(DisplayRole.BONUS,InternalRole.BIG.display(false));assertEquals(DisplayRole.BONUS,InternalRole.REG.display(false));
        assertEquals(DisplayRole.BONUS_CHERRY,InternalRole.CHERRY_BIG.display(false));assertEquals(DisplayRole.BONUS_CHERRY,InternalRole.CHERRY_REG.display(false));
        assertEquals(DisplayRole.PIERO_BONUS,InternalRole.PIERO_BIG.display(false));assertEquals(DisplayRole.PIERO_BONUS,InternalRole.PIERO_REG.display(false));assertEquals(DisplayRole.PIERO,InternalRole.PIERO.display(false));
        for(var role:InternalRole.values())if(role!=InternalRole.CHERRY_BIG)assertThrows(IllegalArgumentException.class,()->role.display(true));assertEquals(DisplayRole.PREMIUM_B,InternalRole.CHERRY_BIG.display(true));
        for(var role:List.of(InternalRole.BIG,InternalRole.CHERRY_BIG,InternalRole.PIERO_BIG))assertTrue(role.premiumFEligible());for(var role:List.of(InternalRole.REG,InternalRole.CHERRY_REG,InternalRole.PIERO_REG))assertFalse(role.premiumFEligible());
    }
    @Test void fivePaylinesAndSevenTenpaiDoNotRequireSameRow(){
        assertArrayEquals(new Payline[]{Payline.L1_CENTER,Payline.L2_TOP,Payline.L3_BOTTOM,Payline.L4_DOWN,Payline.L5_UP},Payline.values());
        assertEquals(1,Payline.L4_DOWN.row(Reel.RIGHT));assertEquals(-1,Payline.L5_UP.row(Reel.RIGHT));
        assertTrue(StopCatalogue.sevenTenpaiLines(new StopTriplet(4,3,0),3)>0);
        assertEquals(0,StopCatalogue.sevenTenpaiLines(new StopTriplet(0,0,0),3));
    }
}
