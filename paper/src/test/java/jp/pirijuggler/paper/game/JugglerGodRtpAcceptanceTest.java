package jp.pirijuggler.paper.game;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * NEXT Phase 03 long-run whole-machine RTP acceptance.
 * Uses the same production tuning values currently locked in config.yml.
 * Five independent fixed-seed replications provide an empirical 95% CI for
 * the mean RTP instead of treating one deterministic run as sampling evidence.
 */
class JugglerGodRtpAcceptanceTest {
    private static final long GAMES_PER_REPLICATION = 3_000_000L;
    private static final int REPLICATIONS = 5;
    private static final double[] TARGET = {0.0,97.5,99.0,101.5,105.0,109.5,115.0};
    private static final int[] BONUS_SCALE = {0,743613,734884,734653,739194,741839,696323};
    private static final int[] SMALL_ROLE_SCALE = {0,805150,800295,810566,816063,824997,810240};
    private static final double TOLERANCE_PP = 2.0;
    // Student-t 97.5th percentile for df=4 (two-sided 95% CI, n=5).
    private static final double T95_DF4 = 2.7764451051977987;

    @Test void productionTuningConvergesToLockedTargets() throws Exception {
        var weights=new RoleWeights(GameFixture.defaults());
        for(int setting=1;setting<=6;setting++){
            double[] rtp=new double[REPLICATIONS];
            long totalBet=0,totalPayout=0,totalGod=0,totalHeavenEntries=0;
            for(int replication=0;replication<REPLICATIONS;replication++){
                long seed=0x4A5547474C455247L+setting+0x10000L*replication;
                var result=JugglerGodSimulator.run(
                        weights,setting,GAMES_PER_REPLICATION,
                        125_000,62_500,500_000,
                        BONUS_SCALE[setting],SMALL_ROLE_SCALE[setting],
                        new SplittableRandom(seed));
                rtp[replication]=result.payoutPercent();
                totalBet+=result.totalBet();totalPayout+=result.totalPayout();
                totalGod+=result.god();totalHeavenEntries+=result.heavenEntries();
                System.out.printf(
                        "NEXT_PHASE03_RTP_RUN setting=%d replication=%d games=%d seed=%d observed=%.6f god=%d heavenEntries=%d%n",
                        setting,replication+1,GAMES_PER_REPLICATION,seed,result.payoutPercent(),result.god(),result.heavenEntries());
            }
            double mean=0.0;
            for(double value:rtp)mean+=value;
            mean/=REPLICATIONS;
            double sumSquares=0.0;
            for(double value:rtp){double d=value-mean;sumSquares+=d*d;}
            double sampleSd=Math.sqrt(sumSquares/(REPLICATIONS-1));
            double ciHalfWidth=T95_DF4*sampleSd/Math.sqrt(REPLICATIONS);
            double delta=mean-TARGET[setting];
            System.out.printf(
                    "NEXT_PHASE03_RTP setting=%d totalGames=%d replications=%d target=%.3f mean=%.6f delta_pp=%+.6f sample_sd_pp=%.6f ci95_low=%.6f ci95_high=%.6f ci95_halfwidth_pp=%.6f bet=%d payout=%d god=%d heavenEntries=%d%n",
                    setting,GAMES_PER_REPLICATION*REPLICATIONS,REPLICATIONS,TARGET[setting],mean,delta,sampleSd,
                    mean-ciHalfWidth,mean+ciHalfWidth,ciHalfWidth,totalBet,totalPayout,totalGod,totalHeavenEntries);
            assertEquals(TARGET[setting],mean,TOLERANCE_PP,
                    "setting "+setting+" mean whole-machine RTP outside Phase 03 acceptance band");
        }
    }
}
