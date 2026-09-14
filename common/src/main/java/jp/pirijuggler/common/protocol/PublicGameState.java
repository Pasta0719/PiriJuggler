package jp.pirijuggler.common.protocol;

/** Wire states. Bonus identity is absent until the entry result is visible. */
public enum PublicGameState {
    SEATED_READY, NORMAL_BETTED, NORMAL_SPINNING, REPLAY_READY,
    BONUS_PENDING, BONUS_ENTRY_BETTED, BONUS_ENTRY_SPINNING,
    BIG_READY, BIG_BETTED, BIG_SPINNING, REG_READY, REG_BETTED, REG_SPINNING
}
