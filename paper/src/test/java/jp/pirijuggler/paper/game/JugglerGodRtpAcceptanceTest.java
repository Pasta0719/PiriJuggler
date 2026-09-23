package jp.pirijuggler.paper.game;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * NEXT Phase 03 long-run whole-machine RTP acceptance.
 * Uses the same production tuning values currently locked in config.yml.
 */
class JugglerGodRtpAcceptanceTest {
    private static final long GAMES = 3_000_000L;
    private static final double[] TARGET = {0.0,97.5,99.0,101.5,105.0,109.5,115.0};
    private static final int[] SCALE = {0,804200,801100,809800,814400,824600,810900};
    private static final double TOLERANCE_PP = 2.0;

    @Test void productionTuningConvergesToLockedTargets() throws Exception {
        var weights=new RoleWeights(GameFixture.defaults());
        for(int setting=1;setting<=6;setting++){
            long seed=0x4A5547474C455247L+setting;
            var result=JugglerGodSimulator.run(
                    weights,setting,GAMES,
                    125_000,62_500,500_000,
                    SCALE[setting],SCALE[setting],
                    new SplittableRandom(seed));
            double delta=result.payoutPercent()-TARGET[setting];
            System.out.printf(
                    "NEXT_PHASE03_RTP setting=%d games=%d seed=%d target=%.3f observed=%.6f delta_pp=%+.6f bet=%d payout=%d god=%d heavenEntries=%d%n",
                    setting,GAMES,seed,TARGET[setting],result.payoutPercent(),delta,
                    result.totalBet(),result.totalPayout(),result.god(),result.heavenEntries());
            assertEquals(TARGET[setting],result.payoutPercent(),TOLERANCE_PP,
                    "setting "+setting+" whole-machine RTP outside Phase 03 acceptance band");
        }
    }
}
