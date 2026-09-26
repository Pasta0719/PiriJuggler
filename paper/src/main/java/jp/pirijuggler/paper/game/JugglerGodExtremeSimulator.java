package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.config.FixedGameRules;
import jp.pirijuggler.paper.reel.InternalRole;

import java.util.ArrayDeque;
import java.util.random.RandomGenerator;

/** Economy simulator that mirrors the production JUGGLER_GOD_EXTREME profile. */
public final class JugglerGodExtremeSimulator {
    public static final int GOD_DENOMINATOR=16384;
    public static final int BIG_PAYOUT=420;
    public static final int REG_PAYOUT=168;
    public static final int GUARANTEED_BIGS=8;
    public static final int GOD_IN_GOD_BIG_STOCK=10;
    private static final int[] CONTINUATION={0,75,78,80,82,85,90};

    public record Result(int setting,long requestedGames,long leverGames,long paidGames,long god,long godInGod,
                         long normalBig,long normalReg,long heavenBig,long heavenReg,long godBig,long godReg,
                         long heavenEntries,long heavenContinues,long totalBet,long totalPayout,double payoutPercent,long net){}

    private enum Mode { NORMAL, HEAVEN }
    private static final class Acc {
        final RoleWeights weights; final int setting,bonusScale,smallScale; final RandomGenerator rng;
        long lever,paid,god,godInGod,normalBig,normalReg,heavenBig,heavenReg,godBig,godReg,heavenEntries,heavenContinues,bet,payout;
        boolean free; Mode mode=Mode.NORMAL; int heavenTarget,heavenProgress;
        Acc(RoleWeights w,int s,int b,int sm,RandomGenerator r){weights=w;setting=s;bonusScale=b;smallScale=sm;rng=r;}
    }

    public static Result run(RoleWeights weights,int setting,long games,int bonusScalePpm,int smallRoleScalePpm,RandomGenerator rng){
        if(setting<1||setting>6||games<1||games>100_000_000L)throw new IllegalArgumentException("bounds");
        Acc a=new Acc(weights,setting,bonusScalePpm,smallRoleScalePpm,rng);
        while(a.lever<games){
            a.lever++;
            if(!a.free){a.paid++;a.bet+=FixedGameRules.NORMAL_BET;}
            a.free=false;

            if(rng.nextInt(GOD_DENOMINATOR)==0){
                a.god++;a.payout+=GameRules.payout(InternalRole.GOD);
                playGod(a,new ArrayDeque<>());
                enterHeaven(a);continue;
            }

            boolean originHeaven=a.mode==Mode.HEAVEN;
            InternalRole role;
            if(originHeaven){
                a.heavenProgress++;
                role=a.heavenProgress>=a.heavenTarget
                        ?weights.drawBonusFamily(setting,rng)
                        :weights.drawJugglerGodNonBonus(setting,rng,bonusScalePpm,smallRoleScalePpm);
            }else role=weights.drawJugglerGod(setting,rng,bonusScalePpm,smallRoleScalePpm);

            if(role==InternalRole.REPLAY)a.free=true;
            a.payout+=GameRules.payout(role);
            String type=GameRules.bonus(role);
            if(type==null)continue;
            boolean big="BIG".equals(type);
            if(originHeaven){if(big)a.heavenBig++;else a.heavenReg++;}
            else{if(big)a.normalBig++;else a.normalReg++;}

            boolean fullGod=playOrdinaryBonusTree(a,type);
            if(fullGod){enterHeaven(a);continue;}
            if(originHeaven){
                if(rng.nextLong(1_000_000)<700_000L){a.heavenContinues++;enterHeaven(a);}
                else{a.mode=Mode.NORMAL;a.heavenTarget=0;a.heavenProgress=0;}
            }else{
                long ppm=big?60_000L:30_000L;
                if(rng.nextLong(1_000_000)<ppm)enterHeaven(a);
            }
        }
        return new Result(setting,games,a.lever,a.paid,a.god,a.godInGod,a.normalBig,a.normalReg,a.heavenBig,a.heavenReg,
                a.godBig,a.godReg,a.heavenEntries,a.heavenContinues,a.bet,a.payout,100.0*a.payout/a.bet,a.payout-a.bet);
    }

    /** Returns true when an ordinary/stock bonus was replaced by a full GOD. */
    private static boolean playOrdinaryBonusTree(Acc a,String initial){
        ArrayDeque<String> queue=new ArrayDeque<>();queue.addLast(initial);
        while(!queue.isEmpty()){
            String type=queue.removeFirst();
            a.bet+=FixedGameRules.ENTRY_BET;
            int rounds=games(type);
            for(int i=0;i<rounds;i++){
                a.bet+=FixedGameRules.BONUS_BET;a.payout+=14;
                if(a.rng.nextInt(GOD_DENOMINATOR)==0){
                    a.god++;a.payout+=GameRules.payout(InternalRole.GOD);
                    // Production: discard interrupted remainder, preserve already-earned stock,
                    // add one BIG compensation, then start a full parent GOD.
                    queue.addFirst("BIG");
                    playGod(a,queue);
                    return true;
                }
                InternalRole hit=a.weights.drawJugglerGod(a.setting,a.rng,a.bonusScale,a.smallScale);
                String next=GameRules.bonus(hit);
                if(next!=null)queue.addLast(next);
            }
        }
        return false;
    }

    private static void playGod(Acc a,ArrayDeque<String> carriedStock){
        // Parent #1 starts directly from BAR GOD: no entry bet or normal bet.
        playGodBonus(a,"BIG",true);
        while(!carriedStock.isEmpty())playGodStock(a,carriedStock.removeFirst());

        for(int seed=1;seed<GUARANTEED_BIGS;seed++){
            a.bet+=FixedGameRules.NORMAL_BET;
            playGodBonus(a,"BIG",false);
        }
        int rate=CONTINUATION[a.setting];
        while(a.rng.nextInt(100)<rate){
            a.lever++;a.paid++;a.bet+=FixedGameRules.NORMAL_BET;
            playGodBonus(a,"BIG",false);
        }
    }

    private static void playGodBonus(Acc a,String type,boolean firstParent){
        if(!firstParent)a.bet+=FixedGameRules.ENTRY_BET;
        ArrayDeque<String> stock=new ArrayDeque<>();
        playGodRounds(a,type,stock);
        if("BIG".equals(type))a.godBig++;else a.godReg++;
        while(!stock.isEmpty())playGodStock(a,stock.removeFirst());
    }

    private static void playGodStock(Acc a,String type){
        a.bet+=FixedGameRules.ENTRY_BET;
        ArrayDeque<String> stock=new ArrayDeque<>();
        playGodRounds(a,type,stock);
        if("BIG".equals(type))a.godBig++;else a.godReg++;
        while(!stock.isEmpty())playGodStock(a,stock.removeFirst());
    }

    private static void playGodRounds(Acc a,String type,ArrayDeque<String> stock){
        for(int i=0;i<games(type);i++){
            a.bet+=FixedGameRules.BONUS_BET;a.payout+=14;
            if(a.rng.nextInt(GOD_DENOMINATOR)==0){
                a.godInGod++;a.payout+=GameRules.payout(InternalRole.GOD);
                for(int n=0;n<GOD_IN_GOD_BIG_STOCK;n++)stock.addLast("BIG");
                continue;
            }
            InternalRole hit=a.weights.drawJugglerGod(a.setting,a.rng,a.bonusScale,a.smallScale);
            String next=GameRules.bonus(hit);
            if(next!=null)stock.addLast(next);
        }
    }

    private static int games(String type){return ("BIG".equals(type)?BIG_PAYOUT:REG_PAYOUT)/14;}
    private static void enterHeaven(Acc a){a.mode=Mode.HEAVEN;a.heavenTarget=a.rng.nextInt(32)+1;a.heavenProgress=0;a.heavenEntries++;}
    private JugglerGodExtremeSimulator(){}
}
