package jp.pirijuggler.paper.machine;

/** Pure distance policy for remote-machine interest management. */
final class RemoteInterestPolicy {
    static final double ENTER_RADIUS = 32.0;
    static final double EXIT_RADIUS = 34.0;
    private static final double ENTER_SQUARED = ENTER_RADIUS * ENTER_RADIUS;
    private static final double EXIT_SQUARED = EXIT_RADIUS * EXIT_RADIUS;

    private RemoteInterestPolicy() {}

    static boolean contains(double distanceSquared, boolean currentlyInterested) {
        return distanceSquared <= (currentlyInterested ? EXIT_SQUARED : ENTER_SQUARED);
    }
}
