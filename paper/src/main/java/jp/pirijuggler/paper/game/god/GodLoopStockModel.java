package jp.pirijuggler.paper.game.god;

/**
 * Exact expectation for Kiseki-style loop stock.
 *
 * A loop stock is tested repeatedly on lever-on. Every success grants one GG stock
 * and the same loop continues; the first failure ends that loop.
 */
public final class GodLoopStockModel {
    public enum Loop {
        A_1(0.01),
        B_25(0.25),
        C_50(0.50),
        D_80(0.80);

        private final double continuation;
        Loop(double continuation) { this.continuation = continuation; }
        public double continuation() { return continuation; }
    }

    public record Mix(double a1, double b25, double c50, double d80) {
        public Mix {
            probability(a1, "a1");
            probability(b25, "b25");
            probability(c50, "c50");
            probability(d80, "d80");
            if (Math.abs(a1+b25+c50+d80-1.0) > 1e-9)
                throw new IllegalArgumentException("mix sum");
        }
    }

    private GodLoopStockModel() {}

    /** Expected additional GG stocks from one loop stock. */
    public static double expectedAdditionalStocks(double continuation) {
        probability(continuation, "continuation");
        if (continuation >= 1.0) throw new IllegalArgumentException("continuation");
        return continuation / (1.0 - continuation);
    }

    public static double expectedAdditionalStocks(Loop loop) {
        return expectedAdditionalStocks(loop.continuation());
    }

    public static double expectedAdditionalStocks(Mix mix) {
        return mix.a1()*expectedAdditionalStocks(Loop.A_1)
                + mix.b25()*expectedAdditionalStocks(Loop.B_25)
                + mix.c50()*expectedAdditionalStocks(Loop.C_50)
                + mix.d80()*expectedAdditionalStocks(Loop.D_80);
    }

    public static double expectedNetMedals(Mix mix) {
        return expectedAdditionalStocks(mix) * GodEconomyTargets.ggSetNetMedals();
    }

    /** Published Kiseki mix for GG hit while in Heaven. */
    public static Mix heavenMix() {
        return new Mix(0.250, 0.250, 0.469, 0.031);
    }

    /** Published Kiseki mix for GG hit while in Super-Heaven. */
    public static Mix superHeavenMix() {
        return new Mix(0.0, 0.0, 0.750, 0.250);
    }

    /** Published Kiseki mix for blue-7 history hit at 3–4 consecutive. */
    public static Mix blue7ThreeToFourMix() {
        return new Mix(0.996, 0.0, 0.0, 0.004);
    }

    /** Published Kiseki mix for blue-7 history hit at 5+ consecutive. */
    public static Mix blue7FivePlusMix() {
        return new Mix(0.500, 0.418, 0.078, 0.004);
    }

    /** Published Kiseki mix for yellow-7 history GG hit. */
    public static Mix yellow7HistoryMix() {
        return new Mix(0.418, 0.332, 0.148, 0.102);
    }

    private static void probability(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException(name);
    }
}
