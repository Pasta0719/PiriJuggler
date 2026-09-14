package jp.pirijuggler.paper.reel;

import jp.pirijuggler.common.reel.*;
import java.util.concurrent.ConcurrentHashMap;

/** Candidate existence is preserved after every stop; all ordering is integer/deterministic. */
public final class StopSolver {
    public record Choice(StopTriplet candidate,int targetRank,int pressedIndex,int stopIndex,int slip,int durationMs){}
    private record Key(DisplayRole role,int fixedKey,Reel reel,boolean nonTenpai){}
    private final StopCatalogue catalogue;
    private final ConcurrentHashMap<Key,Choice[]> cache=new ConcurrentHashMap<>();
    public StopSolver(StopCatalogue catalogue){this.catalogue=catalogue;}
    public StopCatalogue catalogue(){return catalogue;}
    public Choice choose(DisplayRole role,int stoppedMask,StopTriplet stopped,Reel reel,int pressedIndex,boolean premiumF){
        if(pressedIndex<0||pressedIndex>=21||stoppedMask<0||stoppedMask>7||(stoppedMask&reel.bit())!=0)throw new IllegalArgumentException("Invalid STOP conditions");
        if(premiumF&&role!=DisplayRole.MISS&&role!=DisplayRole.CHERRY&&role!=DisplayRole.PIERO)throw new IllegalArgumentException("Invalid premium F base");
        boolean filter=premiumF&&Integer.bitCount(stoppedMask)==1;
        var key=new Key(role,stopped.fixedKey(stoppedMask),reel,filter);
        return cache.computeIfAbsent(key,ignored->choices(role,stoppedMask,stopped,reel,filter))[pressedIndex];
    }
    private Choice[] choices(DisplayRole role,int mask,StopTriplet stopped,Reel reel,boolean filter){
        Choice[] best=new Choice[21];
        for(var candidate:catalogue.candidates(role,mask,stopped)){
            if(filter&&StopCatalogue.sevenTenpaiLines(candidate.stops(),mask|reel.bit())!=0)continue;
            for(int p=0;p<21;p++){
                int stop=candidate.stops().stop(reel),slip=ReelMotion.slip(stop,p),rank=candidate.targetRank(role);Choice old=best[p];
                if(old==null||slip<old.slip||slip==old.slip&&(rank<old.targetRank||rank==old.targetRank&&candidate.stops().id()<old.candidate.id()))best[p]=new Choice(candidate.stops(),rank,p,stop,slip,ReelMotion.durationMs(slip));
            }
        }
        if(best[0]==null)throw new IllegalStateException("No candidate: role="+role+" stoppedMask="+mask+" stops="+stopped+" reel="+reel+" premiumF="+filter);
        return best;
    }
}
