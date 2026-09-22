package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.config.FixedGameRules;
import jp.pirijuggler.paper.reel.InternalRole;

import java.util.random.RandomGenerator;

/**
 * Whole-machine JUGGLER_GOD economy simulation.
 * Mirrors authoritative Phase 02 ordering: GOD check -> mode-specific role draw -> bonus -> mode transition.
 */
public final class JugglerGodSimulator {
    private static final int GOD_DENOMINATOR = 8192;
    private static final int[] CONTINUATION_PERCENT = {0,25,30,35,45,55,70};

    public record Result(
            int setting,long requestedLeverGames,long leverGames,long paidLeverGames,long replay,
            long grape,long cherry,long bell,long piero,long normalBig,long normalReg,long heavenBig,long heavenReg,
            long god,long godBig,long godContinuationBig,long heavenEntries,long heavenContinues,
            long totalBet,long totalPayout,double payoutPercent,long net
    ) {}

    private enum Mode { NORMAL, HEAVEN }

    public static Result run(RoleWeights weights,int setting,long games,long normalToHeavenPpm,long heavenToHeavenPpm,int bonusScalePpm,RandomGenerator random) {
        if(setting<1||setting>6||games<1||games>100_000_000L)throw new IllegalArgumentException("Simulator bounds");
        if(normalToHeavenPpm<0||normalToHeavenPpm>1_000_000||heavenToHeavenPpm<0||heavenToHeavenPpm>1_000_000
                ||bonusScalePpm<0||bonusScalePpm>1_000_000)
            throw new IllegalArgumentException("Phase 03 tuning");

        long lever=0,paid=0,replay=0,grape=0,cherry=0,bell=0,piero=0;
        long normalBig=0,normalReg=0,heavenBig=0,heavenReg=0,god=0,godBig=0,godContinuationBig=0;
        long heavenEntries=0,heavenContinues=0,bet=0,payout=0;
        boolean free=false;
        Mode mode=Mode.NORMAL;
        int heavenTarget=0,heavenProgress=0;

        while(lever<games){
            if((lever&65535)==0&&Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Simulator interrupted");

            lever++;
            if(!free){paid++;bet+=FixedGameRules.NORMAL_BET;}
            free=false;

            if(random.nextInt(GOD_DENOMINATOR)==0){
                god++;
                payout+=GameRules.payout(InternalRole.GOD);
                BonusAccounting first=bonus("BIG");bet+=first.bet;payout+=first.payout;godBig++;

                // Four remaining guaranteed stock BIGs are 0G in history but real 3-medal trigger games economically.
                for(int i=0;i<4;i++){
                    lever++;paid++;bet+=FixedGameRules.NORMAL_BET;
                    BonusAccounting b=bonus("BIG");bet+=b.bet;payout+=b.payout;godBig++;
                }
                while(random.nextInt(100)<CONTINUATION_PERCENT[setting]){
                    lever++;paid++;bet+=FixedGameRules.NORMAL_BET;
                    BonusAccounting b=bonus("BIG");bet+=b.bet;payout+=b.payout;godBig++;godContinuationBig++;
                }
                mode=Mode.HEAVEN;heavenTarget=random.nextInt(32)+1;heavenProgress=0;heavenEntries++;
                continue;
            }

            InternalRole role;
            if(mode==Mode.HEAVEN){
                heavenProgress++;
                role=heavenProgress>=heavenTarget
                        ?weights.drawBonusFamily(setting,random)
                        :weights.drawJugglerGodNonBonus(setting,random,bonusScalePpm);
            }else role=weights.drawJugglerGod(setting,random,bonusScalePpm);

            switch(role){
                case REPLAY -> { replay++; free=true; }
                case GRAPE -> grape++;
                case BELL -> bell++;
                case CHERRY -> cherry++;
                case PIERO -> piero++;
                case CHERRY_BIG -> cherry++;
                case CHERRY_REG -> cherry++;
                case PIERO_BIG -> piero++;
                case PIERO_REG -> piero++;
                default -> {}
            }
            payout+=GameRules.payout(role);

            String bonusType=GameRules.bonus(role);
            if(bonusType==null)continue;

            BonusAccounting b=bonus(bonusType);bet+=b.bet;payout+=b.payout;
            boolean big="BIG".equals(bonusType);
            if(mode==Mode.HEAVEN){
                if(big)heavenBig++; else heavenReg++;
                if(random.nextLong(1_000_000)<heavenToHeavenPpm){
                    mode=Mode.HEAVEN;heavenTarget=random.nextInt(32)+1;heavenProgress=0;heavenEntries++;heavenContinues++;
                }else{
                    mode=Mode.NORMAL;heavenTarget=0;heavenProgress=0;
                }
            }else{
                if(big)normalBig++; else normalReg++;
                if(random.nextLong(1_000_000)<normalToHeavenPpm){
                    mode=Mode.HEAVEN;heavenTarget=random.nextInt(32)+1;heavenProgress=0;heavenEntries++;
                }
            }
        }

        return new Result(setting,games,lever,paid,replay,grape,cherry,bell,piero,
                normalBig,normalReg,heavenBig,heavenReg,god,godBig,godContinuationBig,
                heavenEntries,heavenContinues,bet,payout,100.0*payout/bet,payout-bet);
    }

    private record BonusAccounting(long bet,long payout) {}
    private static BonusAccounting bonus(String type){
        return new BonusAccounting(GameRules.bonusTotalBet(type),GameRules.bonusGross(type));
    }
    private JugglerGodSimulator(){}
}
