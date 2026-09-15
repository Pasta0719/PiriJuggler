package jp.pirijuggler.paper.reel;
import com.google.gson.*;
import jp.pirijuggler.common.reel.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StopSolverTest {
    private static final StopCatalogue CATALOGUE=new StopCatalogue();private static final StopSolver SOLVER=new StopSolver(CATALOGUE);
    private static final Set<DisplayRole> DIVERSIFIED=EnumSet.of(DisplayRole.GRAPE,DisplayRole.BELL,DisplayRole.PIERO,DisplayRole.REPLAY,DisplayRole.BIG_ENTRY,DisplayRole.REG_ENTRY);
    @Test void everyTripletAndExactStrictCountsMatchIndependentEnumeration() throws Exception {
        assertEquals(9261,CATALOGUE.evaluations().size());assertEquals(9261,CATALOGUE.evaluations().stream().map(e->e.stops().id()).distinct().count());
        var expected=Map.of(DisplayRole.GRAPE,750,DisplayRole.BELL,50,DisplayRole.PIERO,20,DisplayRole.REPLAY,525,DisplayRole.CHERRY,1502,DisplayRole.MISS,5126,DisplayRole.BONUS,5250,DisplayRole.BIG_ENTRY,10,DisplayRole.REG_ENTRY,10,DisplayRole.PREMIUM_B,758);
        assertEquals(expected,CATALOGUE.counts());
        var lock=JsonParser.parseString(Files.readString(Path.of(System.getProperty("piri.specRoot"),"docs/spec-lock.json"))).getAsJsonObject();
        for(var role:DisplayRole.values()){assertTrue(CATALOGUE.candidates(role).size()>=role.minimum());for(var candidate:CATALOGUE.candidates(role))assertTrue(oracle(candidate.stops()).contains(role),candidate.stops()+" "+role);}
        for(var e:CATALOGUE.evaluations()){var roles=oracle(e.stops());for(var role:DisplayRole.values())assertEquals(roles.contains(role),e.valid(role),e.stops()+" "+role);}
    }
    // Independent oracle uses literal row triples and named reach patterns, not catalogue masks.
    private static Set<DisplayRole> oracle(StopTriplet s){
        int[][] rows={{0,0,0},{-1,-1,-1},{1,1,1},{-1,0,1},{1,0,-1}};var wins=new EnumMap<DisplayRole,Integer>(DisplayRole.class);int reach=0;
        for(int[] row:rows){
            Symbol left=FixedReels.row(Reel.LEFT,s.left(),row[0]),center=FixedReels.row(Reel.CENTER,s.center(),row[1]),right=FixedReels.row(Reel.RIGHT,s.right(),row[2]);
            String shape=left+"/"+center+"/"+right;DisplayRole r=switch(shape){case "GRAPE/GRAPE/GRAPE"->DisplayRole.GRAPE;case "BELL/BELL/BELL"->DisplayRole.BELL;case "PIERO/PIERO/PIERO"->DisplayRole.PIERO;case "REPLAY/REPLAY/REPLAY"->DisplayRole.REPLAY;case "SEVEN/SEVEN/SEVEN"->DisplayRole.BIG_ENTRY;case "SEVEN/SEVEN/BAR"->DisplayRole.REG_ENTRY;default->null;};if(r!=null)wins.merge(r,1,Integer::sum);
            if(StopCatalogue.isReachPattern(left,center,right))reach++;
        }
        boolean top=FixedReels.row(Reel.LEFT,s.left(),-1)==Symbol.CHERRY,middle=FixedReels.row(Reel.LEFT,s.left(),0)==Symbol.CHERRY,bottom=FixedReels.row(Reel.LEFT,s.left(),1)==Symbol.CHERRY;var result=EnumSet.noneOf(DisplayRole.class);
        boolean noCherry=!top&&!middle&&!bottom;
        if(noCherry&&wins.isEmpty()&&reach==0)result.add(DisplayRole.MISS);
        if(noCherry&&wins.isEmpty()&&(reach==0||reach==1))result.add(DisplayRole.BONUS);
        if(noCherry&&reach==0&&wins.size()==1&&wins.values().iterator().next()==1)result.add(wins.keySet().iterator().next());
        if(wins.isEmpty()&&reach==0){if(!middle&&(top^bottom))result.add(DisplayRole.CHERRY);if(middle&&!top&&!bottom)result.add(DisplayRole.PREMIUM_B);}return result;
    }
    @Test void allOrdersAndAllPressedSequencesCompleteIncludingPremiumF(){
        var report=ReelVerification.verify(SOLVER);assertEquals(555660,report.allSequences());assertEquals(166698,report.premiumFSequences());assertEquals(7938,report.premiumFSecondChecks());
    }
    @Test void stopChoicesPreferNaturalOneToFourFramesAndFallBackLongOnlyWhenRequired(){
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
                    else if(hasNatural)assertTrue(choice.slip()>=1&&choice.slip()<=4,role+" should stay in natural pull-in range");
                    else assertTrue(choice.slip()>=minSlip&&choice.slip()<=Math.min(20,minSlip+2),role+" should use required long fallback");
                }
            }
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
        assertEquals(DisplayRole.BONUS,InternalRole.BIG.display(false));assertEquals(DisplayRole.BONUS,InternalRole.REG.display(false));assertEquals(DisplayRole.CHERRY,InternalRole.CHERRY_BIG.display(false));assertEquals(DisplayRole.PIERO,InternalRole.PIERO_REG.display(false));
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
