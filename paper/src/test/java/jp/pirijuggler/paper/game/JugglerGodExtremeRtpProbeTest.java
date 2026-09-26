package jp.pirijuggler.paper.game;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.SplittableRandom;
import static org.junit.jupiter.api.Assertions.*;

class JugglerGodExtremeRtpProbeTest extends GameFixture {
    @Test void printOneMillionGameRtpProbe(){
        var weights=new RoleWeights(config);
        @SuppressWarnings("unchecked")
        Map<String,Object> tuning=(Map<String,Object>)config.get("juggler_god_extreme");
        @SuppressWarnings("unchecked")
        Map<String,Object> settings=(Map<String,Object>)tuning.get("settings");
        double[] target={0,97.5,99.0,101.5,105.0,109.5,115.0};
        for(int setting=1;setting<=6;setting++){
            @SuppressWarnings("unchecked")
            Map<String,Object> row=(Map<String,Object>)settings.get(Integer.toString(setting));
            int bonus=((Number)row.get("bonus_scale_ppm")).intValue();
            int small=((Number)row.get("small_role_scale_ppm")).intValue();
            var result=JugglerGodExtremeSimulator.run(weights,setting,20_000_000L,bonus,small,
                    new SplittableRandom(0x45585452454d4500L+setting));
            System.out.printf("EXTREME_RTP_PROBE setting=%d games=%d target=%.3f observed=%.6f delta=%+.6f god=%d godInGod=%d%n",
                    setting,result.leverGames(),target[setting],result.payoutPercent(),result.payoutPercent()-target[setting],result.god(),result.godInGod());
            assertTrue(Double.isFinite(result.payoutPercent()));
        }
    }
}
