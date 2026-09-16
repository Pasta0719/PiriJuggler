package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.ReelMotion;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReelTimingGateTest {
    @Test void stopUnlocksTwoHundredMillisecondsAfterFullSpeed(){
        assertEquals(700,ReelMotion.Profile.NORMAL.clientDelayMs());
        assertEquals(650,ReelMotion.Profile.NORMAL.serverThresholdMs());
        assertEquals(1000,ReelMotion.Profile.REVERSE_500MS.clientDelayMs());
        assertEquals(950,ReelMotion.Profile.REVERSE_500MS.serverThresholdMs());
        assertEquals(200,ReelMotion.Profile.RESUME_NORMAL.clientDelayMs());
        assertEquals(150,ReelMotion.Profile.RESUME_NORMAL.serverThresholdMs());

        var time=new AtomicLong();
        var view=SlotViewStateTest.open(time);
        var start=SlotViewStateTest.start().payload();
        start.addProperty("stopEnableAfterMs",ReelMotion.Profile.NORMAL.clientDelayMs());
        view.receive(Envelope.current(PacketType.SPIN_START,start));
        time.set(699_999_999L);assertFalse(view.canSend(PacketType.STOP_LEFT));
        time.set(700_000_000L);assertTrue(view.canSend(PacketType.STOP_LEFT));
    }

    @Test void nextGameWaitIsOnlyTheRemainderOfTwoSecondsFromPreviousSpinStart(){
        var time=new AtomicLong();
        var view=SlotViewStateTest.open(time);
        view.receive(SlotViewStateTest.start());
        time.set(1_200_000_000L);
        settle(view);
        assertEquals(800_000_000L,view.nextGameRemainingNanos());
        assertTrue(view.shouldQueueLever());assertFalse(view.queuedLeverReady());
        time.set(1_999_999_999L);assertTrue(view.shouldQueueLever());assertFalse(view.queuedLeverReady());
        time.set(2_000_000_000L);assertFalse(view.shouldQueueLever());assertTrue(view.queuedLeverReady());

        var lateTime=new AtomicLong();
        var late=SlotViewStateTest.open(lateTime);
        late.receive(SlotViewStateTest.start());
        lateTime.set(2_100_000_000L);
        settle(late);
        assertEquals(0,late.nextGameRemainingNanos());
        assertFalse(late.shouldQueueLever());assertTrue(late.queuedLeverReady());
    }

    private static void settle(SlotViewState view){
        view.receive(SlotViewStateTest.packet(PacketType.REEL_STOP,"{\"spinId\":\""+SlotViewStateTest.SPIN+"\",\"reel\":\"LEFT\",\"stopIndex\":8,\"durationMs\":0}"));
        view.receive(SlotViewStateTest.packet(PacketType.REEL_STOP,"{\"spinId\":\""+SlotViewStateTest.SPIN+"\",\"reel\":\"CENTER\",\"stopIndex\":3,\"durationMs\":0}"));
        view.receive(SlotViewStateTest.packet(PacketType.REEL_STOP,"{\"spinId\":\""+SlotViewStateTest.SPIN+"\",\"reel\":\"RIGHT\",\"stopIndex\":12,\"durationMs\":0}"));
        view.receive(SlotViewStateTest.packet(PacketType.PUBLIC_STATE,"{\"sessionId\":\""+SlotViewStateTest.ID+"\",\"machineId\":1,\"gameState\":\"NORMAL_BETTED\",\"lampOn\":false,\"displayStops\":{\"left\":8,\"center\":3,\"right\":12}}"));
    }
}
