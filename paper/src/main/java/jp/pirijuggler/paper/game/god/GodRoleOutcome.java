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
     * Provisional Piri calibration for an un-navigated ORDERED_YELLOW7 in normal flow.
     *
     * Phase 01 locked the normal-play result to the miss side (0 or 1 medal), never
     * the navigated 15-medal acquisition. Public material does not publish the exact
     * 0/1 split, so Phase 04 owns the final calibration. Until then we preserve the
     * previous calibration mass as a labelled provisional one-medal probability.
     */
    public static final double NORMAL_ORDERED_ONE_MEDAL_RATE = 1.0 - 0.0104273;

    public static Outcome resolve(GodRole role,GodPhase phase,double unit){
        Objects.requireNonNull(role);
        Objects.requireNonNull(phase);
        if(!(unit>=0.0&&unit<1.0))throw new IllegalArgumentException("unit");

        boolean navigated=phase==GodPhase.GG||phase==GodPhase.G_ZONE||phase==GodPhase.SGG||
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
                if(navigated)yield new Outcome("COMMON_YELLOW7",15,false);
                boolean oneMedal=unit<NORMAL_ORDERED_ONE_MEDAL_RATE;
                yield new Outcome(oneMedal?"ORDERED_YELLOW7_ONE":"MISS",oneMedal?1:0,false);
            }
        };
    }

    private GodRoleOutcome(){}
}
