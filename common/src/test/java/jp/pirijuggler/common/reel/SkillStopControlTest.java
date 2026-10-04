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
        Set<SkillStopRole> visiblyWon=EnumSet.noneOf(SkillStopRole.class);
        List<String> failures=new ArrayList<>();

        for(var context:contexts){
            var control=new SkillStopControl();
            for(Reel[] order:ORDERS)for(int a0=0;a0<21;a0++){
                SkillStopHistory h1;
                try{h1=stop(control,context,SkillStopHistory.empty(),order[0],a0);}
                catch(Throwable e){recordFailure(failures,context,order,a0,-1,-1,"first",e);continue;}

                for(int b0=0;b0<21;b0++){
                    SkillStopHistory h2;
                    try{
                        h2=stop(control,context,h1,order[1],b0);
                        if(context.premium()==SkillStopControl.Premium.F)assertEquals(0,SkillStopControl.sevenTenpai(h2));
                    }catch(Throwable e){recordFailure(failures,context,order,a0,b0,-1,"second",e);continue;}

                    for(int c0=0;c0<21;c0++){
                        try{
                            var h3=stop(control,context,h2,order[2],c0);
                            var out=control.outcome(context,h3);
                            independentCheck(context,h3,out);
                            if(context.mode()==SkillStopControl.Mode.NORMAL&&context.bonus()==null){
                                int roleBit=context.role().pattern()&15;
                                if(roleBit!=0&&(out.patterns()&roleBit)!=0)visiblyWon.add(context.role());
                                if(context.role()==SkillStopRole.CHERRY&&(SkillStopReels.row(Reel.LEFT,h3.stop(0),-1)==Symbol.CHERRY||SkillStopReels.row(Reel.LEFT,h3.stop(0),0)==Symbol.CHERRY||SkillStopReels.row(Reel.LEFT,h3.stop(0),1)==Symbol.CHERRY))visiblyWon.add(SkillStopRole.CHERRY);
                            }
                            histories++;
                        }catch(Throwable e){recordFailure(failures,context,order,a0,b0,c0,"third/outcome",e);}
                    }
                }
            }
        }

        for(var role:List.of(SkillStopRole.REPLAY,SkillStopRole.GRAPE,SkillStopRole.BELL,SkillStopRole.PIERO,SkillStopRole.CHERRY))
            if(!visiblyWon.contains(role))failures.add("No reachable visible win for "+role);
        if(!failures.isEmpty())fail("SKILL_STOP_FAILURES ("+failures.size()+")\n"+String.join("\n",failures));
        assertEquals(contexts.size()*6L*21*21*21,histories);
        System.out.println("SKILL_STOP_HISTORIES_PASS "+histories);
    }

    private static void recordFailure(List<String> failures,SkillStopControl.Context context,Reel[] order,int a,int b,int c,String stage,Throwable e){
        String message=stage+" context="+context+" order="+Arrays.toString(order)+" inputs=["+a+","+b+","+c+"] "+e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
        System.err.println("SKILL_STOP_FAILURE "+message);
        if(failures.size()<200)failures.add(message);
    }
    private static void independentCheck(SkillStopControl.Context c,SkillStopHistory h,SkillStopControl.Outcome out){
        int allowed=c.role().pattern();String bonus=c.bonus();
        if(c.mode()==SkillStopControl.Mode.CHALLENGE){
            assertEquals(14,out.payout());
            assertFalse(line(h,"7","7","7"),"challenge must kick BIG entry line");
            assertFalse(line(h,"7","7","BAR"),"challenge must kick REG entry line");
            return;
        }
        // Independent five-line scan: never trust the controller's cached pattern mask alone.
        boolean replay=line(h,"R","R","R");
        boolean grape=line(h,"G","G","G");
        boolean bell=line(h,"B","B","B");
        boolean piero=line(h,"P","P","P");
        assertEquals(replay,out.replay(),"replay flag must match visible five-line replay");
        assertEquals(replay,(out.patterns()&1)!=0,"replay bit must match visible five-line replay");
        assertEquals(grape,(out.patterns()&2)!=0,"grape bit must match visible five-line grape");
        assertEquals(bell,(out.patterns()&4)!=0,"bell bit must match visible five-line bell");
        assertEquals(piero,(out.patterns()&8)!=0,"piero bit must match visible five-line piero");
        int expectedPayout=grape?8:bell?14:piero?10:0;
        if(c.role().cherry()){
            Symbol leftMiddle=SkillStopReels.row(Reel.LEFT,h.stop(0),0);
            Symbol leftTop=SkillStopReels.row(Reel.LEFT,h.stop(0),-1);
            Symbol leftBottom=SkillStopReels.row(Reel.LEFT,h.stop(0),1);
            if(leftMiddle==Symbol.CHERRY||leftTop==Symbol.CHERRY||leftBottom==Symbol.CHERRY)expectedPayout+=4;
        }
        if(c.role().oneMedal()&&(out.patterns()&c.role().pattern())!=0)expectedPayout++;
        assertEquals(expectedPayout,out.payout(),"visible five-line payout must match");
        boolean[] reach={
            line(h,"7","7","7"),line(h,"7","7","BAR"),line(h,"7","BAR","7"),line(h,"BAR","7","7"),
            line(h,"7","BAR","BAR"),line(h,"BAR","7","BAR"),line(h,"BAR","BAR","7"),line(h,"BAR","BAR","BAR")
        };
        if(bonus==null)for(boolean hit:reach)assertFalse(hit,"bonus-only reach pattern appeared without bonus");
        boolean big=reach[0],reg=reach[1];
        if(big||reg){assertEquals(big?"BIG":"REG",bonus);assertEquals(bonus,out.entryBonus());if(c.mode()!=SkillStopControl.Mode.PENDING){assertTrue(h.bit(h.order()[1]));assertTrue(h.bit(h.order()[2]));}assertNotEquals(SkillStopControl.Premium.F,c.premium());}
        else assertNull(out.entryBonus());
        for(int bit:new int[]{1,2,4,8,128,256,512,1024,2048,4096})if((out.patterns()&bit)!=0)assertTrue((allowed&bit)!=0,"non-established pattern");
        Symbol mid=SkillStopReels.row(Reel.LEFT,h.stop(0),0);if(mid==Symbol.CHERRY){assertEquals(SkillStopControl.Premium.B,c.premium());assertTrue(h.bit(0));}
        assertTrue(out.payout()>=0&&out.payout()<=14);
    }
    private static boolean line(SkillStopHistory h,String l,String m,String r){String[] target={l,m,r};for(int[] rows:LINES){boolean match=true;for(int k=0;k<3;k++)if(SkillStopReels.row(Reel.values()[k],h.stop(k),rows[k])!=symbol(target[k]))match=false;if(match)return true;}return false;}
    private static Symbol symbol(String s){return switch(s){case "7"->Symbol.SEVEN;case "R"->Symbol.REPLAY;case "G"->Symbol.GRAPE;case "C"->Symbol.CHERRY;case "B"->Symbol.BELL;case "P"->Symbol.PIERO;default->Symbol.valueOf(s);};}
    private static SkillStopHistory stop(SkillStopControl control,SkillStopControl.Context context,SkillStopHistory h,Reel r,int input){var choice=control.choose(context,h,r,input);assertTrue(choice.slip()>=0&&choice.slip()<=4);assertEquals(ReelMotion.slip(choice.stopIndex(),input),choice.slip());return h.append(r,input,choice.stopIndex());}
    private static SkillStopHistory appendTop(SkillStopControl control,SkillStopControl.Context context,SkillStopHistory h,Reel r,int top){return stop(control,context,h,r,SkillStopReels.stopIndex(top));}
    private static void assertTop(SkillStopControl control,SkillStopControl.Context context,Reel reel,int input,int expected,int slip){var choice=control.choose(context,SkillStopHistory.empty(),reel,SkillStopReels.stopIndex(input));assertEquals(expected,SkillStopReels.topNumber(choice.stopIndex()));assertEquals(slip,choice.slip());}
}
