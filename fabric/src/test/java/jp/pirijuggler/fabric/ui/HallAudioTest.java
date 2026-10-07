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
    @Test void perMachineSoundSourcesMatchOwnerResolution() {
        java.util.function.Predicate<String> all=name->true;
        java.util.function.Predicate<String> none=name->false;

        assertEquals("bet",HallAudio.resolveLogical("JUGGLER","bet",all));
        assertEquals("bet",HallAudio.resolveLogical("SKILL_STOP","bet",all));

        assertEquals("juggler_god_bet",HallAudio.resolveLogical("JUGGLER_GOD","bet",all));
        assertEquals("juggler_god_bet",HallAudio.resolveLogical("JUGGLER_GOD_EXTREME","bet",all));
        assertEquals("bet",HallAudio.resolveLogical("JUGGLER_GOD","bet",none));
        assertEquals("bet",HallAudio.resolveLogical("JUGGLER_GOD_EXTREME","bet",none));

        assertEquals("juggler_god_god_freeze",HallAudio.resolveLogical("JUGGLER_GOD","god_freeze",all));
        assertEquals("god_freeze",HallAudio.resolveLogical("JUGGLER_GOD","god_freeze",none));
        assertEquals("juggler_god_god_stop_1",HallAudio.resolveLogical("JUGGLER_GOD","god_stop_1",all));
        assertEquals("stop",HallAudio.resolveLogical("JUGGLER_GOD","god_stop_1",none));
        assertEquals("juggler_god_god_bonus_start",HallAudio.resolveLogical("JUGGLER_GOD","god_bonus_start",all));
        assertEquals("bonus_start",HallAudio.resolveLogical("JUGGLER_GOD","god_bonus_start",none));
        assertEquals("juggler_god_god_bonus_end",HallAudio.resolveLogical("JUGGLER_GOD_EXTREME","god_bonus_end",all));
        assertEquals("bonus_end",HallAudio.resolveLogical("JUGGLER_GOD_EXTREME","god_bonus_end",none));
        assertEquals("juggler_god_god_big_bgm",HallAudio.resolveLogical("JUGGLER_GOD_EXTREME","god_big_bgm",all));
        assertEquals("big_bgm",HallAudio.resolveLogical("JUGGLER_GOD_EXTREME","god_big_bgm",none));
    }
    @Test void allFourMachinesResolveEveryOwnerAudioSourceIdentically() {
        var base=java.util.List.of("notice","notice_strong","tenpai","bet","lever","stop","payout","error","bonus_start","bonus_end","big_bgm","reg_bgm");
        java.util.function.Predicate<String> all=name->true;
        java.util.function.Predicate<String> none=name->false;
        for(String sound:base){
            assertEquals(sound,HallAudio.resolveLogical("JUGGLER",sound,all),sound);
            assertEquals(sound,HallAudio.resolveLogical("SKILL_STOP",sound,all),sound);
            assertEquals("juggler_god_"+sound,HallAudio.resolveLogical("JUGGLER_GOD",sound,all),sound);
            assertEquals("juggler_god_"+sound,HallAudio.resolveLogical("JUGGLER_GOD_EXTREME",sound,all),sound);
            assertEquals(sound,HallAudio.resolveLogical("JUGGLER_GOD",sound,none),sound);
            assertEquals(sound,HallAudio.resolveLogical("JUGGLER_GOD_EXTREME",sound,none),sound);
        }
    }

    @Test void distanceAttenuationIsGentlerButKeepsSameCutoff() {
        assertEquals(1.0f,HallAudio.distanceGain(0,HallAudio.SE_RADIUS),1e-6f);
        assertEquals(.75f,HallAudio.distanceGain(8,HallAudio.SE_RADIUS),1e-6f);
        assertEquals(0f,HallAudio.distanceGain(16,HallAudio.SE_RADIUS),1e-6f);
        assertEquals(0f,HallAudio.distanceGain(17,HallAudio.SE_RADIUS),1e-6f);
        assertTrue(HallAudio.distanceGain(8,HallAudio.SE_RADIUS)>.5f);
        assertEquals(.55f,HallAudio.WALL_OCCLUSION,1e-6f);
    }
    @Test void phase14VolumesAreLocked() {
        assertEquals(.35f,HallAudio.NORMAL_VOLUME);
        assertEquals(.45f,HallAudio.NOTICE_VOLUME);
        assertEquals(.18f,HallAudio.BGM_VOLUME);
    }
}
