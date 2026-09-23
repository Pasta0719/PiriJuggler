package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.config.FixedGameRules;
import jp.pirijuggler.paper.reel.InternalRole;

import java.util.ArrayDeque;
import java.util.random.RandomGenerator;

/**
 * Whole-machine JUGGLER_GOD economy simulation.
 * Phase 03 model includes visible bonus-in-bonus stock and GOD-in-GOD +7 BIG stock.
 */
public final class JugglerGodSimulator {
    private static final int GOD_DENOMINATOR = 8192;
    private static final int[] CONTINUATION_PERCENT = {0,25,30,35,45,55,70};
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
            long godContinuationBig,long godInGod,boolean forceHeaven
    ) {
        BonusResolution plus(BonusResolution o){
            return new BonusResolution(
                    bet+o.bet,payout+o.payout,addedBig+o.addedBig,addedReg+o.addedReg,
                    god+o.god,godBig+o.godBig,godReg+o.godReg,
                    godContinuationBig+o.godContinuationBig,godInGod+o.godInGod,
                    forceHeaven||o.forceHeaven
            );
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
        ArrayDeque<String> queue=new ArrayDeque<>();queue.add(initial);
        long bet=0,payout=0,addedBig=0,addedReg=0,god=0,godBig=0,godReg=0,godContinuation=0,godInGod=0;
        boolean forceHeaven=false;
        while(!queue.isEmpty()){
            String type=queue.removeFirst();
            bet+=GameRules.bonusTotalBet(type);payout+=GameRules.bonusGross(type);
            int rounds=GameRules.bonusGames(type);
            for(int i=0;i<rounds;i++){
                if(random.nextInt(GOD_DENOMINATOR)==0){
                    god++;payout+=GameRules.payout(InternalRole.GOD);forceHeaven=true;
                    BonusResolution g=resolveGodChain(weights,setting,bonusScalePpm,smallRoleScalePpm,random);
                    bet+=g.bet;payout+=g.payout;addedBig+=g.addedBig;addedReg+=g.addedReg;
                    godBig+=g.godBig;godReg+=g.godReg;godContinuation+=g.godContinuationBig;godInGod+=g.godInGod;
                    continue;
                }
                InternalRole hit=weights.drawJugglerGod(setting,random,bonusScalePpm,smallRoleScalePpm);
                String stock=GameRules.bonus(hit);
                if(stock!=null){
                    queue.addLast(stock);
                    if("BIG".equals(stock))addedBig++;else addedReg++;
                }
            }
        }
        return new BonusResolution(bet,payout,addedBig,addedReg,god,godBig,godReg,godContinuation,godInGod,forceHeaven);
    }

    private static BonusResolution resolveGodChain(
            RoleWeights weights,int setting,int bonusScalePpm,int smallRoleScalePpm,RandomGenerator random
    ){
        ArrayDeque<String> queue=new ArrayDeque<>();
        for(int i=0;i<5;i++)queue.addLast("BIG");
        long continuation=0;
        int rate=CONTINUATION_PERCENT[setting];
        while(random.nextInt(100)<rate){queue.addLast("BIG");continuation++;}

        long bet=0,payout=0,addedBig=0,addedReg=0,godBig=0,godReg=0,godInGod=0;
        while(!queue.isEmpty()){
            String type=queue.removeFirst();
            bet+=GameRules.bonusTotalBet(type);payout+=GameRules.bonusGross(type);
            if("BIG".equals(type))godBig++;else godReg++;
            int rounds=GameRules.bonusGames(type);
            for(int i=0;i<rounds;i++){
                if(random.nextInt(GOD_DENOMINATOR)==0){
                    godInGod++;payout+=GameRules.payout(InternalRole.GOD);
                    for(int n=0;n<GOD_IN_GOD_BIG_STOCK;n++){queue.addLast("BIG");addedBig++;}
                    continue;
                }
                InternalRole hit=weights.drawJugglerGod(setting,random,bonusScalePpm,smallRoleScalePpm);
                String stock=GameRules.bonus(hit);
                if(stock!=null){
                    queue.addLast(stock);
                    if("BIG".equals(stock))addedBig++;else addedReg++;
                }
            }
        }
        return new BonusResolution(bet,payout,addedBig,addedReg,0,godBig,godReg,continuation,godInGod,true);
    }

    private JugglerGodSimulator(){}
}
