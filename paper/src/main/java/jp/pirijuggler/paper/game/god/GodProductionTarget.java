package jp.pirijuggler.paper.game.god;

/** Locked production payout target for Piri GOD. */
public final class GodProductionTarget {
    private static final GodPayoutCurve CURVE=
            GodPayoutCurve.of(97.2,99.1,102.1,106.9,111.7,114.6);

    private GodProductionTarget() {}

    public static GodPayoutCurve curve() {
        return CURVE;
    }
}
