package jp.pirijuggler.common.reel;

import java.util.Locale;

/**
 * Exact 20-stop visual reel strips for SmaSlo Million God: Kamigami no Kiseki.
 *
 * Index 0 corresponds to published stop position 1, index 19 to position 20.
 * The large "deka-million" artwork occupies two adjacent stop positions where
 * shown by the published reel chart.
 */
public final class GodReelStrip {
    public static final int STOPS=20;

    public enum Symbol {
        GOD, RED7, BLUE7, YELLOW7, MILLION, DEKA_MILLION_BOTTOM, DEKA_MILLION_TOP
    }

    /**
     * Shared visible-window convention used by both Paper stop control and Fabric.
     * Published chart positions increase upward: TOP = middle + 1, BOTTOM = middle - 1.
     */
    public enum VisibleRow {
        TOP(1), MIDDLE(0), BOTTOM(-1);
        private final int offset;
        VisibleRow(int offset){this.offset=offset;}
        public int offset(){return offset;}
    }

    // Published positions 1 -> 20.
    private static final Symbol[] LEFT={
            Symbol.GOD,Symbol.BLUE7,Symbol.RED7,Symbol.MILLION,Symbol.YELLOW7,
            Symbol.GOD,Symbol.BLUE7,Symbol.RED7,Symbol.MILLION,Symbol.YELLOW7,
            Symbol.GOD,Symbol.BLUE7,Symbol.DEKA_MILLION_BOTTOM,Symbol.DEKA_MILLION_TOP,Symbol.YELLOW7,
            Symbol.GOD,Symbol.BLUE7,Symbol.RED7,Symbol.MILLION,Symbol.YELLOW7
    };
    private static final Symbol[] CENTER={
            Symbol.GOD,Symbol.YELLOW7,Symbol.BLUE7,Symbol.YELLOW7,Symbol.RED7,
            Symbol.GOD,Symbol.YELLOW7,Symbol.BLUE7,Symbol.YELLOW7,Symbol.RED7,
            Symbol.GOD,Symbol.YELLOW7,Symbol.BLUE7,Symbol.YELLOW7,Symbol.RED7,
            Symbol.GOD,Symbol.YELLOW7,Symbol.BLUE7,Symbol.YELLOW7,Symbol.RED7
    };
    private static final Symbol[] RIGHT={
            Symbol.GOD,Symbol.YELLOW7,Symbol.DEKA_MILLION_BOTTOM,Symbol.DEKA_MILLION_TOP,Symbol.BLUE7,
            Symbol.GOD,Symbol.YELLOW7,Symbol.RED7,Symbol.MILLION,Symbol.BLUE7,
            Symbol.GOD,Symbol.YELLOW7,Symbol.RED7,Symbol.MILLION,Symbol.BLUE7,
            Symbol.GOD,Symbol.YELLOW7,Symbol.RED7,Symbol.MILLION,Symbol.BLUE7
    };
    private static final Symbol[][] STRIPS={LEFT,CENTER,RIGHT};

    public static Symbol symbol(int reel,int index){
        if(reel<0||reel>2)throw new IllegalArgumentException("reel");
        return STRIPS[reel][Math.floorMod(index,STOPS)];
    }

    public static int visibleIndex(int middleStop,VisibleRow row){
        if(middleStop<0||middleStop>=STOPS)throw new IllegalArgumentException("middleStop");
        if(row==null)throw new IllegalArgumentException("row");
        return Math.floorMod(middleStop+row.offset(),STOPS);
    }

    public static Symbol visibleSymbol(int reel,int middleStop,VisibleRow row){
        return symbol(reel,visibleIndex(middleStop,row));
    }

    /**
     * Presentation target for a role when a full stop-control table is not required.
     * MISS/GAIA/SP/fake roles use a legal nearby symbol instead of inventing a symbol
     * that does not exist on the actual strip.
     */
    public static Symbol symbolForRole(String role,int reel){
        if(role==null)return fallback(reel);
        return switch(role.toUpperCase(Locale.ROOT)){
            case "GOD" -> Symbol.GOD;
            case "RED7" -> Symbol.RED7;
            case "UPPER_BLUE7","MIDDLE_BLUE7" -> Symbol.BLUE7;
            case "ORDERED_YELLOW7","LOWER_YELLOW7","RISING_YELLOW7","MIDDLE_YELLOW7","COMMON_YELLOW7" -> Symbol.YELLOW7;
            case "GAIA_BELL" -> reel==0?Symbol.MILLION:reel==1?Symbol.YELLOW7:Symbol.GOD;
            case "SP" -> reel<2?Symbol.RED7:Symbol.GOD;
            case "RED7_FAKE" -> reel==0?Symbol.RED7:fallback(reel);
            default -> fallback(reel);
        };
    }

    private static Symbol fallback(int reel){return reel==1?Symbol.YELLOW7:Symbol.BLUE7;}

    /** Normal-direction stop within the physical four-frame slip window. */
    public static int targetFor(int reel,Symbol desired,int pressed){
        if(pressed<0||pressed>=STOPS)throw new IllegalArgumentException("pressed");
        for(int slip=0;slip<=4;slip++){
            int target=Math.floorMod(pressed-slip,STOPS);
            if(symbol(reel,target)==desired)return target;
        }
        // Do not invent an impossible long slip just to show a representative
        // premium stop form. If the desired symbol is outside the four-frame
        // window, keep the pressed stop; the internal role/benefit still applies.
        return pressed;
    }

    public static int slip(int pressed,int target){return Math.floorMod(pressed-target,STOPS);}
    public static double wrap(double value){return (value%STOPS+STOPS)%STOPS;}
    public static double normalStopEndpoint(double current,int target){
        if(!Double.isFinite(current)||target<0||target>=STOPS)throw new IllegalArgumentException("stop endpoint");
        double endpoint=target;while(endpoint>current)endpoint-=STOPS;return endpoint;
    }
    private GodReelStrip(){}
}
