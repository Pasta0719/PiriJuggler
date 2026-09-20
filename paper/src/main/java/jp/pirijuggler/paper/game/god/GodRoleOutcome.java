package jp.pirijuggler.paper.game.god;

/**
 * Server-authoritative settlement facts for one GOD role.
 *
 * Published facts are kept separate from stop-control presentation so the
 * displayed result and credited medals cannot silently drift apart again.
 */
public final class GodRoleOutcome {
    public record Outcome(int payout, boolean replay) {
        public Outcome {
            if(payout<0)throw new IllegalArgumentException("payout");
        }
    }

    /**
     * Published role settlement where the payout is role-defined.
     *
     * ORDERED_YELLOW7 is phase-dependent: in AT-like phases it is the navigated
     * 15-medal role. In normal/G-ZONE the exact 1-medal miss distribution is not
     * publicly specified; Piri uses a 1-medal non-winning settlement there and
     * documents it as a calibration assumption rather than an exact machine rule.
     */
    public static Outcome forRole(GodRole role,GodPhase phase){
        boolean atLike=phase==GodPhase.GG||phase==GodPhase.SGG||phase==GodPhase.SGG_COMEBACK||
                phase==GodPhase.Z_ZONE||phase==GodPhase.Z_GAME;
        return switch(role){
            case UPPER_BLUE7,MIDDLE_BLUE7,RED7_FAKE -> new Outcome(0,true);
            case LOWER_YELLOW7 -> new Outcome(3,false);
            case RISING_YELLOW7,MIDDLE_YELLOW7,COMMON_YELLOW7,SP,RED7,GOD -> new Outcome(15,false);
            case GAIA_BELL -> new Outcome(1,false);
            case ORDERED_YELLOW7 -> new Outcome(atLike?15:1,false);
            case MISS -> new Outcome(0,false);
        };
    }

    private GodRoleOutcome(){}
}
