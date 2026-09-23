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
        int stopMs=jp.pirijuggler.common.reel.ReelMotion.visualDurationMs(20.1,0,80);
        time.set(80_000_000);double after80=view.phase(0);double expected=20.1*(1-80.0/stopMs);assertEquals(expected,after80,1e-9,"must continue downward at the configured normal-speed cap");
        time.set(stopMs*1_000_000L);assertEquals(0,view.phase(0),1e-9);
    }
    static final String ID="00000000-0000-0000-0000-000000000001",SPIN="00000000-0000-0000-0000-000000000002";
    static Envelope packet(PacketType type,String json){return Envelope.current(type,JsonParser.parseString(json).getAsJsonObject());}
    static SlotViewState open(AtomicLong clock){var view=new SlotViewState(clock::get);view.receive(packet(PacketType.OPEN_MACHINE,"{\"sessionId\":\""+ID+"\",\"machineId\":1}"));return view;}
    static JsonObject hints(){var all=new JsonObject();for(String name:new String[]{"left","center","right"}){var a=new JsonArray();for(int p=0;p<21;p++){var h=new JsonObject();h.addProperty("stopIndex",p);h.addProperty("slip",0);h.addProperty("durationMs",80);a.add(h);}all.add(name,a);}return all;}
    static Envelope start(){
        var b=JsonParser.parseString("{\"sessionId\":\""+ID+"\",\"machineId\":1,\"spinId\":\""+SPIN+"\",\"animation\":\"NORMAL\",\"startPhase\":{\"left\":8,\"center\":3,\"right\":12}}").getAsJsonObject();
        b.add("stopHints",hints());return Envelope.current(PacketType.SPIN_START,b);
    }
    @Test void godUsesAuthoritativeTwentyStopServerIndices(){
        var time=new AtomicLong();
        var view=new SlotViewState(time::get);
        view.receive(packet(PacketType.OPEN_MACHINE,"{\"sessionId\":\""+ID+"\",\"machineId\":1,\"machineType\":\"GOD\"}"));
        var b=start().payload();
        b.addProperty("machineType","GOD");
        b.getAsJsonObject("startPhase").addProperty("left",19.5);
        view.receive(Envelope.current(PacketType.SPIN_START,b));
        view.receive(packet(PacketType.REEL_STOP,"{\"spinId\":\""+SPIN+"\",\"reel\":\"LEFT\",\"pressedIndex\":19,\"stopIndex\":17,\"slip\":2,\"durationMs\":80}"));
        time.set(1_000_000_000L);
        assertEquals(17.0,view.phase(0),1e-9);
    }

    @Test void motionBoundariesAreContinuousAndRespectReceptionTime(){
        assertEquals(0,SlotViewState.distance("NORMAL",.150));assertEquals(-3.675,SlotViewState.distance("NORMAL",.5),1e-9);assertEquals(-14.175,SlotViewState.distance("NORMAL",1),1e-9);
        assertEquals(6,SlotViewState.distance("REVERSE_500MS",.5),1e-9);assertEquals(2.85,SlotViewState.distance("REVERSE_500MS",.8),1e-9);assertEquals(-21,SlotViewState.distance("RESUME_NORMAL",1));
        var time=new AtomicLong(9_000_000_000L);var view=open(time);view.receive(start());assertEquals(8,view.phase(0));time.addAndGet(150_000_000);assertEquals(8,view.phase(0));time.addAndGet(350_000_000);assertEquals(4.325,view.phase(0),1e-9);
    }
    @Test void ServerStopIndexWinsIncludingSixSlipAndWrongSpinIsIgnored(){
        var time=new AtomicLong();var view=open(time);view.receive(start());
        view.receive(packet(PacketType.REEL_STOP,"{\"spinId\":\"00000000-0000-0000-0000-000000000099\",\"reel\":\"LEFT\",\"stopIndex\":1,\"durationMs\":0}"));assertEquals(8,view.phase(0));
        view.receive(packet(PacketType.REEL_STOP,"{\"spinId\":\""+SPIN+"\",\"reel\":\"LEFT\",\"pressedIndex\":8,\"stopIndex\":2,\"slip\":6,\"durationMs\":380}"));time.set(190_000_000);assertEquals(5,view.phase(0),1e-9);time.set(380_000_000);assertEquals(2,view.phase(0),1e-9);time.set(9_000_000_000L);assertEquals(2,view.phase(0),1e-9);
    }
    @Test void exactZeroPingStopUsesServerHintBeforeReplyAndAckDoesNotRetarget(){
        var time=new AtomicLong();var view=open(time);var b=start().payload();b.getAsJsonObject("startPhase").addProperty("left",8.7);
        var hint=b.getAsJsonObject("stopHints").getAsJsonArray("left").get(5).getAsJsonObject();hint.addProperty("stopIndex",2);hint.addProperty("slip",3);hint.addProperty("durationMs",230);view.receive(Envelope.current(PacketType.SPIN_START,b));
        time.set(500_000_000L);double atPress=view.phase(0);int pressed=view.localInput(PacketType.STOP_LEFT);assertEquals(5,pressed);
        time.addAndGet(40_000_000L);double beforeReply=view.phase(0);assertTrue(beforeReply<atPress);assertTrue(beforeReply>2,"the three-slip motion must already be underway before the reply");
        var stop=packet(PacketType.REEL_STOP,"{\"spinId\":\""+SPIN+"\",\"reel\":\"LEFT\",\"pressedIndex\":5,\"stopIndex\":2,\"slip\":3,\"durationMs\":230,\"nextStopHints\":{}}");view.receive(stop);
        assertEquals(beforeReply,view.phase(0),1e-9,"matching authoritative ack must not retarget or jump");time.addAndGet(500_000_000L);assertEquals(2,view.phase(0),1e-9);
    }
    @Test void publicAssetsRemainAuthoritativeAndNoticeBlinkUsesOneSecondWindow(){
        var time=new AtomicLong();var view=open(time);view.receive(start());view.receive(packet(PacketType.NOTICE,"{\"spinId\":\""+SPIN+"\",\"lamp\":\"ON\",\"pattern\":\"FAST_BLINK_1S\"}"));assertTrue(view.lampOn());time.set(100_000_000);assertFalse(view.lampOn());time.set(1_000_000_000);assertTrue(view.lampOn());
        assertEquals("—",view.value("credit"));view.receive(packet(PacketType.PUBLIC_STATE,"{\"sessionId\":\""+ID+"\",\"machineId\":1,\"gameState\":\"REPLAY_READY\",\"credit\":32,\"bet\":3,\"pay\":0,\"heldMedals\":442,\"lampOn\":false,\"displayStops\":{\"left\":3,\"center\":3,\"right\":3}}"));assertEquals("32",view.value("credit"));assertEquals("3",view.value("bet"));assertEquals(3,view.phase(0));assertFalse(view.lampOn());
        var copy=view.publicState();copy.addProperty("credit",0);assertEquals("32",view.value("credit"));
    }
    @Test void resumedDisplayKeepsAlreadyStoppedReelsFixed(){
        var time=new AtomicLong();var view=open(time);
        view.receive(packet(PacketType.PUBLIC_STATE,"{\"sessionId\":\""+ID+"\",\"machineId\":1,\"gameState\":\"NORMAL_SPINNING\",\"stoppedMask\":1,\"lampOn\":false,\"displayStops\":{\"left\":14,\"center\":3,\"right\":12}}"));
        var b=start().payload();b.addProperty("animation","RESUME_NORMAL");view.receive(Envelope.current(PacketType.SPIN_START,b));time.set(1_000_000_000);assertEquals(14,view.phase(0));assertEquals(3,view.phase(1),1e-9);assertEquals(12,view.phase(2),1e-9);
    }
    @Test void clientStopDelayUsesEachServerMotionProfile(){
        for(var profile:jp.pirijuggler.common.reel.ReelMotion.Profile.values()){
            var time=new AtomicLong();var view=open(time);var b=start().payload();b.addProperty("animation",profile.name());b.addProperty("stopEnableAfterMs",profile.clientDelayMs());view.receive(Envelope.current(PacketType.SPIN_START,b));
            time.set(profile.clientDelayMs()*1_000_000L-1);assertFalse(view.canSend(PacketType.STOP_LEFT));assertFalse(view.canSend(PacketType.SPACE_ACTION));assertTrue(view.canSend(PacketType.CLOSE_REQUEST));time.incrementAndGet();assertTrue(view.canSend(PacketType.STOP_LEFT));
        }
    }
    @Test void godPushOrderHintsGateButtonsAndSpaceUsesTheAllowedReel(){
        var time=new AtomicLong();var view=open(time);var b=start().payload();
        b.addProperty("godNav","R-C-L");
        var onlyRight=new JsonObject();onlyRight.add("right",b.getAsJsonObject("stopHints").getAsJsonArray("right").deepCopy());
        b.add("stopHints",onlyRight);view.receive(Envelope.current(PacketType.SPIN_START,b));
        assertEquals("R-C-L",view.godNav());
        time.set(700_000_000L);
        assertFalse(view.canSend(PacketType.STOP_LEFT));
        assertTrue(view.canSend(PacketType.STOP_RIGHT));
        assertTrue(view.canSend(PacketType.SPACE_ACTION));

        int pressed=view.localInput(PacketType.SPACE_ACTION);
        assertTrue(pressed>=0);
        var next=new JsonObject();next.add("center",hints().getAsJsonArray("center").deepCopy());
        var stop=new JsonObject();stop.addProperty("spinId",SPIN);stop.addProperty("reel","RIGHT");
        stop.addProperty("pressedIndex",pressed);stop.addProperty("stopIndex",pressed);stop.addProperty("durationMs",80);
        stop.add("nextStopHints",next);view.receive(Envelope.current(PacketType.REEL_STOP,stop));
        assertFalse(view.canSend(PacketType.STOP_LEFT));
        assertTrue(view.canSend(PacketType.STOP_CENTER));
    }

    @Test void jugglerGodFreezeHasCinematicInputLockAndFinalImpactWindow(){
        var time=new AtomicLong();
        var view=new SlotViewState(time::get);
        view.receive(packet(PacketType.OPEN_MACHINE,"{\"sessionId\":\""+ID+"\",\"machineId\":1,\"machineType\":\"JUGGLER_GOD\"}"));
        var b=start().payload();b.addProperty("godFreeze",true);b.addProperty("stopEnableAfterMs",150);
        view.receive(Envelope.current(PacketType.SPIN_START,b));
        assertTrue(view.godFreeze());
        assertTrue(view.godFreezeInputLocked());
        time.set(1_199_000_000L);assertFalse(view.canSend(PacketType.STOP_LEFT));
        time.set(1_200_000_000L);assertTrue(view.canSend(PacketType.STOP_LEFT));assertFalse(view.godFreezeInputLocked());

        for(String reel:new String[]{"LEFT","CENTER","RIGHT"})
            view.receive(packet(PacketType.REEL_STOP,"{\"spinId\":\""+SPIN+"\",\"reel\":\""+reel+"\",\"stopIndex\":0,\"durationMs\":0}"));
        assertFalse(view.godRevealed(0),"BAR must not appear while the reel is still visually stopping");
        assertFalse(view.godRevealed(1));assertFalse(view.godRevealed(2));
        time.addAndGet(2_000_000_000L);
        assertTrue(view.godRevealed(0));assertTrue(view.godRevealed(1));assertTrue(view.godRevealed(2));
        assertFalse(view.godImpactActive());
    }


    @Test void completedGodResultStaysBlackWithAllBarsUntilNextSpin(){
        var time=new AtomicLong();
        var view=new SlotViewState(time::get);
        view.receive(packet(PacketType.OPEN_MACHINE,"{\"sessionId\":\""+ID+"\",\"machineId\":1,\"machineType\":\"JUGGLER_GOD\"}"));
        var b=start().payload();b.addProperty("godFreeze",true);
        view.receive(Envelope.current(PacketType.SPIN_START,b));

        for(String reel:new String[]{"LEFT","CENTER","RIGHT"})
            view.receive(packet(PacketType.REEL_STOP,"{\"spinId\":\""+SPIN+"\",\"reel\":\""+reel+"\",\"stopIndex\":0,\"durationMs\":0}"));
        assertFalse(view.godRevealed(0));
        time.addAndGet(2_000_000_000L);
        assertTrue(view.godRevealed(0));assertTrue(view.godRevealed(1));assertTrue(view.godRevealed(2));

        view.receive(packet(PacketType.PUBLIC_STATE,
                "{\"sessionId\":\""+ID+"\",\"machineId\":1,\"gameState\":\"BIG_READY\",\"credit\":47,\"bet\":0,\"pay\":15,\"heldMedals\":0,\"bonusCount\":0,\"lampOn\":true,\"godFreeze\":true,\"displayStops\":{\"left\":0,\"center\":0,\"right\":0},\"stoppedMask\":0}"));
        assertTrue(view.godFreeze(),"GOD reel window must not recover after the third stop");
        assertTrue(view.godRevealed(0));assertTrue(view.godRevealed(1));assertTrue(view.godRevealed(2));
        assertTrue(view.lampOn(),"Piri Chance must be lit for the confirmed GOD result");

        var next=start().payload();next.addProperty("spinId","00000000-0000-0000-0000-000000000003");
        view.receive(Envelope.current(PacketType.SPIN_START,next));
        assertFalse(view.godFreeze(),"the following BIG spin owns the next presentation");
        assertFalse(view.godRevealed(0));assertFalse(view.godRevealed(1));assertFalse(view.godRevealed(2));
    }

    @Test void reopeningCompletedGodRestoresBlackoutAndAllBarsFromPublicState(){
        var time=new AtomicLong(5_000_000_000L);
        var view=new SlotViewState(time::get);
        view.receive(packet(PacketType.OPEN_MACHINE,"{\"sessionId\":\""+ID+"\",\"machineId\":1,\"machineType\":\"JUGGLER_GOD\"}"));
        view.receive(packet(PacketType.PUBLIC_STATE,
                "{\"sessionId\":\""+ID+"\",\"machineId\":1,\"gameState\":\"BIG_READY\",\"credit\":47,\"bet\":0,\"pay\":15,\"heldMedals\":0,\"bonusCount\":0,\"lampOn\":true,\"godFreeze\":true,\"displayStops\":{\"left\":0,\"center\":0,\"right\":0},\"stoppedMask\":0}"));
        assertTrue(view.godFreeze());
        assertTrue(view.godRevealed(0));assertTrue(view.godRevealed(1));assertTrue(view.godRevealed(2));
        assertTrue(view.godFreezeElapsedMillis()>=165,"reopen must restore settled blackout instead of replaying entry");
    }

    @Test void reopeningMidGodSpinKeepsAlreadyStoppedBarsAndDoesNotReplayEntry(){
        var time=new AtomicLong(5_000_000_000L);
        var view=new SlotViewState(time::get);
        view.receive(packet(PacketType.OPEN_MACHINE,"{\"sessionId\":\""+ID+"\",\"machineId\":1,\"machineType\":\"JUGGLER_GOD\"}"));
        view.receive(packet(PacketType.PUBLIC_STATE,
                "{\"sessionId\":\""+ID+"\",\"machineId\":1,\"gameState\":\"NORMAL_SPINNING\",\"credit\":47,\"bet\":3,\"pay\":0,\"heldMedals\":0,\"bonusCount\":0,\"lampOn\":false,\"godFreeze\":true,\"displayStops\":{\"left\":7,\"center\":3,\"right\":12},\"stoppedMask\":1}"));
        var b=start().payload();b.addProperty("animation","RESUME_NORMAL");b.addProperty("godFreeze",true);
        view.receive(Envelope.current(PacketType.SPIN_START,b));
        assertTrue(view.godFreeze());
        assertTrue(view.godRevealed(0));assertFalse(view.godRevealed(1));assertFalse(view.godRevealed(2));
        assertTrue(view.godFreezeElapsedMillis()>=165);
    }

}
