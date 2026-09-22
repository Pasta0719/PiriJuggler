package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.config.FixedGameRules;
import jp.pirijuggler.paper.reel.InternalRole;

public final class GameRules {
    public record Balance(int credit,long held) {
        public Balance { if(credit<0||credit>50||held<0)throw new IllegalArgumentException("Invalid session balance"); }
        public Balance payout(long amount) {
            if(amount<0)throw new IllegalArgumentException("Negative payout");
            int toCredit=(int)Math.min(amount,50-credit);
            return new Balance(credit+toCredit,Math.addExact(held,amount-toCredit));
        }
        public Bet bet(int cost) {
            if(cost<1||cost>3)throw new IllegalArgumentException("BET cost");
            int move=(int)Math.min(Math.max(0,cost-credit),held);int available=credit+move;
            return new Bet(new Balance(available>=cost?available-cost:available,held-move),available>=cost);
        }
    }
    public record Bet(Balance balance,boolean accepted) {}
    public static int payout(InternalRole role) {
        return switch(role){case GRAPE->8;case CHERRY,CHERRY_BIG,CHERRY_REG->2;
            case GOD->15;case BELL,PIERO,PIERO_BIG,PIERO_REG->14;default->0;};
    }
    public static String bonus(InternalRole role) {
        return switch(role){case GOD,BIG,CHERRY_BIG,PIERO_BIG->"BIG";case REG,CHERRY_REG,PIERO_REG->"REG";default->null;};
    }
    public static int bonusGross(String type) {return switch(type){case "BIG"->FixedGameRules.BIG_PAYOUT;case "REG"->FixedGameRules.REG_PAYOUT;default->throw new IllegalArgumentException("Bonus type");};}
    public static int bonusGames(String type) {return bonusGross(type)/14;}
    public static int bonusTotalBet(String type) {return FixedGameRules.ENTRY_BET+FixedGameRules.BONUS_BET*bonusGames(type);}
    private GameRules() {}
}
