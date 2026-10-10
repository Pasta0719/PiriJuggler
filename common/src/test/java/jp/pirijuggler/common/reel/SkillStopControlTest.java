package jp.pirijuggler.common.reel;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SkillStopControlTest {
    private static final Reel[][] ORDERS={{Reel.LEFT,Reel.CENTER,Reel.RIGHT},{Reel.LEFT,Reel.RIGHT,Reel.CENTER},{Reel.CENTER,Reel.LEFT,Reel.RIGHT},{Reel.CENTER,Reel.RIGHT,Reel.LEFT},{Reel.RIGHT,Reel.LEFT,Reel.CENTER},{Reel.RIGHT,Reel.CENTER,Reel.LEFT}};
    private static final int[][] LINES={{-1,-1,-1},{0,0,0},{1,1,1},{-1,0,1},{1,0,-1}};
    @Test void approvedStripsAndNumberMapping(){
        String[] expected={"R G BAR C G R G R B 7 P R G C BAR G R G P 7 G","C G P R C G BAR R C G R C G P BAR R C G B 7 R","B R P G B R P G B R P G B R P G B R BAR 7 G"};
        for(Reel reel:Reel.values()){String[] symbols=expected[reel.ordinal()].split(" ");for(int number=1;number<=21;number++){
            assertEquals(symbol(symbols[number-1]),SkillStopReels.numbered(reel,number));int stop=SkillStopReels.stopIndex(number);assertEquals(number,SkillStopReels.topNumber(stop));
            for(int row=-1;row<=1;row++)assertEquals(SkillStopReels.numbered(reel,number-row-1),SkillStopReels.row(reel,stop,row));
        }}
    }
    @Test void firstStopContracts(){
        var solver=new SkillStopControl();
        for(SkillStopControl.Premium premium:new SkillStopControl.Premium[]{SkillStopControl.Premium.NONE,SkillStopControl.Premium.A,SkillStopControl.Premium.C,SkillStopControl.Premium.D,SkillStopControl.Premium.E}){
            assertTop(solver,SkillStopControl.Context.normal(SkillStopRole.CHERRY_BIG,premium),Reel.CENTER,20,21,1);
            assertTop(solver,SkillStopControl.Context.normal(SkillStopRole.PIERO_BIG,premium),Reel.CENTER,20,3,4);
        }
        assertTop(solver,SkillStopControl.Context.pending(SkillStopRole.REPLAY,"BIG"),Reel.CENTER,20,1,2);
        assertTop(solver,SkillStopControl.Context.pending(SkillStopRole.MISS,"REG"),Reel.CENTER,20,20,0);
        assertTop(solver,SkillStopControl.Context.normal(SkillStopRole.BELL,SkillStopControl.Premium.NONE),Reel.RIGHT,21,1,1);
        assertTop(solver,SkillStopControl.Context.normal(SkillStopRole.PIERO_BIG,SkillStopControl.Premium.NONE),Reel.RIGHT,21,3,3);
        for(SkillStopRole role:SkillStopRole.values())if(role.oneMedal())for(int input:new int[]{20,21})assertTop(solver,SkillStopControl.Context.normal(role,SkillStopControl.Premium.NONE),Reel.CENTER,input,input,0);
        assertTop(solver,SkillStopControl.Context.normal(SkillStopRole.CHERRY,SkillStopControl.Premium.NONE),Reel.LEFT,5,6,1);
        assertTop(solver,SkillStopControl.Context.normal(SkillStopRole.CHERRY_BIG,SkillStopControl.Premium.B),Reel.LEFT,5,5,0);
    }
    @Test void reportedMixedBonusStopsAreRejected(){
        var control=new SkillStopControl();
        var context=SkillStopControl.Context.normal(SkillStopRole.MISS,SkillStopControl.Premium.NONE);
        for(int[] inputs:new int[][]{{19,7,2},{19,2,2}}){
            var h=SkillStopHistory.empty();
            for(Reel reel:Reel.values())h=stop(control,context,h,reel,inputs[reel.ordinal()]);
            assertNoUnexpectedBonusLines(context,h,control.outcome(context,h));
        }
    }
    @Test void barChallengeStillAllowsItsIntendedTarget(){
        var control=new SkillStopControl();
        var context=SkillStopControl.Context.challenge(64);
        var h=SkillStopHistory.empty();
        int[] inputs={19,15,3};
        for(Reel reel:Reel.values())h=stop(control,context,h,reel,inputs[reel.ordinal()]);
        assertTrue(control.outcome(context,h).challengeSuccess(), "BAR challenge must remain winnable");
        assertNoUnexpectedBonusLines(context,h,control.outcome(context,h));
    }
    @Test void wrongBarTargetIsNotKickedButNeverCountsAsSuccess(){
        var control=new SkillStopControl();
        var context=SkillStopControl.Context.challenge(4);
        var h=SkillStopHistory.empty();
        int[] inputs={19,15,3};
        for(Reel reel:Reel.values())h=stop(control,context,h,reel,inputs[reel.ordinal()]);
        assertArrayEquals(inputs,h.stops(),"wrong-target BAR stops must not be kicked");
        assertFalse(control.outcome(context,h).challengeSuccess());
        assertNoUnexpectedBonusLines(context,h,control.outcome(context,h));
    }
    @Test void replayLawStillPermitsLowerBonusBitEntry(){
        var solver=new SkillStopControl();var context=SkillStopControl.Context.pending(SkillStopRole.REPLAY,"BIG");var h=SkillStopHistory.empty();
        h=appendTop(solver,context,h,Reel.CENTER,20);h=appendTop(solver,context,h,Reel.LEFT,1);h=appendTop(solver,context,h,Reel.RIGHT,1);
        assertEquals("BIG",solver.outcome(context,h).entryBonus());assertEquals(0,solver.outcome(context,h).payout());
    }
    @Test void exhaustiveInputHistories(){
        List<SkillStopControl.Context> contexts=new ArrayList<>();
        for(SkillStopRole role:SkillStopRole.values()){
            contexts.add(SkillStopControl.Context.normal(role,SkillStopControl.Premium.NONE));
            if(role.bigFamily())for(var premium:List.of(SkillStopControl.Premium.A,SkillStopControl.Premium.C,SkillStopControl.Premium.D,SkillStopControl.Premium.E,SkillStopControl.Premium.F))contexts.add(SkillStopControl.Context.normal(role,premium));
        }
        contexts.add(SkillStopControl.Context.normal(SkillStopRole.CHERRY_BIG,SkillStopControl.Premium.B));
        for(String bonus:List.of("BIG","REG"))for(SkillStopRole small:List.of(SkillStopRole.MISS,SkillStopRole.REPLAY,SkillStopRole.GRAPE,SkillStopRole.BELL,SkillStopRole.CHERRY,SkillStopRole.PIERO))contexts.add(SkillStopControl.Context.pending(small,bonus));
        for(int target:new int[]{4,8,64})contexts.add(SkillStopControl.Context.challenge(target));
        long histories=0;
        for(var context:contexts){var control=new SkillStopControl();for(Reel[] order:ORDERS)for(int a=0;a<21;a++){
            var h1=stop(control,context,SkillStopHistory.empty(),order[0],a);
            for(int b=0;b<21;b++){var h2=stop(control,context,h1,order[1],b);
                if(context.premium()==SkillStopControl.Premium.F)assertEquals(0,SkillStopControl.sevenTenpai(h2));
                for(int c=0;c<21;c++){var h3=stop(control,context,h2,order[2],c);var out=control.outcome(context,h3);independentCheck(context,h3,out);histories++;}
            }
        }System.out.println("SKILL_STOP_CONTEXT_PASS "+context);}
        assertEquals(contexts.size()*6L*21*21*21,histories);System.out.println("SKILL_STOP_HISTORIES_PASS "+histories);
    }
    private static void independentCheck(SkillStopControl.Context c,SkillStopHistory h,SkillStopControl.Outcome out){
        assertNoUnexpectedBonusLines(c,h,out);
        int allowed=c.role().pattern();String bonus=c.bonus();
        if(c.mode()==SkillStopControl.Mode.CHALLENGE){assertEquals(14,out.payout());assertFalse(line(h,"7","7","7"));assertFalse(line(h,"7","7","BAR"));return;}
        assertFalse(line(h,"BAR","BAR","BAR"));
        boolean big=line(h,"7","7","7"),reg=line(h,"7","7","BAR");
        if(big||reg){assertEquals(big?"BIG":"REG",bonus);assertEquals(bonus,out.entryBonus());assertTrue(h.bit(h.order()[1]));assertTrue(h.bit(h.order()[2]));assertNotEquals(SkillStopControl.Premium.F,c.premium());}
        else assertNull(out.entryBonus());
        for(int bit:new int[]{1,2,4,8,128,256,512,1024,2048,4096})if((out.patterns()&bit)!=0)assertTrue((allowed&bit)!=0,"non-established pattern");
        Symbol mid=SkillStopReels.row(Reel.LEFT,h.stop(0),0);if(mid==Symbol.CHERRY){assertEquals(SkillStopControl.Premium.B,c.premium());assertTrue(h.bit(0));}
        assertTrue(out.payout()>=0&&out.payout()<=14);
    }
    private static void assertNoUnexpectedBonusLines(SkillStopControl.Context c,SkillStopHistory h,SkillStopControl.Outcome out){
        for(int[] rows:LINES){
            Symbol left=SkillStopReels.row(Reel.LEFT,h.stop(0),rows[0]);
            Symbol center=SkillStopReels.row(Reel.CENTER,h.stop(1),rows[1]);
            Symbol right=SkillStopReels.row(Reel.RIGHT,h.stop(2),rows[2]);
            if(!bonusSymbol(left)||!bonusSymbol(center)||!bonusSymbol(right))continue;
            boolean allowed=c.mode()==SkillStopControl.Mode.CHALLENGE
                    ? left==Symbol.BAR&&center==Symbol.BAR&&right==Symbol.BAR
                    : ("BIG".equals(out.entryBonus())&&left==Symbol.SEVEN&&center==Symbol.SEVEN&&right==Symbol.SEVEN)
                        ||("REG".equals(out.entryBonus())&&left==Symbol.SEVEN&&center==Symbol.SEVEN&&right==Symbol.BAR);
            assertTrue(allowed,"unexpected bonus-symbol line "+left+"/"+center+"/"+right+" context="+c+" stops="+Arrays.toString(h.stops()));
        }
    }
    private static boolean bonusSymbol(Symbol symbol){return symbol==Symbol.SEVEN||symbol==Symbol.BAR;}
    private static boolean line(SkillStopHistory h,String l,String m,String r){String[] target={l,m,r};for(int[] rows:LINES){boolean match=true;for(int k=0;k<3;k++)if(SkillStopReels.row(Reel.values()[k],h.stop(k),rows[k])!=symbol(target[k]))match=false;if(match)return true;}return false;}
    private static Symbol symbol(String s){return switch(s){case "7"->Symbol.SEVEN;case "R"->Symbol.REPLAY;case "G"->Symbol.GRAPE;case "C"->Symbol.CHERRY;case "B"->Symbol.BELL;case "P"->Symbol.PIERO;default->Symbol.valueOf(s);};}
    private static SkillStopHistory stop(SkillStopControl control,SkillStopControl.Context context,SkillStopHistory h,Reel r,int input){var choice=control.choose(context,h,r,input);assertTrue(choice.slip()>=0&&choice.slip()<=4);assertEquals(ReelMotion.slip(choice.stopIndex(),input),choice.slip());return h.append(r,input,choice.stopIndex());}
    private static SkillStopHistory appendTop(SkillStopControl control,SkillStopControl.Context context,SkillStopHistory h,Reel r,int top){return stop(control,context,h,r,SkillStopReels.stopIndex(top));}
    private static void assertTop(SkillStopControl control,SkillStopControl.Context context,Reel reel,int input,int expected,int slip){var choice=control.choose(context,SkillStopHistory.empty(),reel,SkillStopReels.stopIndex(input));assertEquals(expected,SkillStopReels.topNumber(choice.stopIndex()));assertEquals(slip,choice.slip());}
}
