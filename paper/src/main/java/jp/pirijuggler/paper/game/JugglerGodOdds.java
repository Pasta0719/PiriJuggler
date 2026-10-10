package jp.pirijuggler.paper.game;

/**
 * Independently tuned bonus-stock odds and normal-mode HOT multipliers.
 * The HOT multipliers scale with normal-base odds, but never weaken the
 * uninterrupted 20G/15G base-bonus windows below 38%/68% respectively.
 */
public final class JugglerGodOdds {
    private static final int[] GOD_REFERENCE_BASE = {0,297986,296030,298549,303386,306204,289197};
    private static final int[] EXTREME_REFERENCE_BASE = {0,224070,221440,225491,230039,235721,223878};
    private static final int[] GOD_STOCK = {0,743613,734884,734653,739194,741839,696323};
    private static final int[] EXTREME_STOCK = {0,564190,554200,558800,562600,571106,537600};
    private static final double HIGH_FLOOR_CHANCE = 1.0-Math.pow(0.62,1.0/20.0);
    private static final double ULTRA_FLOOR_CHANCE = 1.0-Math.pow(0.32,1.0/15.0);
    private static final double HIGH_REFERENCE_CHANCE = 1.0-Math.pow(0.60,1.0/20.0);
    private static final double ULTRA_REFERENCE_CHANCE = 1.0-Math.pow(0.30,1.0/15.0);
    private JugglerGodOdds() {}

    public static int referenceBase(String profile,int setting) {
        check(setting);
        return ("juggler_god_extreme".equals(profile)?EXTREME_REFERENCE_BASE:GOD_REFERENCE_BASE)[setting];
    }
    public static int defaultStockScale(String profile,int setting) {
        check(setting);
        return ("juggler_god_extreme".equals(profile)?EXTREME_STOCK:GOD_STOCK)[setting];
    }
    /**
     * Spend the requested total base-bonus reduction solely on standalone
     * BIG/REG while preserving original cherry/piero-overlap bonus rates.
     * The nominal bonus_scale_ppm is a total-family EV budget, not a second roll.
     */
    public static int normalStandaloneScale(int familyBudgetScale,int originalOverlapScale,
            long rawStandalone,long rawFamily) {
        if(familyBudgetScale<0||familyBudgetScale>1_000_000
                ||originalOverlapScale<0||originalOverlapScale>1_000_000
                ||rawStandalone<=0||rawFamily<rawStandalone)
            throw new IllegalArgumentException("GOD standalone bonus odds");
        double overlapWeight=rawFamily-rawStandalone;
        double standalone=(familyBudgetScale*(double)rawFamily
                -originalOverlapScale*overlapWeight)/rawStandalone;
        return (int)Math.max(0,Math.min(1_000_000,Math.round(standalone)));
    }

    /** Exact scaled BIG/REG-family weighting, sharing the production drawing scale. */
    public static int hotScale(int normalBaseScale,int referenceBase,long rawBonusWeight,boolean ultra) {
        if(normalBaseScale<0||normalBaseScale>1_000_000||referenceBase<=0||rawBonusWeight<=0)
            throw new IllegalArgumentException("GOD HOT odds");
        double referenceChance=ultra?ULTRA_REFERENCE_CHANCE:HIGH_REFERENCE_CHANCE;
        double floorChance=ultra?ULTRA_FLOOR_CHANCE:HIGH_FLOOR_CHANCE;
        double chance=Math.max(floorChance,referenceChance*normalBaseScale/referenceBase);
        return Math.toIntExact(Math.round(chance*1_000_000_000_000_000.0/rawBonusWeight));
    }
    private static void check(int setting) {
        if(setting<1||setting>6)throw new IllegalArgumentException("Setting");
    }
}
