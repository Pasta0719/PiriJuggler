package jp.pirijuggler.fabric.ui;
import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SlotViewStateTest {
    @Test void fractionalStopsEndExactlyAtEveryServerIndexIncludingZero(){
        for(int target=0;target<21;target++)for(int step=1;step<210;step++){
            var time=new AtomicLong();var view=open(time);var b=start().payload();b.getAsJsonObject("startPhase").addProperty("left",step/10.0);view.receive(Envelope.current(PacketType.SPIN_START,b));
            view.receive(packet(PacketType.REEL_STOP,"{\"spinId\":\""+SPIN+"\",\"reel\":\"LEFT\",\"stopIndex\":"+target+",\"durationMs\":380}"));
            long minMs=jp.pirijuggler.common.reel.ReelMotion.visualDurationMs(step/10.0,jp.pirijuggler.common.reel.ReelMotion.normalStopEndpoint(step/10.0,target),380);
            time.set(minMs*1_000_000L);assertEquals((double)target,view.phase(0));time.set((minMs+500)*1_000_000L);assertEquals((double)target,view.phase(0));
        }
    }
    @Test void downwardStopWrapsAcrossZeroWithoutTakingTheOppositeShortcut(){
        var time=new AtomicLong();var view=open(time);var b=start().payload();b.getAsJsonObject("startPhase").addProperty("left",.5);view.receive(Envelope.current(PacketType.SPIN_START,b));
        view.receive(packet(PacketType.REEL_STOP,"{\"spinId\":\""+SPIN+"\",\"reel\":\"LEFT\",\"stopIndex\":20,\"durationMs\":200}"));
        time.set(100_000_000);assertEquals(20.75,view.phase(0),1e-9);time.set(200_000_000);assertEquals(20,view.phase(0),1e-9);
    }
    @Test void lateStopPacketDoesNotReverseOrSprintThroughAFullWrap(){
        var time=new AtomicLong();var view=open(time);var b=start().payload();b.getAsJsonObject("startPhase").addProperty("left",20.1);view.receive(Envelope.current(PacketType.SPIN_START,b));
        view.receive(packet(PacketType.REEL_STOP,"{\"spinId\":\""+SPIN+"\",\"reel\":\"LEFT\",\"stopIndex\":0,\"durationMs\":80}"));
        time.set(80_000_000);double after80=view.phase(0);assertTrue(after80<20.1&&after80>18.5,"must continue downward without a visual sprint");
        time.set(1_117_000_000L);assertEquals(0,view.phase(0),1e-9);
    }
    static final String ID="00000000-0000-0000-0000-000000000001",SPIN="00000000-0000-0000-0000-000000000002";
    static Envelope packet(PacketType type,String json){return Envelope.current(type,JsonParser.parseString(json).getAsJsonObject());}
    static SlotViewState open(AtomicLong clock){var view=new SlotViewState(clock::get);view.receive(packet(PacketType.OPEN_MACHINE,"{\"sessionId\":\""+ID+"\",\"machineId\":1}"));return view;}
    static Envelope start(){return packet(PacketType.SPIN_START,"{\"sessionId\":\""+ID+"\",\"machineId\":1,\"spinId\":\""+SPIN+"\",\"animation\":\"NORMAL\",\"startPhase\":{\"left\":8,\"center\":3,\"right\":12}}");}
    @Test void motionBoundariesAreContinuousAndRespectReceptionTime(){
        assertEquals(0,SlotViewState.distance("NORMAL",.150));assertEquals(-3.15,SlotViewState.distance("NORMAL",.5),1e-9);assertEquals(-12.15,SlotViewState.distance("NORMAL",1),1e-9);
        assertEquals(6,SlotViewState.distance("REVERSE_500MS",.5),1e-9);assertEquals(3.3,SlotViewState.distance("REVERSE_500MS",.8),1e-9);assertEquals(-18,SlotViewState.distance("RESUME_NORMAL",1));
        var time=new AtomicLong(9_000_000_000L);var view=open(time);view.receive(start());assertEquals(8,view.phase(0));time.addAndGet(150_000_000);assertEquals(8,view.phase(0));time.addAndGet(350_000_000);assertEquals(4.85,view.phase(0),1e-9);
    }
    @Test void ServerStopIndexWinsIncludingSixSlipAndWrongSpinIsIgnored(){
        var time=new AtomicLong();var view=open(time);view.receive(start());
        view.receive(packet(PacketType.REEL_STOP,"{\"spinId\":\"00000000-0000-0000-0000-000000000099\",\"reel\":\"LEFT\",\"stopIndex\":1,\"durationMs\":0}"));assertEquals(8,view.phase(0));
        view.receive(packet(PacketType.REEL_STOP,"{\"spinId\":\""+SPIN+"\",\"reel\":\"LEFT\",\"pressedIndex\":8,\"stopIndex\":2,\"slip\":6,\"durationMs\":380}"));time.set(190_000_000);assertEquals(5,view.phase(0),1e-9);time.set(380_000_000);assertEquals(2,view.phase(0),1e-9);time.set(9_000_000_000L);assertEquals(2,view.phase(0),1e-9);
    }
    @Test void publicAssetsRemainAuthoritativeAndNoticeBlinkUsesOneSecondWindow(){
        var time=new AtomicLong();var view=open(time);view.receive(start());view.receive(packet(PacketType.NOTICE,"{\"spinId\":\""+SPIN+"\",\"lamp\":\"ON\",\"pattern\":\"FAST_BLINK_1S\"}"));assertTrue(view.lampOn());time.set(100_000_000);assertFalse(view.lampOn());time.set(1_000_000_000);assertTrue(view.lampOn());
        assertEquals("—",view.value("credit"));view.receive(packet(PacketType.PUBLIC_STATE,"{\"sessionId\":\""+ID+"\",\"machineId\":1,\"gameState\":\"REPLAY_READY\",\"credit\":32,\"bet\":3,\"pay\":0,\"heldMedals\":442,\"lampOn\":false,\"displayStops\":{\"left\":3,\"center\":3,\"right\":3}}"));assertEquals("32",view.value("credit"));assertEquals("3",view.value("bet"));assertEquals(3,view.phase(0));assertFalse(view.lampOn());
        var copy=view.publicState();copy.addProperty("credit",0);assertEquals("32",view.value("credit"));
    }
    @Test void resumedDisplayKeepsAlreadyStoppedReelsFixed(){
        var time=new AtomicLong();var view=open(time);
        view.receive(packet(PacketType.PUBLIC_STATE,"{\"sessionId\":\""+ID+"\",\"machineId\":1,\"gameState\":\"NORMAL_SPINNING\",\"stoppedMask\":1,\"lampOn\":false,\"displayStops\":{\"left\":14,\"center\":3,\"right\":12}}"));
        var b=start().payload();b.addProperty("animation","RESUME_NORMAL");view.receive(Envelope.current(PacketType.SPIN_START,b));time.set(1_000_000_000);assertEquals(14,view.phase(0));assertEquals(6,view.phase(1),1e-9);assertEquals(15,view.phase(2),1e-9);
    }
    @Test void clientStopDelayUsesEachServerMotionProfile(){
        for(var profile:jp.pirijuggler.common.reel.ReelMotion.Profile.values()){
            var time=new AtomicLong();var view=open(time);var b=start().payload();b.addProperty("animation",profile.name());b.addProperty("stopEnableAfterMs",profile.clientDelayMs());view.receive(Envelope.current(PacketType.SPIN_START,b));
            time.set(profile.clientDelayMs()*1_000_000L-1);assertFalse(view.canSend(PacketType.STOP_LEFT));assertFalse(view.canSend(PacketType.SPACE_ACTION));assertTrue(view.canSend(PacketType.CLOSE_REQUEST));time.incrementAndGet();assertTrue(view.canSend(PacketType.STOP_LEFT));
        }
    }
}
