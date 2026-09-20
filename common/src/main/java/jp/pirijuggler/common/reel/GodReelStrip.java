package jp.pirijuggler.common.reel;

import java.util.Locale;

/** Shared visual strip model for the Piri GOD three-reel presentation. */
public final class GodReelStrip {
    public enum Symbol { GOD, RED7, BLUE7, YELLOW7, BELL, BLANK }

    private static final Symbol[][] STRIPS={
            strip(0),strip(2),strip(4)
    };

    private static Symbol[] strip(int shift){
        Symbol[] base={Symbol.GOD,Symbol.RED7,Symbol.BLUE7,Symbol.YELLOW7,Symbol.BELL,Symbol.BLANK};
        Symbol[] result=new Symbol[21];
        for(int i=0;i<result.length;i++)result[i]=base[Math.floorMod(i+shift,base.length)];
        return result;
    }

    public static Symbol symbol(int reel,int index){
        if(reel<0||reel>2)throw new IllegalArgumentException("reel");
        return STRIPS[reel][Math.floorMod(index,21)];
    }

    public static Symbol symbolForRole(String role){
        if(role==null)return Symbol.BLANK;
        return switch(role.toUpperCase(Locale.ROOT)){
            case "GOD" -> Symbol.GOD;
            case "RED7" -> Symbol.RED7;
            case "UPPER_BLUE7","MIDDLE_BLUE7" -> Symbol.BLUE7;
            case "ORDERED_YELLOW7","LOWER_YELLOW7","RISING_YELLOW7","MIDDLE_YELLOW7","COMMON_YELLOW7" -> Symbol.YELLOW7;
            case "GAIA_BELL" -> Symbol.BELL;
            default -> Symbol.BLANK;
        };
    }

    /** Normal-direction stop, choosing the nearest requested display symbol. */
    public static int targetFor(int reel,Symbol desired,int pressed){
        if(pressed<0||pressed>=21)throw new IllegalArgumentException("pressed");
        for(int slip=0;slip<21;slip++){
            int target=Math.floorMod(pressed-slip,21);
            if(symbol(reel,target)==desired)return target;
        }
        return pressed;
    }

    public static int slip(int pressed,int target){return Math.floorMod(pressed-target,21);}
    private GodReelStrip(){}
}
