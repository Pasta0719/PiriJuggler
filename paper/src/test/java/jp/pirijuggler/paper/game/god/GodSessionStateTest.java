package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodSessionStateTest {
    @Test
    void initialStateRoundTripsAsJson() {
        GodSessionState initial=GodSessionState.initial();
        assertEquals(initial,GodSessionState.fromJson(initial.toJson()));
        assertEquals(GodPhase.NORMAL,initial.phase());
        assertEquals("READY",initial.lastEvent());
    }

    @Test
    void rejectsNegativeCounters() {
        assertThrows(IllegalArgumentException.class,()->new GodSessionState(
                GodPhase.NORMAL,-1,0,null,0,0,0,0,0,0,0,0,"READY","NONE"));
    }
}
