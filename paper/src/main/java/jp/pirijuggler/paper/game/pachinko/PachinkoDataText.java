package jp.pirijuggler.paper.game.pachinko;

import java.util.List;
import java.util.Locale;

/** Public pachinko data display with observed rotation kept distinct from theoretical border. */
public final class PachinkoDataText {
    public static List<String> detail(int machineId, PachinkoRuntime runtime) {
        PachinkoStatistics s = runtime.statistics();
        double rotation = runtime.measuredSpinsPer1000Yen();
        double difference = PachinkoSimulator.observedDifferenceBalls(runtime);
        return List.of(
                "--- PACHINKO #" + machineId + " ---",
                "STARTS " + runtime.totalStarts() + " / FIRED " + runtime.totalFired(),
                "OBSERVED ROTATION " + decimal(rotation) + " starts/1000",
                "THEORETICAL BORDER " + decimal(PachinkoSpec.equivalentBorderSpinsPer1000Yen()) + " starts/1000",
                "INITIAL " + s.initialJackpots() + " / 450 " + s.initial450() + " / 1500 " + s.initial1500(),
                "RUSH ENTRY " + s.rushEntries() + " / RIGHT WINS " + s.rightWins(),
                "RIGHT 1500 " + s.right1500() + " / 3000 " + s.right3000(),
                "PAYOUT " + runtime.cumulativePayout() + " / DIFF " + signed(difference)
        );
    }

    private static String decimal(double value) { return String.format(Locale.ROOT, "%.4f", value); }
    private static String signed(double value) { return (value > 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f", value); }
    private PachinkoDataText() {}
}
