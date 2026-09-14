package jp.pirijuggler.paper.reel;
public enum DisplayRole {
    GRAPE(750), BELL(50), PIERO(20), REPLAY(525), CHERRY(1510), MISS(5242), BONUS(5250), BIG_ENTRY(10), REG_ENTRY(10), PREMIUM_B(1);
    private final int minimum;DisplayRole(int minimum){this.minimum=minimum;}public int minimum(){return minimum;}
}
