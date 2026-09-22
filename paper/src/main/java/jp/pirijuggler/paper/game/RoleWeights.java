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
        InternalRole.PIERO_BIG,InternalRole.PIERO_REG,InternalRole.GOD,InternalRole.MISS};
    private final int[][] cumulative=new int[6][ORDER.length];
    private final int[][] rawWeights=new int[6][ORDER.length];
    private final long[][] bonusFamilies=new long[6][2];
    private final Map<String,Object> sourceConfig;
    public RoleWeights(Map<String,Object> config) {
        sourceConfig=config;
        var settings=StartupProfile.map(StartupProfile.map(config.get("probabilities")).get("settings"));
        for(int setting=1;setting<=6;setting++) {
            var row=StartupProfile.map(settings.get(Integer.toString(setting)));long sum=0;
            for(var role:ORDER)if(role!=InternalRole.GOD&&!row.containsKey(role.name().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Missing role weight "+role);
            for(int i=0;i<ORDER.length;i++) {
                Object raw=row.get(ORDER[i].name().toLowerCase(Locale.ROOT));
                long value=raw instanceof Number n?n.longValue():0L;
                if(value<0||value>DENOMINATOR)throw new IllegalArgumentException("Invalid role weight");
                sum=Math.addExact(sum,value);rawWeights[setting-1][i]=Math.toIntExact(value);cumulative[setting-1][i]=Math.toIntExact(sum);
            }
            if(sum!=DENOMINATOR)throw new IllegalArgumentException("Role weights must total 1e9");
            bonusFamilies[setting-1][0]=number(row,"big")+number(row,"cherry_big")+number(row,"piero_big");
            bonusFamilies[setting-1][1]=number(row,"reg")+number(row,"cherry_reg")+number(row,"piero_reg");

        }
    }
    Map<String,Object> sourceConfig(){return sourceConfig;}
    public InternalRole draw(int setting,RandomGenerator rng) { return at(setting,rng.nextInt(DENOMINATOR)); }
    public InternalRole drawNonBonus(int setting,RandomGenerator rng) {
        for(int attempts=0;attempts<1024;attempts++){
            InternalRole role=draw(setting,rng);
            if(GameRules.bonus(role)==null && role!=InternalRole.GOD)return role;
        }
        throw new IllegalStateException("Unable to draw non-bonus role");
    }
    public InternalRole drawJugglerGod(int setting,RandomGenerator rng,int bonusScalePpm) {
        if(setting<1||setting>6||bonusScalePpm<0||bonusScalePpm>1_000_000)throw new IllegalArgumentException("JUGGLER_GOD weights");
        int roll=rng.nextInt(DENOMINATOR);long cursor=0,removed=0;
        int[] row=rawWeights[setting-1];
        for(int i=0;i<ORDER.length;i++){
            InternalRole role=ORDER[i];long weight=row[i];
            if(GameRules.bonus(role)!=null&&role!=InternalRole.GOD){
                long scaled=weight*bonusScalePpm/1_000_000L;
                removed+=weight-scaled;weight=scaled;
            } else if(role==InternalRole.MISS) weight+=removed;
            cursor+=weight;
            if(roll<cursor)return role;
        }
        throw new IllegalStateException("Uncovered JUGGLER_GOD draw interval");
    }
    public InternalRole drawJugglerGodNonBonus(int setting,RandomGenerator rng,int bonusScalePpm) {
        for(int attempts=0;attempts<1024;attempts++){
            InternalRole role=drawJugglerGod(setting,rng,bonusScalePpm);
            if(GameRules.bonus(role)==null&&role!=InternalRole.GOD)return role;
        }
        throw new IllegalStateException("Unable to draw JUGGLER_GOD non-bonus role");
    }
    public InternalRole drawBonusFamily(int setting,RandomGenerator rng) {
        if(setting<1||setting>6)throw new IllegalArgumentException("Setting");
        long big=bonusFamilies[setting-1][0],reg=bonusFamilies[setting-1][1],total=big+reg;
        if(total<=0)throw new IllegalStateException("No bonus-family weights for setting "+setting);
        long roll=rng.nextLong(total);
        return roll<big?InternalRole.BIG:InternalRole.REG;
    }
    public long bonusFamilyWeight(int setting,boolean big,int bonusScalePpm) {
        if(setting<1||setting>6||bonusScalePpm<0||bonusScalePpm>1_000_000)throw new IllegalArgumentException("JUGGLER_GOD weights");
        return bonusFamilies[setting-1][big?0:1]*bonusScalePpm/1_000_000L;
    }
    private static long number(Map<String,Object> row,String key){
        Object value=row.get(key);return value instanceof Number n?n.longValue():0L;
    }
    public InternalRole at(int setting,int value) {
        if(setting<1||setting>6||value<0||value>=DENOMINATOR)throw new IllegalArgumentException("Draw bounds");
        int[] ends=cumulative[setting-1];for(int i=0;i<ends.length;i++)if(value<ends[i])return ORDER[i];
        throw new IllegalStateException("Uncovered draw interval");
    }
}
