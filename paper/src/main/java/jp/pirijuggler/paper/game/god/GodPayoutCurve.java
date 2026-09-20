package jp.pirijuggler.paper.game.god;

import java.util.Arrays;

/**
 * Explicit six-setting payout target curve.
 *
 * A benchmark curve is not automatically the production target. Callers must
 * pass the curve they actually want to fit.
 */
public final class GodPayoutCurve {
    private final double[] payoutPercent;

    private GodPayoutCurve(double[] payoutPercent) {
        this.payoutPercent = payoutPercent;
    }

    public static GodPayoutCurve of(double s1,double s2,double s3,double s4,double s5,double s6) {
        double[] values={s1,s2,s3,s4,s5,s6};
        for(double v:values) {
            if(!Double.isFinite(v) || v<=0) throw new IllegalArgumentException("payoutPercent");
        }
        return new GodPayoutCurve(values);
    }

    /** Kamigami no Kiseki published curve, for reference only. */
    public static GodPayoutCurve kisekiBenchmark() {
        return of(97.2,99.1,102.1,106.9,111.7,114.6);
    }

    public double payoutPercent(int setting) {
        if(setting<1 || setting>6) throw new IllegalArgumentException("setting");
        return payoutPercent[setting-1];
    }

    public double targetNetPerGame(int setting) {
        return GodEconomyTargets.BET_PER_GAME*(payoutPercent(setting)/100.0-1.0);
    }

    public double[] copy() {
        return Arrays.copyOf(payoutPercent,payoutPercent.length);
    }
}
