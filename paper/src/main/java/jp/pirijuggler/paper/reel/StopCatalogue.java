package jp.pirijuggler.paper.reel;

import jp.pirijuggler.common.reel.*;
import java.util.*;
import static jp.pirijuggler.common.reel.Symbol.*;

/** Exhaustive immutable catalogue of all 9,261 triplets, including rejected shapes. */
public final class StopCatalogue {
    private static final Symbol[][] REACH_PATTERNS={
        {SEVEN,BAR,SEVEN},{SEVEN,BAR,BAR},{BAR,SEVEN,SEVEN},{BAR,SEVEN,BAR},{BAR,BAR,SEVEN},{BAR,BAR,BAR},
        {PIERO,SEVEN,PIERO},{PIERO,BAR,PIERO}
    };
    public record Evaluation(StopTriplet stops,int winningGrapeLines,int winningBellLines,int winningPieroLines,int winningReplayLines,
                             boolean leftTopCherry,boolean leftMiddleCherry,boolean leftBottomCherry,int winningBigLines,int winningRegLines,
                             int winningBarConfirmationLines,int winningReachLines) {
        public int lineMask(DisplayRole role){return switch(role){case GRAPE->winningGrapeLines;case BELL->winningBellLines;case PIERO->winningPieroLines;case REPLAY->winningReplayLines;case BIG_ENTRY->winningBigLines;case REG_ENTRY->winningRegLines;case BONUS,BONUS_CHERRY->winningReachLines;default->0;};}
        public int baseLines(){return Integer.bitCount(winningGrapeLines)+Integer.bitCount(winningBellLines)+Integer.bitCount(winningPieroLines)+Integer.bitCount(winningReplayLines)+Integer.bitCount(winningBigLines)+Integer.bitCount(winningRegLines);}
        public int totalLines(){return baseLines()+Integer.bitCount(winningReachLines);}
        public boolean anyCherry(){return leftTopCherry||leftMiddleCherry||leftBottomCherry;}
        public boolean hasBonusSymbolPair(){return bonusSymbolPairLines(stops)!=0;}
        public boolean valid(DisplayRole role){return switch(role){
            case MISS->baseLines()==0&&winningReachLines==0&&!anyCherry();
            case BONUS->!anyCherry()&&baseLines()==0&&Integer.bitCount(winningReachLines)==1;
            case BONUS_CHERRY->baseLines()==0&&Integer.bitCount(winningReachLines)==1&&!leftMiddleCherry&&(leftTopCherry^leftBottomCherry);
            case CHERRY->baseLines()==0&&winningReachLines==0&&!hasBonusSymbolPair()&&!leftMiddleCherry&&(leftTopCherry^leftBottomCherry);
            case PREMIUM_B->baseLines()==0&&winningReachLines==0&&leftMiddleCherry&&!leftTopCherry&&!leftBottomCherry;
            default->winningReachLines==0&&!anyCherry()&&baseLines()==1&&Integer.bitCount(lineMask(role))==1;};}
        public int targetRank(DisplayRole role){if(!valid(role))throw new IllegalArgumentException("Not a strict candidate");return switch(role){case MISS->5;case BONUS,BONUS_CHERRY->Integer.numberOfTrailingZeros(winningReachLines);case CHERRY->leftTopCherry?1:2;case PREMIUM_B->0;default->Integer.numberOfTrailingZeros(lineMask(role));};}
    }
    private final List<Evaluation> evaluations;
    private final Map<DisplayRole,List<Evaluation>> candidates;
    private final Map<DisplayRole,Map<Integer,List<Evaluation>>> fixed;
    public StopCatalogue(){
        var all=new ArrayList<Evaluation>(9261);var byRole=new EnumMap<DisplayRole,List<Evaluation>>(DisplayRole.class);
        for(var role:DisplayRole.values())byRole.put(role,new ArrayList<>());
        for(int l=0;l<21;l++)for(int c=0;c<21;c++)for(int r=0;r<21;r++){var e=evaluate(new StopTriplet(l,c,r));all.add(e);for(var role:DisplayRole.values())if(e.valid(role))byRole.get(role).add(e);}
        evaluations=List.copyOf(all);var groups=new EnumMap<DisplayRole,Map<Integer,List<Evaluation>>>(DisplayRole.class);
        byRole.replaceAll((role,list)->List.copyOf(list));candidates=Map.copyOf(byRole);
        for(var role:DisplayRole.values()){
            var mutable=new HashMap<Integer,List<Evaluation>>();for(var e:candidates(role))for(int mask=0;mask<8;mask++)mutable.computeIfAbsent(e.stops.fixedKey(mask),k->new ArrayList<>()).add(e);
            mutable.replaceAll((key,list)->List.copyOf(list));groups.put(role,Map.copyOf(mutable));
        }fixed=Map.copyOf(groups);
    }
    public static Evaluation evaluate(StopTriplet stops){
        return new Evaluation(stops,winning(stops,GRAPE,GRAPE,GRAPE),winning(stops,BELL,BELL,BELL),winning(stops,PIERO,PIERO,PIERO),winning(stops,REPLAY,REPLAY,REPLAY),
            FixedReels.row(Reel.LEFT,stops.left(),-1)==CHERRY,FixedReels.row(Reel.LEFT,stops.left(),0)==CHERRY,FixedReels.row(Reel.LEFT,stops.left(),1)==CHERRY,
            winning(stops,SEVEN,SEVEN,SEVEN),winning(stops,SEVEN,SEVEN,BAR),winning(stops,BAR,BAR,BAR),reachLines(stops));
    }
    private static int winning(StopTriplet stops,Symbol left,Symbol center,Symbol right){int bits=0;for(var line:Payline.values())if(FixedReels.row(Reel.LEFT,stops.left(),line.row(Reel.LEFT))==left&&FixedReels.row(Reel.CENTER,stops.center(),line.row(Reel.CENTER))==center&&FixedReels.row(Reel.RIGHT,stops.right(),line.row(Reel.RIGHT))==right)bits|=1<<line.ordinal();return bits;}
    public static int reachLines(StopTriplet stops){int bits=0;for(var pattern:REACH_PATTERNS)bits|=winning(stops,pattern[0],pattern[1],pattern[2]);return bits;}
    public static int bonusSymbolPairLines(StopTriplet stops){
        int bits=0;
        for(var line:Payline.values()){
            int count=0;
            for(var reel:Reel.values()){
                Symbol symbol=FixedReels.row(reel,stops.stop(reel),line.row(reel));
                if(symbol==SEVEN||symbol==BAR)count++;
            }
            if(count>=2)bits|=1<<line.ordinal();
        }
        return bits;
    }
    public static boolean isReachPattern(Symbol left,Symbol center,Symbol right){for(var pattern:REACH_PATTERNS)if(pattern[0]==left&&pattern[1]==center&&pattern[2]==right)return true;return false;}
    public static int sevenTenpaiLines(StopTriplet stops,int mask){
        if(Integer.bitCount(mask)!=2||mask<0||mask>7)throw new IllegalArgumentException("Tenpai requires exactly two stopped reels");
        int count=0;for(var line:Payline.values()){boolean both=true;for(var reel:Reel.values())if((mask&reel.bit())!=0&&FixedReels.row(reel,stops.stop(reel),line.row(reel))!=SEVEN)both=false;if(both)count++;}return count;
    }
    public List<Evaluation> evaluations(){return evaluations;}public Evaluation evaluation(StopTriplet stops){return evaluations.get(stops.id());}
    public List<Evaluation> candidates(DisplayRole role){return candidates.get(role);}
    public List<Evaluation> candidates(DisplayRole role,int mask,StopTriplet stops){if(mask<0||mask>7)throw new IllegalArgumentException("Invalid mask");return fixed.get(role).getOrDefault(stops.fixedKey(mask),List.of());}
    public Map<DisplayRole,Integer> counts(){var out=new EnumMap<DisplayRole,Integer>(DisplayRole.class);for(var role:DisplayRole.values())out.put(role,candidates(role).size());return Map.copyOf(out);}
}
