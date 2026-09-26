package jp.pirijuggler.paper.machine;

/**
 * Persistent machine-family identifier.
 *
 * Only JUGGLER is active in the current schema. The remaining identifiers reserve
 * stable names for the upcoming engines so protocol/database code does not depend
 * on Java class names.
 */
public enum MachineType {
    JUGGLER,
    JUGGLER_GOD,
    JUGGLER_GOD_EXTREME,
    OKIDOKI,
    GOD,
    DISC
}
