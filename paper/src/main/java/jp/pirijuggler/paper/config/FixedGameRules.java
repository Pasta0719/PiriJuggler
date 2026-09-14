package jp.pirijuggler.paper.config;

/** SPEC chapter 103: these values cannot be changed through config.yml. */
public final class FixedGameRules {
    public static final int CREDIT_MAX = 50;
    public static final int NORMAL_BET = 3;
    public static final int ENTRY_BET = 1;
    public static final int BONUS_BET = 2;
    public static final int BIG_THRESHOLD = 266;
    public static final int BIG_PAYOUT = 280;
    public static final int REG_THRESHOLD = 98;
    public static final int REG_PAYOUT = 112;
    public static final int MAX_MEDAL_BUNDLE = 500;

    private FixedGameRules() { }
}
