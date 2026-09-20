package jp.pirijuggler.paper.game.god;

import java.util.random.RandomGenerator;

/** Published Gaia-mode / Gaia-stage tables used by the Piri GOD engine. */
public final class GodGaiaRules {
    private static final double[][] TARGET = {
            {0,.023,.004,.102,.004,.102,.004,.148,.008,.152,.004,.191,.004,.191,.004,.059},
            {0,.098,.008,.250,.035,.250,.035,.137,.020,.078,.020,.043,.004,.012,.004,.008},
            {0,.332,.332,.336,0,0,0,0,0,0,0,0,0,0,0,0}
    };

    private GodGaiaRules(){}

    public static int chooseTarget(GodGaiaMode mode, RandomGenerator rng){
        double x=rng.nextDouble(),c=0;double[] row=TARGET[mode.ordinal()];
        for(int i=1;i<=15;i++){c+=row[i];if(x<c)return i;}
        return mode==GodGaiaMode.HEAVEN?3:15;
    }

    public static double stageEntryRate(GodGaiaMode mode){
        return switch(mode){case LOW->.102;case NORMAL->.301;case HEAVEN->.668;};
    }

    public static GodGaiaMode nextMode(GodGaiaMode mode,RandomGenerator rng){
        double x=rng.nextDouble();
        return switch(mode){
            case LOW -> x<.801?GodGaiaMode.LOW:x<.996?GodGaiaMode.NORMAL:GodGaiaMode.HEAVEN;
            case NORMAL -> x<.500?GodGaiaMode.LOW:x<.949?GodGaiaMode.NORMAL:GodGaiaMode.HEAVEN;
            case HEAVEN -> x<.125?GodGaiaMode.LOW:x<.250?GodGaiaMode.NORMAL:GodGaiaMode.HEAVEN;
        };
    }

    public static double ggChance(GodRole role){
        return switch(role){
            case MIDDLE_BLUE7 -> .50;
            case RISING_YELLOW7 -> .625;
            case MIDDLE_YELLOW7, SP, RED7, GOD -> 1.0;
            case MISS, UPPER_BLUE7, RED7_FAKE, ORDERED_YELLOW7, GAIA_BELL -> .016;
            default -> 0.0;
        };
    }

    public static boolean historyHit(int blue,int yellow,RandomGenerator rng){
        double p=0;
        if(blue>=5||yellow>=5)return true;
        if(blue>=3)p=blue==3?.25:.75; else if(blue>=1)p=.059;
        if(yellow>=3)p=Math.max(p,yellow==3?.102:.50); else if(yellow>=1)p=Math.max(p,.012);
        return p>0&&rng.nextDouble()<p;
    }

    public static int maybeExtendGuarantee(int remaining,GodRole role,RandomGenerator rng){
        if(remaining>3)return remaining;
        double p=switch(role){
            case MIDDLE_BLUE7,RISING_YELLOW7->1.0;
            case MISS,UPPER_BLUE7,RED7_FAKE,ORDERED_YELLOW7,GAIA_BELL->.053;
            default->0.0;
        };
        return p>0&&rng.nextDouble()<p?remaining+3:remaining;
    }

    public static boolean exitsAfterGuarantee(GodRole role,RandomGenerator rng){
        return (role==GodRole.MISS||role==GodRole.ORDERED_YELLOW7)&&rng.nextDouble()<.129;
    }
}
