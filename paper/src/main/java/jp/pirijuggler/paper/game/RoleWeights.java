package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.database.StartupProfile;
import jp.pirijuggler.paper.reel.InternalRole;
import java.util.*;
import java.util.random.RandomGenerator;

/** Integer intervals; overlapping roles are drawn once, without a second bonus draw. */
public final class RoleWeights {
    public static final int DENOMINATOR=1_000_000_000;
    private static final InternalRole[] ORDER={InternalRole.REPLAY,InternalRole.GRAPE,InternalRole.BELL,InternalRole.CHERRY,
        InternalRole.PIERO,InternalRole.BIG,InternalRole.REG,InternalRole.CHERRY_BIG,InternalRole.CHERRY_REG,
        InternalRole.PIERO_BIG,InternalRole.PIERO_REG,InternalRole.MISS};
    private final int[][] cumulative=new int[6][12];
    private final Map<String,Object> sourceConfig;
    public RoleWeights(Map<String,Object> config) {
        sourceConfig=config;
        var settings=StartupProfile.map(StartupProfile.map(config.get("probabilities")).get("settings"));
        for(int setting=1;setting<=6;setting++) {
            var row=StartupProfile.map(settings.get(Integer.toString(setting)));long sum=0;
            for(var role:ORDER)if(!row.containsKey(role.name().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Missing role weight "+role);
            for(int i=0;i<ORDER.length;i++) {
                long value=((Number)row.get(ORDER[i].name().toLowerCase(Locale.ROOT))).longValue();
                if(value<0||value>DENOMINATOR)throw new IllegalArgumentException("Invalid role weight");
                sum=Math.addExact(sum,value);cumulative[setting-1][i]=Math.toIntExact(sum);
            }
            if(sum!=DENOMINATOR)throw new IllegalArgumentException("Role weights must total 1e9");
        }
    }
    Map<String,Object> sourceConfig(){return sourceConfig;}
    public InternalRole draw(int setting,RandomGenerator rng) { return at(setting,rng.nextInt(DENOMINATOR)); }
    public InternalRole at(int setting,int value) {
        if(setting<1||setting>6||value<0||value>=DENOMINATOR)throw new IllegalArgumentException("Draw bounds");
        int[] ends=cumulative[setting-1];for(int i=0;i<ends.length;i++)if(value<ends[i])return ORDER[i];
        throw new IllegalStateException("Uncovered draw interval");
    }
}
