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
                Map.entry(DisplayRole.CHERRY,1287),Map.entry(DisplayRole.MISS,5126),Map.entry(DisplayRole.BONUS,447),Map.entry(DisplayRole.BONUS_CHERRY,24),
                Map.entry(DisplayRole.BIG_ENTRY,10),Map.entry(DisplayRole.REG_ENTRY,10),Map.entry(DisplayRole.PREMIUM_B,782),Map.entry(DisplayRole.GOD_BAR,1));
        assertEquals(expected,CATALOGUE.counts());
        var lock=JsonParser.parseString(Files.readString(Path.of(System.getProperty("piri.specRoot"),"docs/spec-lock.json"))).getAsJsonObject();
        for(var role:DisplayRole.values()){assertTrue(CATALOGUE.candidates(role).size()>=role.minimum());for(var candidate:CATALOGUE.candidates(role))assertTrue(oracle(candidate.stops()).contains(role),candidate.stops()+" "+role);}
        for(var e:CATALOGUE.evaluations()){var roles=oracle(e.stops());for(var role:DisplayRole.values())assertEquals(roles.contains(role),e.valid(role),e.stops()+" "+role);}
    }
    // Independent oracle uses literal row triples and named reach patterns, not catalogue candidate maps.
    private static Set<DisplayRole> oracle(StopTriplet s){
        int[][] rows={{0,0,0},{-1,-1,-1},{1,1,1},{-1,0,1},{1,0,-1}};var wins=new EnumMap<DisplayRole,Integer>(DisplayRole.class);int reach=0;boolean bonusPair=false;
        for(int[] row:rows){
            Symbol left=FixedReels.row(Reel.LEFT,s.left(),row[0]),center=FixedReels.row(Reel.CENTER,s.center(),row[1]),right=FixedReels.row(Reel.RIGHT,s.right(),row[2]);
            String shape=left+"/"+center+"/"+right;DisplayRole r=switch(shape){case "GRAPE/GRAPE/GRAPE"->DisplayRole.GRAPE;case "BELL/BELL/BELL"->DisplayRole.BELL;case "PIERO/PIERO/PIERO"->DisplayRole.PIERO;case "REPLAY/REPLAY/REPLAY"->DisplayRole.REPLAY;case "SEVEN/SEVEN/SEVEN"->DisplayRole.BIG_ENTRY;case "SEVEN/SEVEN/BAR"->DisplayRole.REG_ENTRY;default->null;};if(r!=null)wins.merge(r,1,Integer::sum);
            if(StopCatalogue.isReachPattern(left,center,right))reach++;
            int bonusSymbols=(left==Symbol.SEVEN||left==Symbol.BAR?1:0)+(center==Symbol.SEVEN||center==Symbol.BAR?1:0)+(right==Symbol.SEVEN||right==Symbol.BAR?1:0);if(bonusSymbols>=2)bonusPair=true;
        }
        int bonusControl=Integer.bitCount(StopCatalogue.bonusControlLines(s));
        int centerBar=FixedReels.row(Reel.LEFT,s.left(),0)==Symbol.BAR&&FixedReels.row(Reel.CENTER,s.center(),0)==Symbol.BAR&&FixedReels.row(Reel.RIGHT,s.right(),0)==Symbol.BAR?1:0;
        boolean top=FixedReels.row(Reel.LEFT,s.left(),-1)==Symbol.CHERRY,middle=FixedReels.row(Reel.LEFT,s.left(),0)==Symbol.CHERRY,bottom=FixedReels.row(Reel.LEFT,s.left(),1)==Symbol.CHERRY;var result=EnumSet.noneOf(DisplayRole.class);
        boolean noCherry=!top&&!middle&&!bottom;
        if(noCherry&&wins.isEmpty()&&reach==0)result.add(DisplayRole.MISS);
        if(noCherry&&wins.isEmpty()&&bonusControl==1)result.add(DisplayRole.BONUS);
        if(wins.isEmpty()&&bonusControl==1&&!middle&&(top^bottom))result.add(DisplayRole.BONUS_CHERRY);
        if(noCherry&&reach==0&&wins.size()==1&&wins.values().iterator().next()==1){DisplayRole win=wins.keySet().iterator().next();result.add(win);if(win==DisplayRole.PIERO)result.add(DisplayRole.PIERO_BONUS);}
        if(wins.isEmpty()&&reach==0&&!bonusPair&&!middle&&(top^bottom))result.add(DisplayRole.CHERRY);
        if(wins.isEmpty()&&reach<=1&&middle&&!top&&!bottom)result.add(DisplayRole.PREMIUM_B);
        if(centerBar==1&&wins.isEmpty()&&noCherry)result.add(DisplayRole.GOD_BAR);
        return result;
    }
    @Test void allOrdersAndAllPressedSequencesCompleteIncludingPremiumF(){
        var report=ReelVerification.verify(SOLVER);assertEquals(722358,report.allSequences());assertEquals(166698,report.premiumFSequences());assertEquals(7938,report.premiumFSecondChecks());
    }
    @Test void pureBonusAlwaysHasOneControlLineAndNoCherryWhileCherryBonusHasBoth(){
        for(var e:CATALOGUE.candidates(DisplayRole.BONUS)){
            assertEquals(1,Integer.bitCount(e.lineMask(DisplayRole.BONUS)));assertFalse(e.anyCherry());
        }
        for(var e:CATALOGUE.candidates(DisplayRole.BONUS_CHERRY)){
            assertEquals(1,Integer.bitCount(e.lineMask(DisplayRole.BONUS_CHERRY)));assertFalse(e.leftMiddleCherry());assertTrue(e.leftTopCherry()^e.leftBottomCherry());
        }
    }
    @Test void premiumBMayAlsoCarryOneBonusReachLine(){
        assertTrue(CATALOGUE.candidates(DisplayRole.PREMIUM_B).stream().anyMatch(e->e.winningReachLines()!=0));
        assertTrue(CATALOGUE.candidates(DisplayRole.PREMIUM_B).stream().anyMatch(e->e.winningBarConfirmationLines()!=0));
        for(var e:CATALOGUE.candidates(DisplayRole.PREMIUM_B)){
            assertTrue(Integer.bitCount(e.winningReachLines())<=1);
            assertTrue(e.leftMiddleCherry());assertFalse(e.leftTopCherry());assertFalse(e.leftBottomCherry());
        }
    }
    @Test void nonPremiumBonusControlNeverFinishesOnBarBarBarButPremiumMay(){
        var blank=new StopTriplet(0,0,0);boolean premiumBarSeen=false;
        for(var order:ReelVerification.orders())for(int p1=0;p1<21;p1++)for(int p2=0;p2<21;p2++)for(int p3=0;p3<21;p3++){
            int[] presses={p1,p2,p3};StopTriplet ordinary=blank;int mask=0;
            for(int i=0;i<3;i++){Reel reel=order.get(i);var c=SOLVER.choose(DisplayRole.BONUS,mask,ordinary,reel,presses[i],false);ordinary=ordinary.with(reel,c.stopIndex());mask|=reel.bit();}
            assertEquals(0,CATALOGUE.evaluation(ordinary).winningBarConfirmationLines(),"ordinary "+order+" "+p1+","+p2+","+p3);
            if(!premiumBarSeen){StopTriplet premium=blank;mask=0;for(int i=0;i<3;i++){Reel reel=order.get(i);var c=SOLVER.choose(DisplayRole.BONUS,null,mask,premium,reel,presses[i],false,true,false);premium=premium.with(reel,c.stopIndex());mask|=reel.bit();}premiumBarSeen=CATALOGUE.evaluation(premium).winningBarConfirmationLines()!=0;}
        }
        assertTrue(premiumBarSeen,"premium control should retain a BAR-BAR-BAR path");
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
                final int fixedMask=mask;var candidates=CATALOGUE.candidates(role,mask,e.stops()).stream().filter(c->role==DisplayRole.PREMIUM_B||c.winningBarConfirmationLines()==0).toList();
                if(candidates.isEmpty())continue;
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
    @Test void sevenTenpaiCanMissOnEveryThirdReelWithoutAbnormalBonusPull(){
        for(var entry:List.of(DisplayRole.BIG_ENTRY,DisplayRole.REG_ENTRY)){
            var thirdReels=EnumSet.noneOf(Reel.class);
            for(var order:ReelVerification.orders()){
                Reel first=order.get(0),second=order.get(1),third=order.get(2);
                boolean witnessed=false;
                for(int p1=0;p1<21&&!witnessed;p1++)for(int p2=0;p2<21&&!witnessed;p2++){
                    StopTriplet stopped=new StopTriplet(0,0,0);int mask=0;
                    var one=SOLVER.choose(DisplayRole.BONUS,entry,mask,stopped,first,p1,false,false);stopped=stopped.with(first,one.stopIndex());mask|=first.bit();
                    var two=SOLVER.choose(DisplayRole.BONUS,entry,mask,stopped,second,p2,false,false);stopped=stopped.with(second,two.stopIndex());mask|=second.bit();
                    if(StopCatalogue.sevenTenpaiLines(stopped,mask)==0)continue;
                    for(int p3=0;p3<21;p3++){
                        var three=SOLVER.choose(DisplayRole.BONUS,entry,mask,stopped,third,p3,false,false);
                        var finalStops=stopped.with(third,three.stopIndex());var evaluation=CATALOGUE.evaluation(finalStops);
                        if(evaluation.valid(entry))assertTrue(three.slip()<=4,"direct bonus entry may only use natural 0..4 slip: "+entry+" "+order+" press="+p3+" slip="+three.slip());
                        else if(evaluation.valid(DisplayRole.BONUS)){assertTrue(three.slip()<=4,"tenpai miss should stay natural: "+entry+" "+order+" press="+p3+" slip="+three.slip());witnessed=true;thirdReels.add(third);break;}
                    }
                }
            }
            assertEquals(EnumSet.allOf(Reel.class),thirdReels,"every reel must be able to miss a seven tenpai as third stop for "+entry);
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
