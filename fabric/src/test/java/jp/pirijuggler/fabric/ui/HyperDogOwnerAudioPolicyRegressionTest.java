package jp.pirijuggler.fabric.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogOwnerAudioPolicyRegressionTest {
    @Test void everyTypeOfOwnHallAudioSuppressedWithoutMutingNeighbors(){
        for(String type:new String[]{"bet","lever","stop","tenpai","notice","payout","bonus_start",
                "big_bgm","reg_bgm","hyper_dog_loop_upper"}){
            assertTrue(HyperDogOwnerAudioFilter.suppressOwnAudio(true,42,42),type);
            assertFalse(HyperDogOwnerAudioFilter.suppressOwnAudio(true,42,41),type);
            assertFalse(HyperDogOwnerAudioFilter.suppressOwnAudio(false,42,42),type);
        }
        assertTrue(HyperDogOwnerAudioFilter.suppressOwnBet(true,42,42,"bet"));
        assertFalse(HyperDogOwnerAudioFilter.suppressOwnBet(true,42,42,"lever"));
    }
}
