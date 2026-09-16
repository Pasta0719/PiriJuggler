package jp.pirijuggler.paper.reel;

import jp.pirijuggler.common.reel.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Candidate existence is preserved after every stop; all ordering is integer/deterministic. */
public final class StopSolver {
    private static final int NATURAL_MAX_SLIP = 4;
    private static final int FALLBACK_LINE_SLIP_WINDOW = 2;
    private static final EnumSet<DisplayRole> DIVERSIFIED_LINE_ROLES = EnumSet.of(
            DisplayRole.GRAPE, DisplayRole.BELL, DisplayRole.PIERO, DisplayRole.PIERO_BONUS, DisplayRole.REPLAY,
            DisplayRole.BIG_ENTRY, DisplayRole.REG_ENTRY);

    public record Choice(StopTriplet candidate,int targetRank,int pressedIndex,int stopIndex,int slip,int durationMs){}
    private record Key(DisplayRole role,DisplayRole alternateRole,int fixedKey,Reel reel,boolean premiumF,boolean allowBarConfirmation,boolean forbidRightFirstGrapeSevenBar){}
    private final StopCatalogue catalogue;
    private final ConcurrentHashMap<Key,Choice[]> cache=new ConcurrentHashMap<>();
    public StopSolver(StopCatalogue catalogue){this.catalogue=catalogue;}
    public StopCatalogue catalogue(){return catalogue;}
    public Choice choose(DisplayRole role,int stoppedMask,StopTriplet stopped,Reel reel,int pressedIndex,boolean premiumF){
        return choose(role,null,stoppedMask,stopped,reel,pressedIndex,premiumF,premiumF||role==DisplayRole.PREMIUM_B,false);
    }
    public Choice choose(DisplayRole role,int stoppedMask,StopTriplet stopped,Reel reel,int pressedIndex,boolean premiumF,boolean forbidRightFirstGrapeSevenBar){
        return choose(role,null,stoppedMask,stopped,reel,pressedIndex,premiumF,premiumF||role==DisplayRole.PREMIUM_B,forbidRightFirstGrapeSevenBar);
    }
    public Choice choose(DisplayRole role,DisplayRole alternateRole,int stoppedMask,StopTriplet stopped,Reel reel,int pressedIndex,boolean premiumF,boolean forbidRightFirstGrapeSevenBar){
        return choose(role,alternateRole,stoppedMask,stopped,reel,pressedIndex,premiumF,premiumF||role==DisplayRole.PREMIUM_B,forbidRightFirstGrapeSevenBar);
    }
    public Choice choose(DisplayRole role,DisplayRole alternateRole,int stoppedMask,StopTriplet stopped,Reel reel,int pressedIndex,boolean premiumF,boolean allowBarConfirmation,boolean forbidRightFirstGrapeSevenBar){
        if(pressedIndex<0||pressedIndex>=21||stoppedMask<0||stoppedMask>7||(stoppedMask&reel.bit())!=0)throw new IllegalArgumentException("Invalid STOP conditions");
        if(premiumF&&role!=DisplayRole.BONUS&&role!=DisplayRole.BONUS_CHERRY&&role!=DisplayRole.PIERO_BONUS)throw new IllegalArgumentException("Invalid premium F base");
        if(premiumF&&alternateRole!=null)throw new IllegalArgumentException("Premium F cannot use direct-entry alternatives");
        var key=new Key(role,alternateRole,stopped.fixedKey(stoppedMask),reel,premiumF,allowBarConfirmation,forbidRightFirstGrapeSevenBar);
        return cache.computeIfAbsent(key,ignored->choices(role,alternateRole,stoppedMask,stopped,reel,premiumF,allowBarConfirmation,forbidRightFirstGrapeSevenBar))[pressedIndex];
    }

    private Choice[] choices(DisplayRole role,DisplayRole alternateRole,int mask,StopTriplet stopped,Reel reel,boolean premiumF,boolean allowBarConfirmation,boolean forbidRightFirstGrapeSevenBar){
        var unique=new LinkedHashMap<Integer,StopCatalogue.Evaluation>();
        for(var candidate:catalogue.candidates(role,mask,stopped))unique.put(candidate.stops().id(),candidate);
        if(alternateRole!=null)for(var candidate:catalogue.candidates(alternateRole,mask,stopped))unique.putIfAbsent(candidate.stops().id(),candidate);
        var candidates=new ArrayList<StopCatalogue.Evaluation>();
        boolean secondPremiumStop=premiumF&&Integer.bitCount(mask)==1;
        for(var candidate:unique.values()){
            if(!allowBarConfirmation&&candidate.winningBarConfirmationLines()!=0)continue;
            if(secondPremiumStop&&StopCatalogue.sevenTenpaiLines(candidate.stops(),mask|reel.bit())!=0)continue;
            if(premiumF&&mask==0&&!premiumFirstStopFeasible(role,reel,candidate.stops().stop(reel)))continue;
            if(forbidRightFirstGrapeSevenBar&&mask==0&&reel==Reel.RIGHT&&StopCatalogue.isRightGrapeSevenBarStop(candidate.stops().right()))continue;
            candidates.add(candidate);
        }
        if(candidates.isEmpty())throw new IllegalStateException("No candidate: role="+role+" alternateRole="+alternateRole+" stoppedMask="+mask+" stops="+stopped+" reel="+reel+" premiumF="+premiumF+" allowBarConfirmation="+allowBarConfirmation+" forbidRightFirstGrapeSevenBar="+forbidRightFirstGrapeSevenBar);

        Choice[] best=new Choice[21];
        for(int p=0;p<21;p++){
            int minSlip=21;
            boolean hasNatural=false;
            for(var candidate:candidates){
                int slip=ReelMotion.slip(candidate.stops().stop(reel),p);
                minSlip=Math.min(minSlip,slip);
                if(slip<=NATURAL_MAX_SLIP)hasNatural=true;
            }

            int minAllowedSlip;
            int maxAllowedSlip;
            if(!DIVERSIFIED_LINE_ROLES.contains(role)){
                minAllowedSlip=minSlip;
                maxAllowedSlip=minSlip;
            }else if(minSlip==0){
                minAllowedSlip=0;
                maxAllowedSlip=0;
            }else if(hasNatural){
                minAllowedSlip=1;
                maxAllowedSlip=NATURAL_MAX_SLIP;
            }else{
                minAllowedSlip=minSlip;
                maxAllowedSlip=Math.min(20,minSlip+FALLBACK_LINE_SLIP_WINDOW);
            }

            int preferredLine=preferredLine(mask,stopped,reel,p);
            boolean naturalSelection=DIVERSIFIED_LINE_ROLES.contains(role)&&(minSlip==0||hasNatural);
            StopCatalogue.Evaluation selected=null;
            int selectedSlip=Integer.MAX_VALUE;
            int selectedLineDistance=Integer.MAX_VALUE;
            int selectedLineDirection=Integer.MAX_VALUE;
            int selectedRank=Integer.MAX_VALUE;
            for(var candidate:candidates){
                int stop=candidate.stops().stop(reel);
                int slip=ReelMotion.slip(stop,p);
                if(slip<minAllowedSlip||slip>maxAllowedSlip)continue;
                int rank=targetRank(candidate,role,alternateRole);
                int lineDistance=DIVERSIFIED_LINE_ROLES.contains(role)?lineDistance(rank,preferredLine):0;
                int lineDirection=DIVERSIFIED_LINE_ROLES.contains(role)&&rank<5?Math.floorMod(rank-preferredLine,5):rank;
                boolean better;
                if(selected==null){
                    better=true;
                }else if(naturalSelection){
                    better=slip<selectedSlip
                            ||slip==selectedSlip&&lineDistance<selectedLineDistance
                            ||slip==selectedSlip&&lineDistance==selectedLineDistance&&lineDirection<selectedLineDirection
                            ||slip==selectedSlip&&lineDistance==selectedLineDistance&&lineDirection==selectedLineDirection&&rank<selectedRank
                            ||slip==selectedSlip&&lineDistance==selectedLineDistance&&lineDirection==selectedLineDirection&&rank==selectedRank&&candidate.stops().id()<selected.stops().id();
                }else{
                    better=lineDistance<selectedLineDistance
                            ||lineDistance==selectedLineDistance&&slip<selectedSlip
                            ||lineDistance==selectedLineDistance&&slip==selectedSlip&&lineDirection<selectedLineDirection
                            ||lineDistance==selectedLineDistance&&slip==selectedSlip&&lineDirection==selectedLineDirection&&rank<selectedRank
                            ||lineDistance==selectedLineDistance&&slip==selectedSlip&&lineDirection==selectedLineDirection&&rank==selectedRank&&candidate.stops().id()<selected.stops().id();
                }
                if(better){
                    selected=candidate;
                    selectedSlip=slip;
                    selectedLineDistance=lineDistance;
                    selectedLineDirection=lineDirection;
                    selectedRank=rank;
                }
            }
            if(selected==null)throw new IllegalStateException("No selectable candidate: role="+role+" alternateRole="+alternateRole+" stoppedMask="+mask+" stops="+stopped+" reel="+reel+" pressedIndex="+p);
            int stop=selected.stops().stop(reel);
            best[p]=new Choice(selected.stops(),selectedRank,p,stop,selectedSlip,ReelMotion.durationMs(selectedSlip));
        }
        return best;
    }

    private static int targetRank(StopCatalogue.Evaluation candidate,DisplayRole role,DisplayRole alternateRole){
        if(candidate.valid(role))return candidate.targetRank(role);
        if(alternateRole!=null&&candidate.valid(alternateRole))return candidate.targetRank(alternateRole);
        throw new IllegalStateException("Candidate does not match either display role");
    }

    private boolean premiumFirstStopFeasible(DisplayRole role,Reel first,int firstStop){
        int firstMask=first.bit();
        StopTriplet partial=new StopTriplet(0,0,0).with(first,firstStop);
        var remaining=catalogue.candidates(role,firstMask,partial);
        for(var second:Reel.values()){
            if(second==first)continue;
            boolean possible=false;
            int pairMask=firstMask|second.bit();
            for(var candidate:remaining){
                if(StopCatalogue.sevenTenpaiLines(candidate.stops(),pairMask)==0){possible=true;break;}
            }
            if(!possible)return false;
        }
        return true;
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
