package jp.pirijuggler.common.reel;

import java.util.Locale;
import java.util.Optional;

/**
 * Published stop-form controller for SmaSlo Million God: Kamigami no Kiseki.
 *
 * The public analysis pages publish representative stop forms, not the complete
 * press-position-by-press-position control table. This class therefore encodes
 * only those published forms. Roles whose exact visual distinction is not
 * published here deliberately fall back to the legacy physical-strip target in
 * GodReelStrip instead of inventing an "exact" machine rule.
 */
public final class GodStopControl {
    public enum Row {
        TOP(-1), MIDDLE(0), BOTTOM(1);
        private final int offset;
        Row(int offset){this.offset=offset;}
        int offset(){return offset;}
    }

    public record Requirement(GodReelStrip.Symbol symbol,Row row) {}
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

    private static Requirement req(GodReelStrip.Symbol symbol,Row row){
        return new Requirement(symbol,row);
    }

    /**
     * Representative published stop forms.
     *
     * LOWER_YELLOW7 is the published 3-medal lower-line form. COMMON_YELLOW7
     * and a successfully navigated ORDERED_YELLOW7 use the published lower-line
     * 15-medal representative form. Public screenshots distinguish lower-line
     * variants more finely than this symbol enum can encode, so that distinction
     * is intentionally not fabricated here.
     */
    public static Optional<Rule> publishedRule(String role){
        if(role==null)return Optional.empty();
        return switch(role.toUpperCase(Locale.ROOT)){
            case "UPPER_BLUE7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.BLUE7,Row.TOP),
                    req(GodReelStrip.Symbol.BLUE7,Row.TOP),
                    req(GodReelStrip.Symbol.BLUE7,Row.TOP),
                    "published upper-line replay"));
            case "MIDDLE_BLUE7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.BLUE7,Row.MIDDLE),
                    req(GodReelStrip.Symbol.BLUE7,Row.MIDDLE),
                    req(GodReelStrip.Symbol.BLUE7,Row.MIDDLE),
                    "published middle-line replay"));
            case "LOWER_YELLOW7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.YELLOW7,Row.BOTTOM),
                    req(GodReelStrip.Symbol.YELLOW7,Row.BOTTOM),
                    req(GodReelStrip.Symbol.YELLOW7,Row.BOTTOM),
                    "published lower-line yellow 7 B (3 medals)"));
            case "COMMON_YELLOW7","ORDERED_YELLOW7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.YELLOW7,Row.BOTTOM),
                    req(GodReelStrip.Symbol.YELLOW7,Row.BOTTOM),
                    req(GodReelStrip.Symbol.YELLOW7,Row.BOTTOM),
                    "published lower-line 15-medal yellow 7 representative form"));
            case "RISING_YELLOW7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.YELLOW7,Row.BOTTOM),
                    req(GodReelStrip.Symbol.YELLOW7,Row.MIDDLE),
                    req(GodReelStrip.Symbol.YELLOW7,Row.TOP),
                    "published rising yellow 7"));
            case "MIDDLE_YELLOW7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.YELLOW7,Row.MIDDLE),
                    req(GodReelStrip.Symbol.YELLOW7,Row.MIDDLE),
                    req(GodReelStrip.Symbol.YELLOW7,Row.MIDDLE),
                    "published middle-line yellow 7"));
            case "GAIA_BELL" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.YELLOW7,Row.TOP),
                    req(GodReelStrip.Symbol.YELLOW7,Row.BOTTOM),
                    req(GodReelStrip.Symbol.YELLOW7,Row.TOP),
                    "published small-V yellow 7"));
            case "SP" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.RED7,Row.MIDDLE),
                    req(GodReelStrip.Symbol.RED7,Row.MIDDLE),
                    req(GodReelStrip.Symbol.GOD,Row.MIDDLE),
                    "published middle RED7/RED7/GOD"));
            case "RED7" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.RED7,Row.MIDDLE),
                    req(GodReelStrip.Symbol.RED7,Row.MIDDLE),
                    req(GodReelStrip.Symbol.RED7,Row.MIDDLE),
                    "published red-7 straight"));
            case "GOD" -> Optional.of(new Rule(
                    req(GodReelStrip.Symbol.GOD,Row.MIDDLE),
                    req(GodReelStrip.Symbol.GOD,Row.MIDDLE),
                    req(GodReelStrip.Symbol.GOD,Row.MIDDLE),
                    "published GOD straight"));
            default -> Optional.empty();
        };
    }

    public static int targetFor(String role,int reel,int pressed){
        var rule=publishedRule(role);
        if(rule.isEmpty()){
            var desired=GodReelStrip.symbolForRole(role,reel);
            return GodReelStrip.targetFor(reel,desired,pressed);
        }
        Requirement requirement=rule.get().requirement(reel);
        int best=-1;
        int bestSlip=Integer.MAX_VALUE;
        for(int middle=0;middle<GodReelStrip.STOPS;middle++){
            int symbolIndex=Math.floorMod(middle+requirement.row().offset(),GodReelStrip.STOPS);
            if(GodReelStrip.symbol(reel,symbolIndex)!=requirement.symbol())continue;
            int slip=GodReelStrip.slip(pressed,middle);
            if(slip>4)continue;
            if(slip<bestSlip){
                best=middle;
                bestSlip=slip;
            }
        }
        // Red7/SP representative forms are not guaranteed from every press
        // position. Do not exceed the physical slip window just to force them.
        return best<0?pressed:best;
    }

    public static boolean matchesPublishedForm(String role,int left,int center,int right){
        var rule=publishedRule(role);
        if(rule.isEmpty())return false;
        int[] middles={left,center,right};
        for(int reel=0;reel<3;reel++){
            Requirement requirement=rule.get().requirement(reel);
            int symbolIndex=Math.floorMod(middles[reel]+requirement.row().offset(),GodReelStrip.STOPS);
            if(GodReelStrip.symbol(reel,symbolIndex)!=requirement.symbol())return false;
        }
        return true;
    }

    private GodStopControl(){}
}
