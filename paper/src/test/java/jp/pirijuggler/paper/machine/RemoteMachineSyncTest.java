package jp.pirijuggler.paper.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RemoteMachineSyncTest {
    @Test void newInterestUsesLockedThirtyTwoBlockRadius() {
        assertTrue(RemoteMachineSync.interestContains(32.0 * 32.0, false));
        assertFalse(RemoteMachineSync.interestContains(Math.nextUp(32.0 * 32.0), false));
    }

    @Test void existingInterestHasTwoBlockBoundaryHysteresis() {
        assertTrue(RemoteMachineSync.interestContains(32.5 * 32.5, true));
        assertTrue(RemoteMachineSync.interestContains(34.0 * 34.0, true));
        assertFalse(RemoteMachineSync.interestContains(Math.nextUp(34.0 * 34.0), true));
    }

    @Test void leavingAndReenteringDoesNotFlapAtThirtyTwoBoundary() {
        assertTrue(RemoteMachineSync.interestContains(31.99 * 31.99, false));
        assertTrue(RemoteMachineSync.interestContains(32.01 * 32.01, true));
        assertTrue(RemoteMachineSync.interestContains(33.99 * 33.99, true));
        assertFalse(RemoteMachineSync.interestContains(34.01 * 34.01, true));
        assertFalse(RemoteMachineSync.interestContains(33.0 * 33.0, false));
        assertTrue(RemoteMachineSync.interestContains(31.99 * 31.99, false));
    }
}
