package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.config.FixedGameRules;
import jp.pirijuggler.paper.reel.InternalRole;

import java.util.ArrayDeque;
import java.util.random.RandomGenerator;

/**
 * Whole-machine JUGGLER_GOD economy simulation.
 *
 * Accounting intentionally mirrors the production successor engine:
 * - independent GOD is checked before the normal/heaven role table;
 * - the parent GOD starts its first BIG directly (no 1-medal entry bet);
 * - the four guaranteed follow-up BIGs consume a 3-medal normal bet but do not
 *   increment the public normal-game counter;
 * - post-guarantee continuation BIGs consume the same normal bet and do count
 *   as one normal game;
 * - BIG/REG acquired inside a bonus are released as 0G stock bonuses;
 * - GOD acquired inside any active bonus is confirmed as +7 BIG stock, matching
 *   JugglerGodGameEngine's current production behavior, rather than starting a
 *   second five-BIG parent chain.
 *
 * Ordinary/stock bonus entry still uses the project's established theoretical
 * convention of one 1-medal entry bet (GameRules.bonusTotalBet).
 */
public final class JugglerGodSimulator {
    private static final int GOD_DENOMINATOR = 8192;
    private static final int[] CONTINUATION_PERCENT = {0,75,78,80,82,85,90};
    private static final int GOD_IN_GOD_BIG_STOCK = 7;

    public record Result(
            int setting,long requestedLeverGames,long leverGames,long paidLeverGames,long replay,
            long grape,long cherry,long bell,long piero,long normalBig,long normalReg,long heavenBig,long heavenReg,
            long god,long godBig,long godReg,long godContinuationBig,
            long bonusInBonusBig,long bonusInBonusReg,long godDuringBonus,long godInGod,
            long heavenEntries,long heavenContinues,
            long totalBet,long totalPayout,double payoutPercent,long net
    ) {}

    private enum Mode { NORMAL, HEAVEN }

    private record BonusResolution(
            long bet,long payout,long addedBig,long addedReg,long god,long godBig,long godReg,
            long godContinuationBig,long godInGod,long countedNormalSpins,boolean forceHeaven
    ) {}

    private static final class BonusAccumulator {
        long bet,payout,addedBig,addedReg,god,godBig,godReg,continuation,godInGod,countedNormalSpins;
        BonusResolution result(boolean forceHeaven){
            return new BonusResolution(bet,payout,addedBig,addedReg,god,godBig,godReg,
                    continuation,godInGod,countedNormalSpins,forceHeaven);
        }
    }

    /** Compatibility entry point for the untuned Phase 02/03 boundary. */
    public static Result run(RoleWeights weights,int setting,long games,long normalBigToHeavenPpm,long normalRegToHeavenPpm,long heavenToHeavenPpm,RandomGenerator random) {
        return run(weights,setting,games,normalBigToHeavenPpm,normalRegToHeavenPpm,heavenToHeavenPpm,1_000_000,1_000_000,random);
    }

    public static Result run(RoleWeights weights,int setting,long games,long normalBigToHeavenPpm,long normalRegToHeavenPpm,long heavenToHeavenPpm,int bonusScalePpm,RandomGenerator random) {
        return run(weights,setting,games,normalBigToHeavenPpm,normalRegToHeavenPpm,heavenToHeavenPpm,bonusScalePpm,1_000_000,random);
    }

    public static Result run(RoleWeights weights,int setting,long games,long normalBigToHeavenPpm,long normalRegToHeavenPpm,long heavenToHeavenPpm,int bonusScalePpm,int smallRoleScalePpm,RandomGenerator random) {
        if(setting<1||setting>6||games<1||games>100_000_000L)throw new IllegalArgumentException("Simulator bounds");
        if(normalBigToHeavenPpm<0||normalBigToHeavenPpm>1_000_000
                ||normalRegToHeavenPpm<0||normalRegToHeavenPpm>1_000_000
                ||heavenToHeavenPpm<0||heavenToHeavenPpm>1_000_000
                ||bonusScalePpm<0||bonusScalePpm>1_000_000
                ||smallRoleScalePpm<0||smallRoleScalePpm>1_000_000)
            throw new IllegalArgumentException("Phase 03 tuning");

        long lever=0,paid=0,replay=0,grape=0,cherry=0,bell=0,piero=0;
        long normalBig=0,normalReg=0,heavenBig=0,heavenReg=0,god=0,godBig=0,godReg=0,godContinuationBig=0;
        long bonusInBonusBig=0,bonusInBonusReg=0,godDuringBonus=0,godInGod=0;
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
                BonusResolution g=resolveGodChain(weights,setting,bonusScalePpm,smallRoleScalePpm,random);
                bet+=g.bet;payout+=g.payout;godBig+=g.godBig;godReg+=g.godReg;
                godContinuationBig+=g.godContinuationBig;bonusInBonusBig+=g.addedBig;bonusInBonusReg+=g.addedReg;godInGod+=g.godInGod;
                lever+=g.countedNormalSpins;paid+=g.countedNormalSpins;
                mode=Mode.HEAVEN;heavenTarget=random.nextInt(32)+1;heavenProgress=0;heavenEntries++;
                continue;
            }

            InternalRole role;
            if(mode==Mode.HEAVEN){
                heavenProgress++;
                role=heavenProgress>=heavenTarget
                        ?weights.drawBonusFamily(setting,random)
                        :weights.drawJugglerGodNonBonus(setting,random,bonusScalePpm,smallRoleScalePpm);
            }else role=weights.drawJugglerGod(setting,random,bonusScalePpm,smallRoleScalePpm);

            switch(role){
                case REPLAY -> { replay++; free=true; }
                case GRAPE -> grape++;
                case BELL -> bell++;
                case CHERRY,CHERRY_BIG,CHERRY_REG -> cherry++;
                case PIERO,PIERO_BIG,PIERO_REG -> piero++;
                default -> {}
            }
            payout+=GameRules.payout(role);

            String bonusType=GameRules.bonus(role);
            if(bonusType==null)continue;

            boolean big="BIG".equals(bonusType);
            boolean originHeaven=mode==Mode.HEAVEN;
            if(originHeaven){if(big)heavenBig++;else heavenReg++;}
            else {if(big)normalBig++;else normalReg++;}

            BonusResolution br=resolveOrdinaryBonus(bonusType,weights,setting,bonusScalePpm,smallRoleScalePpm,random);
            bet+=br.bet;payout+=br.payout;
            bonusInBonusBig+=br.addedBig;bonusInBonusReg+=br.addedReg;
            god+=br.god;godDuringBonus+=br.god;godBig+=br.godBig;godReg+=br.godReg;
            godContinuationBig+=br.godContinuationBig;godInGod+=br.godInGod;
            lever+=br.countedNormalSpins;paid+=br.countedNormalSpins;

            if(br.forceHeaven){
                mode=Mode.HEAVEN;heavenTarget=random.nextInt(32)+1;heavenProgress=0;heavenEntries++;
            }else if(originHeaven){
                if(random.nextLong(1_000_000)<heavenToHeavenPpm){
                    mode=Mode.HEAVEN;heavenTarget=random.nextInt(32)+1;heavenProgress=0;heavenEntries++;heavenContinues++;
                }else{
                    mode=Mode.NORMAL;heavenTarget=0;heavenProgress=0;
                }
            }else {
                long normalHeavenPpm=big?normalBigToHeavenPpm:normalRegToHeavenPpm;
                if(random.nextLong(1_000_000)<normalHeavenPpm){
                    mode=Mode.HEAVEN;heavenTarget=random.nextInt(32)+1;heavenProgress=0;heavenEntries++;
                }
            }
        }

        return new Result(setting,games,lever,paid,replay,grape,cherry,bell,piero,
                normalBig,normalReg,heavenBig,heavenReg,god,godBig,godReg,godContinuationBig,
                bonusInBonusBig,bonusInBonusReg,godDuringBonus,godInGod,
                heavenEntries,heavenContinues,bet,payout,100.0*payout/bet,payout-bet);
    }

    private static BonusResolution resolveOrdinaryBonus(
            String initial,RoleWeights weights,int setting,int bonusScalePpm,int smallRoleScalePpm,RandomGenerator random
    ){
        BonusAccumulator a=new BonusAccumulator();
        ArrayDeque<String> stock=new ArrayDeque<>();
        stock.addLast(initial);
        while(!stock.isEmpty()){
            String type=stock.removeFirst();
            playStockBonus(a,type,false,stock,weights,setting,bonusScalePpm,smallRoleScalePpm,random);
        }
        // Current production keeps the original bonus's ordinary/heaven transition even when
        // a GOD overlay was found; overlay GOD itself does not force a second parent GOD chain.
        return a.result(false);
    }

    private static BonusResolution resolveGodChain(
            RoleWeights weights,int setting,int bonusScalePpm,int smallRoleScalePpm,RandomGenerator random
    ){
        BonusAccumulator a=new BonusAccumulator();

        // The BAR GOD itself directly starts BIG #1, so there is no 1-medal entry bet here.
        playParentGodBig(a,true,false,weights,setting,bonusScalePpm,smallRoleScalePpm,random);

        // Four guaranteed follow-ups. They use a paid normal bet but remain 0G in history.
        for(int i=0;i<4;i++)
            playParentGodBig(a,false,false,weights,setting,bonusScalePpm,smallRoleScalePpm,random);

        // Post-guarantee continuation BIGs use the same paid normal bet and count as 1G.
        int rate=CONTINUATION_PERCENT[setting];
        while(random.nextInt(100)<rate)
            playParentGodBig(a,false,true,weights,setting,bonusScalePpm,smallRoleScalePpm,random);

        return a.result(true);
    }

    private static void playParentGodBig(
            BonusAccumulator a,boolean first,boolean continuation,
            RoleWeights weights,int setting,int bonusScalePpm,int smallRoleScalePpm,RandomGenerator random
    ){
        if(first){
            a.bet+=FixedGameRules.BONUS_BET*GameRules.bonusGames("BIG");
        }else{
            // Production reaches a forced BIG through the normal 3-medal bet. The project's
            // theoretical RTP convention also reserves the ordinary 1-medal bonus-entry cost.
            a.bet+=FixedGameRules.NORMAL_BET+GameRules.bonusTotalBet("BIG");
        }
        a.payout+=GameRules.bonusGross("BIG");
        a.godBig++;
        if(continuation){a.continuation++;a.countedNormalSpins++;}

        ArrayDeque<String> stock=new ArrayDeque<>();
        drawBonusRounds(a,"BIG",true,stock,weights,setting,bonusScalePpm,smallRoleScalePpm,random);
        while(!stock.isEmpty()){
            String type=stock.removeFirst();
            playStockBonus(a,type,true,stock,weights,setting,bonusScalePpm,smallRoleScalePpm,random);
        }
    }

    private static void playStockBonus(
            BonusAccumulator a,String type,boolean insideGod,ArrayDeque<String> stock,
            RoleWeights weights,int setting,int bonusScalePpm,int smallRoleScalePpm,RandomGenerator random
    ){
        a.bet+=GameRules.bonusTotalBet(type);
        a.payout+=GameRules.bonusGross(type);
        if(insideGod){
            if("BIG".equals(type))a.godBig++;else a.godReg++;
        }
        drawBonusRounds(a,type,insideGod,stock,weights,setting,bonusScalePpm,smallRoleScalePpm,random);
    }

    private static void drawBonusRounds(
            BonusAccumulator a,String type,boolean insideGod,ArrayDeque<String> stock,
            RoleWeights weights,int setting,int bonusScalePpm,int smallRoleScalePpm,RandomGenerator random
    ){
        int rounds=GameRules.bonusGames(type);
        for(int i=0;i<rounds;i++){
            if(random.nextInt(GOD_DENOMINATOR)==0){
                a.payout+=GameRules.payout(InternalRole.GOD);
                if(insideGod)a.godInGod++;else a.god++;
                for(int n=0;n<GOD_IN_GOD_BIG_STOCK;n++){stock.addLast("BIG");a.addedBig++;}
                continue;
            }
            InternalRole hit=weights.drawJugglerGod(setting,random,bonusScalePpm,smallRoleScalePpm);
            String next=GameRules.bonus(hit);
            if(next!=null){
                stock.addLast(next);
                if("BIG".equals(next))a.addedBig++;else a.addedReg++;
            }
        }
    }

    private JugglerGodSimulator(){}
}
