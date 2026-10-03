package jp.pirijuggler.common.reel;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;

/** Exports actual production decisions, using numbered top positions for the economy solver. */
public final class SkillStopEconomyExport {
    private static final int[][] ORDERS={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
    private static final String[] NAMES={"MISS","R","G","B","C","P","BIG","REG","CBIG","CREG","PBIG","PREG","7CBAR","B7B","GGP","PGP","PP7","PBARP"};
    private static int index(int top) { return SkillStopReels.stopIndex(top+1); }
    private static int top(int index) { return SkillStopReels.topNumber(index)-1; }
    public static void main(String[] args) throws Exception {
        List<Object> normal=new ArrayList<>(),pending=new ArrayList<>();
        for(SkillStopRole role:SkillStopRole.values()) add(normal,NAMES[role.ordinal()],role,null,SkillStopControl.Premium.NONE);
        for(SkillStopRole role:SkillStopRole.values()) if(role.bigFamily()) add(normal,"F"+(role==SkillStopRole.BIG?"MISS":role==SkillStopRole.CHERRY_BIG?"C":role==SkillStopRole.PIERO_BIG?"P":NAMES[role.ordinal()]),role,null,SkillStopControl.Premium.F);
        add(normal,"BC",SkillStopRole.CHERRY_BIG,null,SkillStopControl.Premium.B);
        for(String bonus:List.of("BIG","REG")) for(int i=0;i<6;i++) add(pending,bonus+NAMES[i],SkillStopRole.values()[i],bonus,SkillStopControl.Premium.NONE);
        Files.writeString(Path.of(args[0]),new Gson().toJson(Map.of("normal",normal,"pending",pending)));
        System.out.println("Production controller exported: "+normal.size()+" normal, "+pending.size()+" pending models");
    }
    private static void add(List<Object> result,String name,SkillStopRole role,String bonus,SkillStopControl.Premium premium) {
        SkillStopControl controller=new SkillStopControl();
        var context=bonus==null?SkillStopControl.Context.normal(role,premium):SkillStopControl.Context.pending(role,bonus);
        for(int oi=0;oi<6;oi++) {
            int[] order=ORDERS[oi],first=new int[21]; int[][] second=new int[21][21]; int[][][] third=new int[21][21][21];
            for(int a=0;a<21;a++) {
                Reel r1=Reel.values()[order[0]]; var c1=controller.choose(context,SkillStopHistory.empty(),r1,index(a));
                var h1=SkillStopHistory.empty().append(r1,index(a),c1.stopIndex()); first[a]=top(c1.stopIndex());
                for(int b=0;b<21;b++) {
                    Reel r2=Reel.values()[order[1]];var c2=controller.choose(context,h1,r2,index(b));
                    var h2=h1.append(r2,index(b),c2.stopIndex());second[a][b]=top(c2.stopIndex());
                    for(int c=0;c<21;c++) {
                        Reel r3=Reel.values()[order[2]];var c3=controller.choose(context,h2,r3,index(c));
                        var outcome=controller.outcome(context,h2.append(r3,index(c),c3.stopIndex()));
                        third[a][b][c]=(top(c3.stopIndex())<<9)|(outcome.entryBonus()!=null?256:0)|(outcome.replay()?128:0)|outcome.payout();
                    }
                }
            }
            Map<String,Object> model=new LinkedHashMap<>();model.put("name",name);model.put("oi",oi);model.put("order",""+"LMR".charAt(order[0])+"LMR".charAt(order[1])+"LMR".charAt(order[2]));
            model.put("si",Math.min(role.ordinal(),5));model.put("small",role.cherry()?"C":role==SkillStopRole.PIERO_BIG||role==SkillStopRole.PIERO_REG?"P":role.ordinal()<6?NAMES[role.ordinal()]:"MISS");
            model.put("one",role.oneMedal()?role.pattern():0);model.put("F",premium==SkillStopControl.Premium.F);model.put("B",premium==SkillStopControl.Premium.B);
            model.put("type",context.bonus());model.put("first",first);model.put("second",second);model.put("third",third);result.add(model);
        }
    }
}
