package jp.pirijuggler.paper.game;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.SplittableRandom;
import static org.junit.jupiter.api.Assertions.*;

class JugglerGodExtremeRtpProbeTest extends GameFixture {
    @Test void printHundredMillionGameAggregateRtp(){
        var weights=new RoleWeights(config);
        @SuppressWarnings("unchecked")
        Map<String,Object> tuning=(Map<String,Object>)config.get("juggler_god_extreme");
        @SuppressWarnings("unchecked")
        Map<String,Object> settings=(Map<String,Object>)tuning.get("settings");
        double[] target={0,97.5,99.0,101.5,105.0,109.5,115.0};
        final long games=20_000_000L;
        final int replications=5;
        for(int setting=1;setting<=6;setting++){
            @SuppressWarnings("unchecked")
            Map<String,Object> row=(Map<String,Object>)settings.get(Integer.toString(setting));
            int bonus=((Number)row.get("bonus_scale_ppm")).intValue();
            int small=((Number)row.get("small_role_scale_ppm")).intValue();
            long totalBet=0,totalPayout=0,totalGod=0,totalGodInGod=0,totalLever=0;
            for(int rep=0;rep<replications;rep++){
                long seed=0x45585452454d4500L + setting*1000L + rep;
                var result=JugglerGodExtremeSimulator.run(weights,setting,games,bonus,small,new SplittableRandom(seed));
                totalBet=Math.addExact(totalBet,result.totalBet());
                totalPayout=Math.addExact(totalPayout,result.totalPayout());
                totalGod=Math.addExact(totalGod,result.god());
                totalGodInGod=Math.addExact(totalGodInGod,result.godInGod());
                totalLever=Math.addExact(totalLever,result.leverGames());
                System.out.printf("EXTREME_RTP_RUN setting=%d rep=%d games=%d observed=%.6f god=%d godInGod=%d%n",
                        setting,rep+1,result.leverGames(),result.payoutPercent(),result.god(),result.godInGod());
            }
            double observed=100.0*totalPayout/totalBet;
            System.out.printf("EXTREME_RTP_AGG setting=%d games=%d target=%.3f observed=%.6f delta=%+.6f god=%d godInGod=%d%n",
                    setting,totalLever,target[setting],observed,observed-target[setting],totalGod,totalGodInGod);
            assertTrue(Double.isFinite(observed));
        }
    }
}
