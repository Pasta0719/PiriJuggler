package jp.pirijuggler.paper.reel;
/** Server-only draw outcomes and their normal-game visuals (§7/14/24/26). */
public enum InternalRole {
    MISS, REPLAY, GRAPE, CHERRY, BELL, PIERO, BIG, REG, CHERRY_BIG, CHERRY_REG, PIERO_BIG, PIERO_REG;
    public DisplayRole display(boolean premiumB){
        if(premiumB){if(this!=CHERRY_BIG)throw new IllegalArgumentException("Premium B requires CHERRY_BIG");return DisplayRole.PREMIUM_B;}
        return switch(this){case BIG,REG->DisplayRole.BONUS;case MISS->DisplayRole.MISS;case CHERRY,CHERRY_BIG,CHERRY_REG->DisplayRole.CHERRY;case PIERO,PIERO_BIG,PIERO_REG->DisplayRole.PIERO;case GRAPE->DisplayRole.GRAPE;case REPLAY->DisplayRole.REPLAY;case BELL->DisplayRole.BELL;};
    }
    public boolean premiumFEligible(){return this==BIG||this==CHERRY_BIG||this==PIERO_BIG;}
}
