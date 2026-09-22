package jp.pirijuggler.common.reel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Published stop-form controller for SmaSlo Million God: Kamigami no Kiseki.
 *
 * Public analysis pages publish representative winning/replay forms, not the complete
 * press-position-by-press-position control table. Piri therefore keeps source-backed
 * role formations, while MISS is selected from every physically valid three-reel stop
 * combination that does not reproduce any known role formation or a straight/diagonal
 * GOD/RED7/BLUE7/YELLOW7 line.
 */
public final class GodStopControl {
    public record Requirement(GodReelStrip.Symbol symbol,GodReelStrip.VisibleRow row) {}
    public record Rule(Requirement left,Requirement center,Requirement right,String sourceNote) {
        public Requirement requirement(int reel){
            return switch(reel){
                case 0 -> left;
                case 1 -> center;
                case 2 -> right;
                default -> throw new IllegalArgumentException("reel");
            };
        }
    }
    public record MissStop(int left,int center,int right) {}

    private static final GodReelStrip.VisibleRow[][] PAYLINES={
            {GodReelStrip.VisibleRow.TOP,GodReelStrip.VisibleRow.TOP,GodReelStrip.VisibleRow.TOP},
            {GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.MIDDLE},
            {GodReelStrip.VisibleRow.BOTTOM,GodReelStrip.VisibleRow.BOTTOM,GodReelStrip.VisibleRow.BOTTOM},
            {GodReelStrip.VisibleRow.BOTTOM,GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.TOP},
            {GodReelStrip.VisibleRow.TOP,GodReelStrip.VisibleRow.MIDDLE,GodReelStrip.VisibleRow.BOTTOM}
    };
    private static final String[] NON_MISS_FORMS={
            "UPPER_BLUE7","MIDDLE_BLUE7","ORDERED_YELLOW7","ORDERED_YELLOW7_ONE","LOWER_YELLOW7",
            "RISING_YELLOW7","MIDDLE_YELLOW7","COMMON_YELLOW7","GAIA_BELL",
            "RED7_FAKE","RED7","GOD","SP"
    };
    private static final List<MissStop> MISS_CANDIDATES=buildMissCandidates();
    private static final Map<Integer,Boolean> MISS_VIABILITY=new HashMap<>();
    private static final Map<Integer,Boolean> FAKE_RED_VIABILITY=new HashMap<>();

    private static Requirement req(GodReelStrip.Symbol symbol,GodReelStrip.VisibleRow row){
        return new Requirement(symbol,row);
    }

    /**
     * Representative published/source-locked stop forms for non-MISS roles.
     * MISS deliberately has no fixed representative form: every safe physical
     * three-reel combination is eligible through the contextual MISS controller.
     */
    public static Optional<Rule> publishedRule(String role){
        if(role==null)return Optional.empty();
        return switch(role.toUpperCase(Locale.ROOT)){
            case "UPPER_BLUE7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.BLUE7,GodReelStrip.VisibleRow.TOP),
                    req(GodReelStrip.Symbol.BLUE7,GodReelStrip.VisibleRow.TOP),
                    req(GodReelStrip.Symbol.BLUE7,GodReelStrip.VisibleRow.TOP),
                    "published upper-line replay"));
            case "MIDDLE_BLUE7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.BLUE7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.BLUE7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.BLUE7,GodReelStrip.VisibleRow.MIDDLE),
                    "published middle-line replay"));
            case "ORDERED_YELLOW7_ONE" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.BLUE7,GodReelStrip.VisibleRow.BOTTOM),
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.BLUE7,GodReelStrip.VisibleRow.TOP),
                    "Piri-specific one-medal marker for normal ordered-yellow settlement; not claimed as exact Kiseki control"));
            case "LOWER_YELLOW7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.BOTTOM),
                    req(GodReelStrip.Symbol.RED7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.BOTTOM),
                    "published 3-medal lower yellow: center reel RED7 on middle row"));
            case "COMMON_YELLOW7","ORDERED_YELLOW7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.BOTTOM),
                    req(GodReelStrip.Symbol.BLUE7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.BOTTOM),
                    "published 15-medal lower yellow: center reel BLUE7 on middle row"));
            case "RISING_YELLOW7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.BOTTOM),
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.TOP),
                    "published rising yellow 7"));
            case "MIDDLE_YELLOW7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.MIDDLE),
                    "published middle-line yellow 7"));
            case "GAIA_BELL" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.TOP),
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.YELLOW7,GodReelStrip.VisibleRow.TOP),
                    "published small-V yellow 7: left/top, center/middle, right/top"));
            case "RED7_FAKE" -> Optional.empty();
            case "SP" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.RED7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.RED7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.GOD,GodReelStrip.VisibleRow.MIDDLE),
                    "published middle RED7/RED7/GOD"));
            case "RED7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.RED7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.RED7,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.RED7,GodReelStrip.VisibleRow.MIDDLE),
                    "published red-7 straight"));
            case "GOD" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.GOD,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.GOD,GodReelStrip.VisibleRow.MIDDLE),
                    req(GodReelStrip.Symbol.GOD,GodReelStrip.VisibleRow.MIDDLE),
                    "published GOD straight"));
            default -> Optional.empty();
        };
    }

    /**
     * Context-free controller for fixed-form roles.
     * MISS must use the contextual overload because its safe target depends on
     * the reels already stopped in the current game.
     */
    public static int targetFor(String role,int reel,int pressed){
        if(role!=null&&("MISS".equalsIgnoreCase(role)||"RED7_FAKE".equalsIgnoreCase(role)))
            throw new IllegalArgumentException(role+" requires contextual stop state");
        if(pressed<0||pressed>=GodReelStrip.STOPS)throw new IllegalArgumentException("pressed");
        var rule=publishedRule(role);
        if(rule.isEmpty()){
            var desired=GodReelStrip.symbolForRole(role,reel);
            return GodReelStrip.targetFor(reel,desired,pressed);
        }
        return targetForRequirement(role,reel,pressed,rule.get().requirement(reel));
    }

    /**
     * Stateful controller used by Paper/recovery. For MISS, it searches the complete
     * safe-stop catalogue and picks the nearest <=4-frame target that guarantees a
     * safe completion regardless of the later legal stop order/press positions.
     */
    public static int targetFor(String role,int reel,int pressed,int stoppedMask,int left,int center,int right){
        return targetFor(role,reel,pressed,stoppedMask,left,center,right,0L);
    }

    /**
     * Selector-aware MISS control. A per-spin selector lets identical button timing
     * resolve to different safe <=4-frame MISS targets instead of collapsing repeated
     * plays onto one deterministic window. The selector never changes role/payout.
     */
    public static int targetFor(String role,int reel,int pressed,int stoppedMask,int left,int center,int right,long selector){
        if(role==null||(!"MISS".equalsIgnoreCase(role)&&!"RED7_FAKE".equalsIgnoreCase(role)))
            return targetFor(role,reel,pressed);
        if(reel<0||reel>2)throw new IllegalArgumentException("reel");
        if(pressed<0||pressed>=GodReelStrip.STOPS)throw new IllegalArgumentException("pressed");
        if((stoppedMask&(1<<reel))!=0)throw new IllegalArgumentException("reel already stopped");

        int[] stops={Math.floorMod(left,GodReelStrip.STOPS),Math.floorMod(center,GodReelStrip.STOPS),Math.floorMod(right,GodReelStrip.STOPS)};
        var legal=new ArrayList<Integer>(5);
        for(int slip=0;slip<=4;slip++){
            int target=Math.floorMod(pressed-slip,GodReelStrip.STOPS);
            stops[reel]=target;
            int nextMask=stoppedMask|(1<<reel);
            boolean viable="MISS".equalsIgnoreCase(role)
                    ? canAlwaysCompleteMiss(nextMask,stops[0],stops[1],stops[2])
                    : canAlwaysCompleteFakeRed(nextMask,stops[0],stops[1],stops[2]);
            if(viable)legal.add(target);
        }
        if(legal.isEmpty())
            throw new IllegalStateException("No safe "+role+" stop target reel="+reel+" pressed="+pressed+" mask="+stoppedMask);

        if("RED7_FAKE".equalsIgnoreCase(role)&&(reel==0||reel==1)){
            var redMiddle=new ArrayList<Integer>();
            for(int target:legal)
                if(GodReelStrip.visibleSymbol(reel,target,GodReelStrip.VisibleRow.MIDDLE)==GodReelStrip.Symbol.RED7)
                    redMiddle.add(target);
            if(!redMiddle.isEmpty())legal=redMiddle;
        }

        long mixed=selector;
        mixed^=(long)(reel+1)*0x9E3779B97F4A7C15L;
        mixed^=(long)(pressed+1)*0xBF58476D1CE4E5B9L;
        mixed^=(long)(stoppedMask+1)*0x94D049BB133111EBL;
        mixed^=(long)(Math.floorMod(left,GodReelStrip.STOPS)+1)<<7;
        mixed^=(long)(Math.floorMod(center,GodReelStrip.STOPS)+1)<<17;
        mixed^=(long)(Math.floorMod(right,GodReelStrip.STOPS)+1)<<27;
        mixed^=mixed>>>30;mixed*=0xBF58476D1CE4E5B9L;
        mixed^=mixed>>>27;mixed*=0x94D049BB133111EBL;
        mixed^=mixed>>>31;
        return legal.get(Math.floorMod((int)(mixed^(mixed>>>32)),legal.size()));
    }

    private static int targetForRequirement(String role,int reel,int pressed,Requirement requirement){
        boolean premiumLongSlip=isPremiumLongSlipRole(role);
        int best=-1;
        int bestSlip=Integer.MAX_VALUE;
        for(int middle=0;middle<GodReelStrip.STOPS;middle++){
            if(GodReelStrip.visibleSymbol(reel,middle,requirement.row())!=requirement.symbol())continue;
            int slip=GodReelStrip.slip(pressed,middle);
            if(!premiumLongSlip&&slip>4)continue;
            if(slip<bestSlip){
                best=middle;
                bestSlip=slip;
            }
        }
        if(best>=0)return best;
        throw new IllegalStateException("No legal GOD stop target role="+role+" reel="+reel+" pressed="+pressed);
    }

    public static boolean isPremiumLongSlipRole(String role){
        if(role==null)return false;
        return switch(role.toUpperCase(Locale.ROOT)){
            case "GOD","RED7","SP" -> true;
            default -> false;
        };
    }

    public static boolean matchesPublishedForm(String role,int left,int center,int right){
        var rule=publishedRule(role);
        if(rule.isEmpty())return false;
        int[] middles={left,center,right};
        for(int reel=0;reel<3;reel++){
            Requirement requirement=rule.get().requirement(reel);
            if(GodReelStrip.visibleSymbol(reel,middles[reel],requirement.row())!=requirement.symbol())return false;
        }
        return true;
    }

    /** True only when the complete three-reel window is safe to present as MISS. */
    public static boolean isSafeMiss(int left,int center,int right){
        if(left<0||left>=GodReelStrip.STOPS||center<0||center>=GodReelStrip.STOPS||right<0||right>=GodReelStrip.STOPS)
            throw new IllegalArgumentException("stop");

        for(String role:NON_MISS_FORMS)
            if(matchesPublishedForm(role,left,center,right))return false;

        int[] stops={left,center,right};
        for(var line:PAYLINES){
            var a=GodReelStrip.visibleSymbol(0,stops[0],line[0]);
            var b=GodReelStrip.visibleSymbol(1,stops[1],line[1]);
            var c=GodReelStrip.visibleSymbol(2,stops[2],line[2]);
            if(a==b&&b==c&&(a==GodReelStrip.Symbol.GOD||a==GodReelStrip.Symbol.RED7||
                    a==GodReelStrip.Symbol.BLUE7||a==GodReelStrip.Symbol.YELLOW7))
                return false;
        }
        return true;
    }

    /** Current-machine representative fake-RED form: middle RED7 / RED7 / miss. */
    public static boolean matchesFakeRedRepresentative(int left,int center,int right){
        return GodReelStrip.visibleSymbol(0,left,GodReelStrip.VisibleRow.MIDDLE)==GodReelStrip.Symbol.RED7
                &&GodReelStrip.visibleSymbol(1,center,GodReelStrip.VisibleRow.MIDDLE)==GodReelStrip.Symbol.RED7
                &&GodReelStrip.visibleSymbol(2,right,GodReelStrip.VisibleRow.MIDDLE)!=GodReelStrip.Symbol.RED7
                &&GodReelStrip.visibleSymbol(2,right,GodReelStrip.VisibleRow.MIDDLE)!=GodReelStrip.Symbol.GOD
                &&isSafeFakeRed(left,center,right);
    }

    /** Safe variable fake-RED replay fallback used where the representative form is unreachable in 0..4 frames. */
    public static boolean isSafeFakeRed(int left,int center,int right){
        if(left<0||left>=GodReelStrip.STOPS||center<0||center>=GodReelStrip.STOPS||right<0||right>=GodReelStrip.STOPS)
            throw new IllegalArgumentException("stop");
        int[] stops={left,center,right};

        for(String role:NON_MISS_FORMS){
            if("RED7_FAKE".equals(role))continue;
            if(matchesPublishedForm(role,left,center,right))return false;
        }

        boolean redVisible=false;
        for(int reel=0;reel<3;reel++)
            for(GodReelStrip.VisibleRow row:GodReelStrip.VisibleRow.values())
                if(GodReelStrip.visibleSymbol(reel,stops[reel],row)==GodReelStrip.Symbol.RED7)redVisible=true;
        if(!redVisible)return false;

        for(var line:PAYLINES){
            var a=GodReelStrip.visibleSymbol(0,stops[0],line[0]);
            var b=GodReelStrip.visibleSymbol(1,stops[1],line[1]);
            var d=GodReelStrip.visibleSymbol(2,stops[2],line[2]);
            if(a==b&&b==d&&(a==GodReelStrip.Symbol.GOD||a==GodReelStrip.Symbol.RED7||
                    a==GodReelStrip.Symbol.BLUE7||a==GodReelStrip.Symbol.YELLOW7))
                return false;
        }
        return true;
    }

    private static boolean canAlwaysCompleteFakeRed(int mask,int left,int center,int right){
        int key=missStateKey(mask,left,center,right);
        synchronized(FAKE_RED_VIABILITY){
            Boolean cached=FAKE_RED_VIABILITY.get(key);
            if(cached!=null)return cached;
            boolean result=computeCanAlwaysCompleteFakeRed(mask,left,center,right);
            FAKE_RED_VIABILITY.put(key,result);
            return result;
        }
    }

    private static boolean computeCanAlwaysCompleteFakeRed(int mask,int left,int center,int right){
        if(mask==7)return isSafeFakeRed(left,center,right);
        int[] base={left,center,right};
        for(int reel=0;reel<3;reel++){
            int bit=1<<reel;
            if((mask&bit)!=0)continue;
            for(int press=0;press<GodReelStrip.STOPS;press++){
                boolean found=false;
                for(int slip=0;slip<=4;slip++){
                    int[] next=base.clone();
                    next[reel]=Math.floorMod(press-slip,GodReelStrip.STOPS);
                    if(canAlwaysCompleteFakeRed(mask|bit,next[0],next[1],next[2])){
                        found=true;
                        break;
                    }
                }
                if(!found)return false;
            }
        }
        return true;
    }

    /** All 20^3 physical stop combinations classified as legal Piri MISS windows. */
    public static List<MissStop> missCandidates(){return MISS_CANDIDATES;}

    private static List<MissStop> buildMissCandidates(){
        var out=new ArrayList<MissStop>();
        for(int l=0;l<GodReelStrip.STOPS;l++)
            for(int c=0;c<GodReelStrip.STOPS;c++)
                for(int r=0;r<GodReelStrip.STOPS;r++)
                    if(isSafeMiss(l,c,r))out.add(new MissStop(l,c,r));
        if(out.isEmpty())throw new IllegalStateException("No safe MISS stop combinations");
        return List.copyOf(out);
    }

    private static boolean canAlwaysCompleteMiss(int mask,int left,int center,int right){
        int key=missStateKey(mask,left,center,right);
        synchronized(MISS_VIABILITY){
            Boolean cached=MISS_VIABILITY.get(key);
            if(cached!=null)return cached;
            boolean result=computeCanAlwaysCompleteMiss(mask,left,center,right);
            MISS_VIABILITY.put(key,result);
            return result;
        }
    }

    private static boolean computeCanAlwaysCompleteMiss(int mask,int left,int center,int right){
        if(mask==7)return isSafeMiss(left,center,right);

        int[] base={left,center,right};
        for(int reel=0;reel<3;reel++){
            int bit=1<<reel;
            if((mask&bit)!=0)continue;
            for(int press=0;press<GodReelStrip.STOPS;press++){
                boolean found=false;
                for(int slip=0;slip<=4;slip++){
                    int[] next=base.clone();
                    next[reel]=Math.floorMod(press-slip,GodReelStrip.STOPS);
                    if(canAlwaysCompleteMiss(mask|bit,next[0],next[1],next[2])){
                        found=true;
                        break;
                    }
                }
                if(!found)return false;
            }
        }
        return true;
    }

    private static int missStateKey(int mask,int left,int center,int right){
        int key=mask&7;
        if((mask&1)!=0)key|=(Math.floorMod(left,GodReelStrip.STOPS)<<3);
        if((mask&2)!=0)key|=(Math.floorMod(center,GodReelStrip.STOPS)<<8);
        if((mask&4)!=0)key|=(Math.floorMod(right,GodReelStrip.STOPS)<<13);
        return key;
    }

    private GodStopControl(){}
}
