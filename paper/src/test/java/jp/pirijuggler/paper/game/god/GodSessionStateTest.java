package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodSessionStateTest {
    @Test
    void initialStateRoundTripsAsJson() {
        GodSessionState initial = GodSessionState.initial();
        GodSessionState decoded = GodSessionState.fromJson(initial.toJson());
        assertEquals(initial, decoded);
        assertEquals(GodPhase.NORMAL, decoded.phase());
        assertEquals(GodFrontMode.LOW_A, decoded.frontMode());
    }

    @Test
    void rejectsNegativeCounters() {
        assertThrows(IllegalArgumentException.class, () -> new GodSessionState(
                GodPhase.NORMAL, GodFrontMode.LOW_A, -1, 0, 0, null,
                0, 0, 0, 0, 0, 0, 0));
    }
}
