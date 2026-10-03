package jp.pirijuggler.common.reel;

/** One draw, including the six visible one-medal BIG overlaps. No hidden A/B symbols. */
public enum SkillStopRole {
    MISS(0, null), REPLAY(1, null), GRAPE(2, null), BELL(4, null), CHERRY(0, null), PIERO(8, null),
    BIG(0, "BIG"), REG(0, "REG"), CHERRY_BIG(0, "BIG"), CHERRY_REG(0, "REG"),
    PIERO_BIG(8, "BIG"), PIERO_REG(8, "REG"),
    ONE_A(128, "BIG"), ONE_B(256, "BIG"), ONE_CD(512, "BIG"),
    ONE_E(1024, "BIG"), ONE_F(2048, "BIG"), ONE_H(4096, "BIG");
    private final int pattern;
    private final String bonus;
    SkillStopRole(int pattern, String bonus) { this.pattern = pattern; this.bonus = bonus; }
    public int pattern() { return pattern; }
    public String bonus() { return bonus; }
    public boolean cherry() { return this == CHERRY || this == CHERRY_BIG || this == CHERRY_REG; }
    public boolean oneMedal() { return pattern >= 128; }
    public boolean bigFamily() { return "BIG".equals(bonus); }
}
