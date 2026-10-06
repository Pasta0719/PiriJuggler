package jp.pirijuggler.fabric.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HallAudioTest {
    @Test void positionalSeUsesSixteenBlockRadius() {
        assertTrue(HallAudio.withinRadius(16,0,0,HallAudio.SE_RADIUS));
        assertFalse(HallAudio.withinRadius(Math.nextUp(16.0),0,0,HallAudio.SE_RADIUS));
    }
    @Test void bonusBgmUsesTwelveBlockRadius() {
        assertTrue(HallAudio.withinRadius(0,0,12,HallAudio.BGM_RADIUS));
        assertFalse(HallAudio.withinRadius(0,0,Math.nextUp(12.0),HallAudio.BGM_RADIUS));
    }
    @Test void phase14VolumesAreLocked() {
        assertEquals(.35f,HallAudio.NORMAL_VOLUME);
        assertEquals(.45f,HallAudio.NOTICE_VOLUME);
        assertEquals(.18f,HallAudio.BGM_VOLUME);
    }
}
