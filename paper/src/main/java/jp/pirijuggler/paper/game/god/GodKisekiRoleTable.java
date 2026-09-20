package jp.pirijuggler.paper.game.god;

import java.util.EnumMap;
import java.util.Map;

/**
 * Published setting-common role odds from Kamigami no Kiseki.
 *
 * The reference table is preserved exactly for research. Piri deliberately
 * overrides only GOD from 1/16384 to 1/8192 at the production-rule level.
 */
public final class GodKisekiRoleTable {
    private static final Map<GodRole, Double> REFERENCE_DENOMINATOR=new EnumMap<>(GodRole.class);

    static {
        REFERENCE_DENOMINATOR.put(GodRole.MISS,5.2);
        REFERENCE_DENOMINATOR.put(GodRole.UPPER_BLUE7,7.9);
        REFERENCE_DENOMINATOR.put(GodRole.MIDDLE_BLUE7,109.2);
        REFERENCE_DENOMINATOR.put(GodRole.ORDERED_YELLOW7,1.7);
        REFERENCE_DENOMINATOR.put(GodRole.LOWER_YELLOW7,18.4);
        REFERENCE_DENOMINATOR.put(GodRole.RISING_YELLOW7,186.2);
        REFERENCE_DENOMINATOR.put(GodRole.MIDDLE_YELLOW7,963.8);
        REFERENCE_DENOMINATOR.put(GodRole.COMMON_YELLOW7,1524.1);
        REFERENCE_DENOMINATOR.put(GodRole.GAIA_BELL,37.6);
        REFERENCE_DENOMINATOR.put(GodRole.RED7_FAKE,936.2);
        REFERENCE_DENOMINATOR.put(GodRole.RED7,6900.0);
        REFERENCE_DENOMINATOR.put(GodRole.GOD,16384.0);
        REFERENCE_DENOMINATOR.put(GodRole.SP,65536.0);
    }

    private GodKisekiRoleTable() {}

    public static double referenceDenominator(GodRole role) {
        Double d=REFERENCE_DENOMINATOR.get(role);
        if(d==null) throw new IllegalArgumentException("No published denominator for "+role);
        return d;
    }

    public static double referenceProbability(GodRole role) {
        return 1.0/referenceDenominator(role);
    }

    public static double piriDenominator(GodRole role) {
        return role==GodRole.GOD ? GodEconomyTargets.GOD_DENOMINATOR : referenceDenominator(role);
    }

    public static double piriProbability(GodRole role) {
        return 1.0/piriDenominator(role);
    }
}
