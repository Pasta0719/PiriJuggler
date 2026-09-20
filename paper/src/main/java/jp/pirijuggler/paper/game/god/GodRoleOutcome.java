package jp.pirijuggler.paper.game.god;

import java.util.Objects;

/**
 * Single settlement/presentation result for one internal GOD role.
 *
 * This prevents the reel result, PAY display and credited medals from being
 * calculated by unrelated code paths.
 */
public final class GodRoleOutcome {
    public record Outcome(String displayRole,int payout,boolean replay) {
        public Outcome {
            Objects.requireNonNull(displayRole);
            if(payout<0)throw new IllegalArgumentException("payout");
            if(replay&&payout!=0)throw new IllegalArgumentException("replay is not a medal payout");
        }
    }

    /**
     * Piri calibration for an un-navigated ORDERED_YELLOW7 in NORMAL/G-ZONE.
     *
     * Public sources identify the flag as a 15-medal push-order yellow and also
     * state that one-medal roles can occur when that flag is missed, but do not
     * publish the complete order/miss distribution. The rate below keeps the
     * published setting-1 normal base (about 30.8G/50 medals) when combined with
     * the published role odds and payouts. It is not presented as a manufacturer
     * control-table value.
     */
    public static final double NORMAL_ORDERED_15_SUCCESS_RATE = 0.010897;

    public static Outcome resolve(GodRole role,GodPhase phase,double unit){
        Objects.requireNonNull(role);
        Objects.requireNonNull(phase);
        if(!(unit>=0.0&&unit<1.0))throw new IllegalArgumentException("unit");

        boolean navigated=phase==GodPhase.GG||phase==GodPhase.SGG||
                phase==GodPhase.SGG_COMEBACK||phase==GodPhase.Z_ZONE||phase==GodPhase.Z_GAME;

        return switch(role){
            case MISS -> new Outcome("MISS",0,false);
            case UPPER_BLUE7 -> new Outcome("UPPER_BLUE7",0,true);
            case MIDDLE_BLUE7 -> new Outcome("MIDDLE_BLUE7",0,true);
            case RED7_FAKE -> new Outcome("RED7_FAKE",0,true);

            case LOWER_YELLOW7 -> new Outcome("LOWER_YELLOW7",3,false);
            case RISING_YELLOW7 -> new Outcome("RISING_YELLOW7",15,false);
            case MIDDLE_YELLOW7 -> new Outcome("MIDDLE_YELLOW7",15,false);
            case COMMON_YELLOW7 -> new Outcome("COMMON_YELLOW7",15,false);
            case GAIA_BELL -> new Outcome("GAIA_BELL",1,false);
            case SP -> new Outcome("SP",15,false);
            case RED7 -> new Outcome("RED7",15,false);
            case GOD -> new Outcome("GOD",15,false);

            case ORDERED_YELLOW7 -> {
                boolean fullPay=navigated||unit<NORMAL_ORDERED_15_SUCCESS_RATE;
                yield fullPay
                        ? new Outcome("ORDERED_YELLOW7",15,false)
                        : new Outcome("MISS",1,false);
            }
        };
    }

    /**
     * Compatibility helper for legacy transition code. New settlement uses
     * resolve(...) once in beginSpin; this method is intentionally deterministic.
     */
    public static Outcome forRole(GodRole role,GodPhase phase){
        return resolve(role,phase,0.5);
    }

    private GodRoleOutcome(){}
}
