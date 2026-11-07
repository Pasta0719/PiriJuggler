package jp.pirijuggler.paper.reel;

import jp.pirijuggler.common.reel.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogRoundTest {
    @Test void stopRoundCanResumeAfterPartialStopForEveryInternalRole() {
        for(var role:HyperDogReelControl.Role.values()) {
            var round=new HyperDogRound(UUID.randomUUID(),role);
            if(role==HyperDogReelControl.Role.CZ_WIN||role==HyperDogReelControl.Role.CZ_LOSE) {
                var awaitingOuter=round;
                assertThrows(IllegalStateException.class,()->awaitingOuter.stopManual(Reel.CENTER,4,1));
                round.stopAutomatic(Reel.LEFT,4,1);
                round=HyperDogRound.restore(round.snapshot());
                round.stopAutomatic(Reel.RIGHT,17,2);
                round.stopManual(Reel.CENTER,13,3);
            } else if(role==HyperDogReelControl.Role.AT_ENTRY_RED7) {
                var auto=round;
                assertThrows(IllegalStateException.class,()->auto.stopManual(Reel.LEFT,4,1));
                round.stopAutomatic(Reel.RIGHT,4,1);
                round=HyperDogRound.restore(round.snapshot());
                round.stopAutomatic(Reel.CENTER,17,2);
                round.stopAutomatic(Reel.LEFT,13,3);
            } else {
                round.stopManual(Reel.RIGHT,4,1);
                round=HyperDogRound.restore(round.snapshot());
                var saved=round;
                assertThrows(IllegalArgumentException.class,()->saved.stopManual(Reel.RIGHT,4,1));
                round.stopManual(Reel.CENTER,17,2);
                round.stopManual(Reel.LEFT,13,3);
            }
            assertTrue(round.complete(),role.name());
            assertTrue(HyperDogReelControl.legalFinal(role,round.snapshot().history().stops()),role.name());
        }
    }
    @Test void persistedSpinIdAndInputsSurviveRoundTrip() {
        UUID spinId=UUID.randomUUID();
        var round=new HyperDogRound(spinId,HyperDogReelControl.Role.GRAPE);
        round.stopManual(Reel.LEFT,12,101);
        var state=round.snapshot();
        var resumed=HyperDogRound.restore(state);
        assertEquals(spinId,resumed.spinId());
        assertEquals(101,resumed.snapshot().lastSequence());
        assertEquals(12,resumed.snapshot().history().input(Reel.LEFT.ordinal()));
        assertThrows(IllegalArgumentException.class,()->resumed.stopManual(Reel.CENTER,5,101));
        resumed.stopManual(Reel.CENTER,5,102);
        resumed.stopManual(Reel.RIGHT,8,103);
        assertTrue(resumed.complete());
        assertThrows(IllegalStateException.class,()->resumed.stopManual(Reel.LEFT,4,104));
    }
}
