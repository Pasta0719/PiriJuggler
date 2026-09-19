package jp.pirijuggler.paper.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RemoteMachineSyncTest {
    @Test void newInterestUsesLockedThirtyTwoBlockRadius() {
        assertTrue(RemoteInterestPolicy.contains(32.0 * 32.0, false));
        assertFalse(RemoteInterestPolicy.contains(Math.nextUp(32.0 * 32.0), false));
    }

    @Test void existingInterestHasTwoBlockBoundaryHysteresis() {
        assertTrue(RemoteInterestPolicy.contains(32.5 * 32.5, true));
        assertTrue(RemoteInterestPolicy.contains(34.0 * 34.0, true));
        assertFalse(RemoteInterestPolicy.contains(Math.nextUp(34.0 * 34.0), true));
    }

    @Test void leavingAndReenteringDoesNotFlapAtThirtyTwoBoundary() {
        assertTrue(RemoteInterestPolicy.contains(31.99 * 31.99, false));
        assertTrue(RemoteInterestPolicy.contains(32.01 * 32.01, true));
        assertTrue(RemoteInterestPolicy.contains(33.99 * 33.99, true));
        assertFalse(RemoteInterestPolicy.contains(34.01 * 34.01, true));
        assertFalse(RemoteInterestPolicy.contains(33.0 * 33.0, false));
        assertTrue(RemoteInterestPolicy.contains(31.99 * 31.99, false));
    }
}
