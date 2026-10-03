package jp.pirijuggler.common.reel;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;

/** Finds deterministic real-input vectors for Phase06 runtime acceptance from production control. */
public final class SkillStopPhase06Vectors {
    private static final Reel[] ORDER={Reel.LEFT,Reel.CENTER,Reel.RIGHT};
    public static void main(String[] args) throws Exception {
        var out=new LinkedHashMap<String,Object>();
        var lines=new LinkedHashMap<String,Object>();
        lines.put("upperSeven",findSeven(-1));
        lines.put("middleSeven",findSeven(0));
        lines.put("lowerSeven",findSeven(1));
        out.put("sevenLines",lines);

        var one=new LinkedHashMap<String,Object>();
        for(var role:List.of(SkillStopRole.ONE_A,SkillStopRole.ONE_B,SkillStopRole.ONE_CD,SkillStopRole.ONE_E,SkillStopRole.ONE_F,SkillStopRole.ONE_H)){
            var v=new LinkedHashMap<String,Object>();
            v.put("recover",find(role,o->o.payout()==1&&o.entryBonus()==null));
            v.put("miss",find(role,o->o.payout()==0&&o.entryBonus()==null));
            v.put("big",find(role,o->"BIG".equals(o.entryBonus())));
            one.put(role.name(),v);
        }
        out.put("oneMedal",one);
        Files.createDirectories(Path.of(args[0]).getParent());
        Files.writeString(Path.of(args[0]),new Gson().toJson(out));
        System.out.println("SKILL_STOP_PHASE06_VECTORS="+args[0]);
    }
    private static Map<String,Object> findSeven(int row){
        return find(SkillStopRole.BIG,o->"BIG".equals(o.entryBonus()),h->
            SkillStopReels.row(Reel.LEFT,h.stop(0),row)==Symbol.SEVEN &&
            SkillStopReels.row(Reel.CENTER,h.stop(1),row)==Symbol.SEVEN &&
            SkillStopReels.row(Reel.RIGHT,h.stop(2),row)==Symbol.SEVEN);
    }
    private interface OutcomeCheck { boolean ok(SkillStopControl.Outcome o); }
    private interface HistoryCheck { boolean ok(SkillStopHistory h); }
    private static Map<String,Object> find(SkillStopRole role,OutcomeCheck check){return find(role,check,h->true);}
    private static Map<String,Object> find(SkillStopRole role,OutcomeCheck check,HistoryCheck hcheck){
        var c=new SkillStopControl();var ctx=SkillStopControl.Context.normal(role,SkillStopControl.Premium.NONE);
        for(int a=0;a<21;a++)for(int b=0;b<21;b++)for(int d=0;d<21;d++){
            var h=SkillStopHistory.empty();
            int[] input={a,b,d},stops=new int[3],slips=new int[3];
            boolean valid=true;
            for(int i=0;i<3;i++){
                try{
                    var ch=c.choose(ctx,h,ORDER[i],input[i]);
                    stops[i]=ch.stopIndex();slips[i]=ch.slip();
                    h=h.append(ORDER[i],input[i],ch.stopIndex());
                }catch(RuntimeException e){valid=false;break;}
            }
            if(!valid)continue;
            var o=c.outcome(ctx,h);
            if(check.ok(o)&&hcheck.ok(h)){
                var m=new LinkedHashMap<String,Object>();
                m.put("inputIndex",input);m.put("inputTop",tops(input));m.put("stopIndex",stops);m.put("stopTop",tops(stops));m.put("slip",slips);
                m.put("payout",o.payout());m.put("entryBonus",o.entryBonus());m.put("patterns",o.patterns());
                return m;
            }
        }
        throw new IllegalStateException("No vector for "+role);
    }
    private static int[] tops(int[] idx){int[] out=new int[idx.length];for(int i=0;i<idx.length;i++)out[i]=SkillStopReels.topNumber(idx[i]);return out;}
}
