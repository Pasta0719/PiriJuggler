package jp.pirijuggler.paper.reel;
import com.google.gson.*;
import jp.pirijuggler.common.reel.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StopSolverTest {
    private static final StopCatalogue CATALOGUE=new StopCatalogue();private static final StopSolver SOLVER=new StopSolver(CATALOGUE);
    @Test void everyTripletAndExactStrictCountsMatchIndependentEnumeration() throws Exception {
        assertEquals(9261,CATALOGUE.evaluations().size());assertEquals(9261,CATALOGUE.evaluations().stream().map(e->e.stops().id()).distinct().count());
        var expected=Map.of(DisplayRole.GRAPE,750,DisplayRole.BELL,50,DisplayRole.PIERO,20,DisplayRole.REPLAY,525,DisplayRole.CHERRY,1514,DisplayRole.MISS,5250,DisplayRole.BIG_ENTRY,10,DisplayRole.REG_ENTRY,10,DisplayRole.PREMIUM_B,782);
        assertEquals(expected,CATALOGUE.counts());
        var lock=JsonParser.parseString(Files.readString(Path.of(System.getProperty("piri.specRoot"),"docs/spec-lock.json"))).getAsJsonObject();
        for(var role:DisplayRole.values()){assertEquals(lock.getAsJsonObject("strictCandidateMinimums").get(role.name()).getAsInt(),role.minimum());for(var candidate:CATALOGUE.candidates(role))assertEquals(Set.of(role),oracle(candidate.stops()));}
        for(var e:CATALOGUE.evaluations()){var roles=oracle(e.stops());for(var role:DisplayRole.values())assertEquals(roles.contains(role),e.valid(role),e.stops()+" "+role);}
    }
    // Independent oracle uses literal row triples and matching symbols, not catalogue masks.
    private static Set<DisplayRole> oracle(StopTriplet s){
        int[][] rows={{0,0,0},{-1,-1,-1},{1,1,1},{-1,0,1},{1,0,-1}};var wins=new EnumMap<DisplayRole,Integer>(DisplayRole.class);
        for(int[] row:rows){String shape=FixedReels.row(Reel.LEFT,s.left(),row[0])+"/"+FixedReels.row(Reel.CENTER,s.center(),row[1])+"/"+FixedReels.row(Reel.RIGHT,s.right(),row[2]);DisplayRole r=switch(shape){case "GRAPE/GRAPE/GRAPE"->DisplayRole.GRAPE;case "BELL/BELL/BELL"->DisplayRole.BELL;case "PIERO/PIERO/PIERO"->DisplayRole.PIERO;case "REPLAY/REPLAY/REPLAY"->DisplayRole.REPLAY;case "SEVEN/SEVEN/SEVEN"->DisplayRole.BIG_ENTRY;case "SEVEN/SEVEN/BAR"->DisplayRole.REG_ENTRY;default->null;};if(r!=null)wins.merge(r,1,Integer::sum);}
        boolean top=FixedReels.row(Reel.LEFT,s.left(),-1)==Symbol.CHERRY,middle=FixedReels.row(Reel.LEFT,s.left(),0)==Symbol.CHERRY,bottom=FixedReels.row(Reel.LEFT,s.left(),1)==Symbol.CHERRY;var result=EnumSet.noneOf(DisplayRole.class);
        if(!top&&!middle&&!bottom){if(wins.isEmpty())result.add(DisplayRole.MISS);else if(wins.size()==1&&wins.values().iterator().next()==1)result.add(wins.keySet().iterator().next());}
        if(wins.isEmpty()){if(!middle&&(top^bottom))result.add(DisplayRole.CHERRY);if(middle&&!top&&!bottom)result.add(DisplayRole.PREMIUM_B);}return result;
    }
    @Test void allOrdersAndAllPressedSequencesCompleteIncludingPremiumF(){
        var report=ReelVerification.verify(SOLVER);assertEquals(500094,report.allSequences());assertEquals(166698,report.premiumFSequences());assertEquals(7938,report.premiumFSecondChecks());
    }
    @Test void tieBreakMatchesIndependentLexicographicOrderingForEveryPartialCandidateState(){
        // Every reachable fixed state, reel and press is checked; duplicate partial states collapse.
        for(var role:DisplayRole.values()){
            var seen=new HashSet<Integer>();
            for(var e:CATALOGUE.candidates(role))for(int mask=0;mask<7;mask++)if(seen.add(e.stops().fixedKey(mask))){
                final int fixedMask=mask;var candidates=CATALOGUE.candidates(role,mask,e.stops());
                for(var reel:Reel.values())if((mask&reel.bit())==0)for(int p=0;p<21;p++){
                    final int press=p;var best=candidates.stream().min(Comparator.comparingInt((StopCatalogue.Evaluation c)->Math.floorMod(press-c.stops().stop(reel),21)).thenComparingInt(c->c.targetRank(role)).thenComparingInt(c->c.stops().left()).thenComparingInt(c->c.stops().center()).thenComparingInt(c->c.stops().right())).orElseThrow();
                    var choice=SOLVER.choose(role,fixedMask,e.stops(),reel,p,false);assertEquals(best.stops(),choice.candidate());assertSame(choice,SOLVER.choose(role,fixedMask,e.stops(),reel,p,false));
                }
            }
        }
    }
    @Test void overlapsMapToBaseVisualAndPremiumBIsRestricted(){
        for(var role:InternalRole.values())assertFalse(CATALOGUE.candidates(role.display(false)).isEmpty());
        assertEquals(DisplayRole.MISS,InternalRole.BIG.display(false));assertEquals(DisplayRole.MISS,InternalRole.REG.display(false));assertEquals(DisplayRole.CHERRY,InternalRole.CHERRY_BIG.display(false));assertEquals(DisplayRole.PIERO,InternalRole.PIERO_REG.display(false));
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
