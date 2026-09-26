package jp.pirijuggler.paper.game;

import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JugglerGodDistributionProbeTest {
    private static final int[] STD_BONUS={0,743613,734884,734653,739194,741839,696323};
    private static final int[] STD_SMALL={0,813500,809600,819100,827000,841000,833500};
    private static final int[] EXT_BONUS={0,564190,554200,558800,562600,571106,537600};
    private static final int EXT_SMALL=700000;

    @Test void distributionProbe() throws Exception {
        String profile=value("piri.dist.profile","PIRI_DIST_PROFILE",null);
        if(profile==null)return;
        int setting=Integer.parseInt(value("piri.dist.setting","PIRI_DIST_SETTING",null));
        int trials=Integer.parseInt(value("piri.dist.trials","PIRI_DIST_TRIALS","10000"));
        long games=Long.parseLong(value("piri.dist.games","PIRI_DIST_GAMES","60000"));
        assertTrue(setting>=1&&setting<=6);
        assertTrue(trials>0);
        assertTrue(games>0);

        var weights=new RoleWeights(GameFixture.defaults());
        long[] net=new long[trials];
        long totalNet=0,totalBet=0,totalPayout=0,totalGod=0;
        int wins=0,losses=0,zeros=0,plus5k=0,plus10k=0,minus5k=0,minus10k=0,noGod=0;

        for(int i=0;i<trials;i++){
            long seed=0x44535450524F4245L
                    ^ ((long)profile.hashCode()<<32)
                    ^ ((long)setting<<24)
                    ^ i;
            if("standard".equalsIgnoreCase(profile)){
                var r=JugglerGodSimulator.run(weights,setting,games,
                        125_000,62_500,500_000,
                        STD_BONUS[setting],STD_SMALL[setting],new SplittableRandom(seed));
                net[i]=r.net();totalBet+=r.totalBet();totalPayout+=r.totalPayout();totalGod+=r.god();
                if(r.god()==0)noGod++;
            }else if("extreme".equalsIgnoreCase(profile)){
                var r=JugglerGodExtremeSimulator.run(weights,setting,games,
                        EXT_BONUS[setting],EXT_SMALL,new SplittableRandom(seed));
                net[i]=r.net();totalBet+=r.totalBet();totalPayout+=r.totalPayout();totalGod+=r.god();
                if(r.god()==0)noGod++;
            }else throw new IllegalArgumentException("profile");

            long n=net[i];totalNet+=n;
            if(n>0)wins++; else if(n<0)losses++; else zeros++;
            if(n>=5000)plus5k++;if(n>=10000)plus10k++;
            if(n<=-5000)minus5k++;if(n<=-10000)minus10k++;
        }

        Arrays.sort(net);
        double aggregateRtp=100.0*totalPayout/totalBet;
        System.out.printf(
                "JG_DIST profile=%s setting=%d games=%d trials=%d rtp=%.6f avgNet=%.3f median=%d p05=%d p10=%d p25=%d p75=%d p90=%d p95=%d wins=%d winRate=%.4f losses=%d lossRate=%.4f zeros=%d plus5k=%d plus5kRate=%.4f plus10k=%d plus10kRate=%.4f minus5k=%d minus5kRate=%.4f minus10k=%d minus10kRate=%.4f avgGod=%.5f noGod=%d noGodRate=%.4f%n",
                profile,setting,games,trials,aggregateRtp,totalNet/(double)trials,
                percentile(net,0.50),percentile(net,0.05),percentile(net,0.10),percentile(net,0.25),
                percentile(net,0.75),percentile(net,0.90),percentile(net,0.95),
                wins,100.0*wins/trials,losses,100.0*losses/trials,zeros,
                plus5k,100.0*plus5k/trials,plus10k,100.0*plus10k/trials,
                minus5k,100.0*minus5k/trials,minus10k,100.0*minus10k/trials,
                totalGod/(double)trials,noGod,100.0*noGod/trials);
    }

    private static String value(String property,String env,String fallback){
        String v=System.getProperty(property);
        if(v==null||v.isBlank())v=System.getenv(env);
        return v==null||v.isBlank()?fallback:v;
    }

    private static long percentile(long[] sorted,double q){
        int index=(int)Math.round(q*(sorted.length-1));
        return sorted[Math.max(0,Math.min(sorted.length-1,index))];
    }
}
