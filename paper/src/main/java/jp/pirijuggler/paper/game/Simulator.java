package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.config.FixedGameRules;
import jp.pirijuggler.paper.reel.InternalRole;
import java.util.random.RandomGenerator;

/** Pure worker computation: no Bukkit, database, live machine stream or financial side effect. */
public final class Simulator {
    public record Result(long normalSpins,long paidNormalSpins,long replay,long grape,long cherry,long bell,long piero,
                         long big,long reg,long totalBet,long totalPayout,double payoutPercent,long net) {}
    public static Result run(RoleWeights weights,int setting,long games,RandomGenerator random) {
        if(setting<1||setting>6||games<1||games>100_000_000L)throw new IllegalArgumentException("Simulator bounds");
        long paid=0,bet=0,payout=0,big=0,reg=0;long[] counts=new long[InternalRole.values().length];boolean free=false;
        for(long i=0;i<games;i++) {
            if((i&65535)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Simulator interrupted");
            if(!free){paid++;bet+=FixedGameRules.NORMAL_BET;}
            InternalRole role=weights.draw(setting,random);counts[role.ordinal()]++;payout+=GameRules.payout(role);
            free=role==InternalRole.REPLAY;String bonus=GameRules.bonus(role);
            if(bonus!=null){bet+=GameRules.bonusTotalBet(bonus);payout+=GameRules.bonusGross(bonus);if(bonus.equals("BIG"))big++;else reg++;}
        }
        return new Result(games,paid,counts[InternalRole.REPLAY.ordinal()],counts[InternalRole.GRAPE.ordinal()],
            counts[InternalRole.CHERRY.ordinal()]+counts[InternalRole.CHERRY_BIG.ordinal()]+counts[InternalRole.CHERRY_REG.ordinal()],
            counts[InternalRole.BELL.ordinal()],counts[InternalRole.PIERO.ordinal()]+counts[InternalRole.PIERO_BIG.ordinal()]+counts[InternalRole.PIERO_REG.ordinal()],
            big,reg,bet,payout,100.0*payout/bet,payout-bet);
    }
    private Simulator() {}
}
