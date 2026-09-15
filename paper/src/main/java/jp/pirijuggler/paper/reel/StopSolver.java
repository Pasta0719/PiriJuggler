package jp.pirijuggler.paper.reel;

import jp.pirijuggler.common.reel.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Candidate existence is preserved after every stop; all ordering is integer/deterministic. */
public final class StopSolver {
    private static final int LINE_SLIP_WINDOW = 2;
    private static final EnumSet<DisplayRole> DIVERSIFIED_LINE_ROLES = EnumSet.of(
            DisplayRole.GRAPE, DisplayRole.BELL, DisplayRole.PIERO, DisplayRole.REPLAY,
            DisplayRole.BIG_ENTRY, DisplayRole.REG_ENTRY);

    public record Choice(StopTriplet candidate,int targetRank,int pressedIndex,int stopIndex,int slip,int durationMs){}
    private record Key(DisplayRole role,int fixedKey,Reel reel,boolean nonTenpai){}
    private final StopCatalogue catalogue;
    private final ConcurrentHashMap<Key,Choice[]> cache=new ConcurrentHashMap<>();
    public StopSolver(StopCatalogue catalogue){this.catalogue=catalogue;}
    public StopCatalogue catalogue(){return catalogue;}
    public Choice choose(DisplayRole role,int stoppedMask,StopTriplet stopped,Reel reel,int pressedIndex,boolean premiumF){
        if(pressedIndex<0||pressedIndex>=21||stoppedMask<0||stoppedMask>7||(stoppedMask&reel.bit())!=0)throw new IllegalArgumentException("Invalid STOP conditions");
        if(premiumF&&role!=DisplayRole.BONUS&&role!=DisplayRole.CHERRY&&role!=DisplayRole.PIERO)throw new IllegalArgumentException("Invalid premium F base");
        boolean filter=premiumF&&Integer.bitCount(stoppedMask)==1;
        var key=new Key(role,stopped.fixedKey(stoppedMask),reel,filter);
        return cache.computeIfAbsent(key,ignored->choices(role,stoppedMask,stopped,reel,filter))[pressedIndex];
    }

    private Choice[] choices(DisplayRole role,int mask,StopTriplet stopped,Reel reel,boolean filter){
        var candidates=new ArrayList<StopCatalogue.Evaluation>();
        for(var candidate:catalogue.candidates(role,mask,stopped)){
            if(filter&&StopCatalogue.sevenTenpaiLines(candidate.stops(),mask|reel.bit())!=0)continue;
            candidates.add(candidate);
        }
        if(candidates.isEmpty())throw new IllegalStateException("No candidate: role="+role+" stoppedMask="+mask+" stops="+stopped+" reel="+reel+" premiumF="+filter);

        Choice[] best=new Choice[21];
        for(int p=0;p<21;p++){
            int minSlip=21;
            for(var candidate:candidates)minSlip=Math.min(minSlip,ReelMotion.slip(candidate.stops().stop(reel),p));
            int maxSlip=minSlip;
            if(DIVERSIFIED_LINE_ROLES.contains(role)&&minSlip>0)maxSlip=Math.min(20,minSlip+LINE_SLIP_WINDOW);
            int preferredLine=preferredLine(mask,stopped,reel,p);

            StopCatalogue.Evaluation selected=null;
            int selectedSlip=Integer.MAX_VALUE;
            int selectedLineDistance=Integer.MAX_VALUE;
            int selectedLineDirection=Integer.MAX_VALUE;
            int selectedRank=Integer.MAX_VALUE;
            for(var candidate:candidates){
                int stop=candidate.stops().stop(reel);
                int slip=ReelMotion.slip(stop,p);
                if(slip>maxSlip)continue;
                int rank=candidate.targetRank(role);
                int lineDistance=DIVERSIFIED_LINE_ROLES.contains(role)?lineDistance(rank,preferredLine):0;
                int lineDirection=DIVERSIFIED_LINE_ROLES.contains(role)&&rank<5?Math.floorMod(rank-preferredLine,5):rank;
                if(selected==null
                        ||lineDistance<selectedLineDistance
                        ||lineDistance==selectedLineDistance&&slip<selectedSlip
                        ||lineDistance==selectedLineDistance&&slip==selectedSlip&&lineDirection<selectedLineDirection
                        ||lineDistance==selectedLineDistance&&slip==selectedSlip&&lineDirection==selectedLineDirection&&rank<selectedRank
                        ||lineDistance==selectedLineDistance&&slip==selectedSlip&&lineDirection==selectedLineDirection&&rank==selectedRank&&candidate.stops().id()<selected.stops().id()){
                    selected=candidate;
                    selectedSlip=slip;
                    selectedLineDistance=lineDistance;
                    selectedLineDirection=lineDirection;
                    selectedRank=rank;
                }
            }
            if(selected==null)throw new IllegalStateException("No selectable candidate: role="+role+" stoppedMask="+mask+" stops="+stopped+" reel="+reel+" pressedIndex="+p);
            int stop=selected.stops().stop(reel);
            best[p]=new Choice(selected.stops(),selectedRank,p,stop,selectedSlip,ReelMotion.durationMs(selectedSlip));
        }
        return best;
    }

    private static int preferredLine(int mask,StopTriplet stopped,Reel reel,int pressedIndex){
        int seed=pressedIndex+reel.ordinal()*2+Integer.bitCount(mask);
        if((mask&Reel.LEFT.bit())!=0)seed+=stopped.left();
        if((mask&Reel.CENTER.bit())!=0)seed+=stopped.center()*2;
        if((mask&Reel.RIGHT.bit())!=0)seed+=stopped.right()*3;
        return Math.floorMod(seed,5);
    }

    private static int lineDistance(int rank,int preferredLine){
        if(rank<0||rank>=5)return 6;
        int forward=Math.floorMod(rank-preferredLine,5);
        return Math.min(forward,5-forward);
    }
}
