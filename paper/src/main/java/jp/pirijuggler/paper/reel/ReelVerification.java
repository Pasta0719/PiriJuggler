package jp.pirijuggler.paper.reel;
import jp.pirijuggler.common.reel.Reel;
import java.util.*;
public final class ReelVerification {
    private static final List<List<Reel>> ORDERS=List.of(List.of(Reel.LEFT,Reel.CENTER,Reel.RIGHT),List.of(Reel.LEFT,Reel.RIGHT,Reel.CENTER),List.of(Reel.CENTER,Reel.LEFT,Reel.RIGHT),List.of(Reel.CENTER,Reel.RIGHT,Reel.LEFT),List.of(Reel.RIGHT,Reel.LEFT,Reel.CENTER),List.of(Reel.RIGHT,Reel.CENTER,Reel.LEFT));
    public record Report(Map<DisplayRole,Integer> counts,int allSequences,int premiumFSecondChecks,int premiumFSequences){}
    public static List<List<Reel>> orders(){return ORDERS;}
    public static Report verify(StopSolver solver){
        var catalogue=solver.catalogue();if(catalogue.evaluations().size()!=9261)throw new IllegalStateException("Expected all 9261 triplets");
        for(var role:DisplayRole.values())if(catalogue.candidates(role).size()<role.minimum())throw new IllegalStateException("Strict candidate minimum failed: "+role+"="+catalogue.candidates(role).size());
        if(catalogue.candidates(DisplayRole.BONUS).size()!=447)throw new IllegalStateException("Expected 447 pure-bonus reach/miss triplets");
        if(catalogue.candidates(DisplayRole.BONUS_CHERRY).size()!=24)throw new IllegalStateException("Expected 24 cherry-bonus reach/miss triplets");
        int normal=0,premium=0,seconds=0;
        for(var role:DisplayRole.values())normal+=verifySequences(solver,role,false);
        for(var role:List.of(DisplayRole.BONUS,DisplayRole.BONUS_CHERRY,DisplayRole.PIERO_BONUS)){premium+=verifySequences(solver,role,true);seconds+=6*21*21;}
        return new Report(catalogue.counts(),normal,seconds,premium);
    }
    private static int verifySequences(StopSolver solver,DisplayRole role,boolean premium){
        int count=0;for(var order:ORDERS)for(int p1=0;p1<21;p1++){
            Reel first=order.get(0),second=order.get(1),third=order.get(2);StopTriplet stops=new StopTriplet(0,0,0);
            try {
                var one=solver.choose(role,0,stops,first,p1,premium);stops=stops.with(first,one.stopIndex());
                for(int p2=0;p2<21;p2++){
                    var two=solver.choose(role,first.bit(),stops,second,p2,premium);var pair=stops.with(second,two.stopIndex());int mask=first.bit()|second.bit();
                    if(premium&&StopCatalogue.sevenTenpaiLines(pair,mask)!=0)throw new IllegalStateException("Premium F actual tenpai: role="+role+" order="+order+" presses="+p1+","+p2);
                    for(int p3=0;p3<21;p3++){
                        var three=solver.choose(role,mask,pair,third,p3,premium);var result=pair.with(third,three.stopIndex());
                        if(!solver.catalogue().evaluation(result).valid(role))throw new IllegalStateException("Unreachable role="+role+" order="+order+" pressed="+p1+","+p2+","+p3);
                        count++;
                    }
                }
            }catch(RuntimeException e){throw new IllegalStateException("Reel self-test failed role="+role+" order="+order+" firstPressedIndex="+p1,e);}
        }return count;
    }
    private ReelVerification(){}
}
