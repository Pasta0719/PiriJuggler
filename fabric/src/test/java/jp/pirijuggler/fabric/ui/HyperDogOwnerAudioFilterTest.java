package jp.pirijuggler.fabric.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogOwnerAudioFilterTest {
    @Test void removeOnlySeatedDogsOwnBet(){
        assertTrue(HyperDogOwnerAudioFilter.suppressOwnBet(true,10,10,"bet"));
        assertFalse(HyperDogOwnerAudioFilter.suppressOwnBet(true,10,11,"bet"));
        assertFalse(HyperDogOwnerAudioFilter.suppressOwnBet(false,10,10,"bet"));
        assertFalse(HyperDogOwnerAudioFilter.suppressOwnBet(true,10,10,"lever"));
        assertFalse(HyperDogOwnerAudioFilter.suppressOwnBet(true,10,10,"bonus_start"));
    }
}
