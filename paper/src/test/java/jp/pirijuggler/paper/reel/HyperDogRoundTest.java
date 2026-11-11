package jp.pirijuggler.paper.reel;

import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.common.protocol.*;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogRoundTest {
    @Test void stopRoundCanResumeAfterPartialStopForEveryInternalRole() {
        for(var role:HyperDogReelControl.Role.values()) {
            var round=new HyperDogRound(UUID.randomUUID(),role);
            if(role==HyperDogReelControl.Role.CZ_WIN||role==HyperDogReelControl.Role.CZ_LOSE) {
                var awaitingOuter=round;
                assertThrows(IllegalStateException.class,()->awaitingOuter.stopManual(Reel.CENTER,4,1));
                round.stopAutomatic(Reel.LEFT,4);
                round=HyperDogRound.restore(round.snapshot());
                round.stopAutomatic(Reel.RIGHT,17);
                assertEquals(-1,round.snapshot().lastSequence());
                round.stopManual(Reel.CENTER,13,1);
                assertEquals(1,round.snapshot().lastSequence());
            } else if(role==HyperDogReelControl.Role.AT_ENTRY_RED7) {
                var auto=round;
                assertThrows(IllegalStateException.class,()->auto.stopManual(Reel.LEFT,4,1));
                round.stopAutomatic(Reel.RIGHT,4);
                round=HyperDogRound.restore(round.snapshot());
                round.stopAutomatic(Reel.CENTER,17);
                round.stopAutomatic(Reel.LEFT,13);
                assertEquals(-1,round.snapshot().lastSequence());
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
        var resumed=HyperDogRound.restore(round.snapshot());
        assertEquals(spinId,resumed.spinId());
        assertEquals(101,resumed.snapshot().lastSequence());
        assertEquals(12,resumed.snapshot().history().input(Reel.LEFT.ordinal()));
        assertThrows(IllegalArgumentException.class,()->resumed.stopManual(Reel.CENTER,5,101));
        resumed.stopManual(Reel.CENTER,5,102);
        resumed.stopManual(Reel.RIGHT,8,103);
        assertTrue(resumed.complete());
        assertThrows(IllegalStateException.class,()->resumed.stopManual(Reel.LEFT,4,104));
    }
    @Test void czTimedAutoStopsAndEarlyMiddleDoesNotConsumeSequence() {
        var session=new HyperDogStopSession(UUID.randomUUID(),24,UUID.randomUUID(),
                HyperDogReelControl.Role.CZ_WIN,10_000,new double[]{2,5,8});
        assertEquals("CZ",session.begin().payload().get("mode").getAsString());
        assertFalse(session.begin().payload().has("role"));
        assertEquals(0,session.tick(10_499).size());
        assertEquals(1,session.tick(10_500).size());
        assertEquals("LEFT",session.tick(10_500).isEmpty()?"LEFT":"NO_DUPLICATE");
        assertEquals(1,session.tick(10_700).size());
        assertEquals(0,session.tick(10_700).size());
        assertEquals("STOP_TOO_EARLY",last(session.receive(client(session,PacketType.STOP_CENTER,1,2),10_899)).payload().get("errorCode").getAsString());
        assertEquals(-1,session.snapshot().round().lastSequence());
        assertEquals(PacketType.REEL_STOP,last(session.receive(client(session,PacketType.STOP_CENTER,1,2),10_900)).packetType());
        assertEquals(1,session.snapshot().round().lastSequence());
        assertTrue(session.complete());
        assertTrue(HyperDogReelControl.legalFinal(HyperDogReelControl.Role.CZ_WIN,session.snapshot().round().history().stops()));
        assertEquals("SEQUENCE_OLD",last(session.receive(client(session,PacketType.STOP_CENTER,1,2),11_000)).payload().get("errorCode").getAsString());
    }
    @Test void czMissNeverYieldsTheThirdRainbow() {
        var session=new HyperDogStopSession(UUID.randomUUID(),24,UUID.randomUUID(),
                HyperDogReelControl.Role.CZ_LOSE,100,new double[]{0,1,2});
        assertEquals(2,session.tick(1_000).size());
        session.receive(client(session,PacketType.STOP_CENTER,1,0),1_000);
        var s=session.snapshot().round().history().stops();
        assertEquals(HyperDogReelControl.Icon.RAINBOW7,HyperDogReelControl.icon(Reel.LEFT,s[0]));
        assertNotEquals(HyperDogReelControl.Icon.RAINBOW7,HyperDogReelControl.icon(Reel.CENTER,s[1]));
        assertEquals(HyperDogReelControl.Icon.RAINBOW7,HyperDogReelControl.icon(Reel.RIGHT,s[2]));
        assertTrue(session.complete());
    }
    @Test void automaticRedSevenDoesNotSpendClientSequenceAndIsPresentationOnly() {
        var s=new HyperDogStopSession(UUID.randomUUID(),25,UUID.randomUUID(),
                HyperDogReelControl.Role.AT_ENTRY_RED7,2000,new double[]{0,2,4});
        assertEquals(0,s.tick(2719).size());
        assertEquals(3,s.tick(2720).size());
        assertEquals(0,s.tick(2800).size());
        assertTrue(s.complete());
        assertEquals(-1,s.snapshot().round().lastSequence());
        assertFalse(s.canStartNextGame(3039));
        assertTrue(s.canStartNextGame(3040));
        assertEquals("INVALID_STATE",last(s.receive(client(s,PacketType.STOP_LEFT,1,0),3000)).payload().get("errorCode").getAsString());
        assertFalse(s.begin().payload().toString().contains("AT_ENTRY_RED7"));
    }
    @Test void upperComebackUsesThreeManualRainbowStopsNotCzAutos() {
        var s=new HyperDogStopSession(UUID.randomUUID(),28,UUID.randomUUID(),
                HyperDogReelControl.Role.AT_ENTRY_RAINBOW7,1000,new double[]{2,6,9});
        assertEquals("RAINBOW7_CONFIRM",s.begin().payload().get("mode").getAsString());
        assertEquals(0,s.tick(3000).size(),"confirmation never auto-stops outer reels");
        assertEquals("STOP_TOO_EARLY",last(s.receive(client(s,PacketType.STOP_LEFT,1,1),1600))
                .payload().get("errorCode").getAsString());
        assertEquals(-1,s.snapshot().round().lastSequence());
        s.receive(client(s,PacketType.STOP_RIGHT,1,4),1800);
        var resumed=HyperDogStopSession.fromJson(s.toJson());
        assertEquals(1,resumed.snapshot().round().history().count());
        assertEquals("ALREADY_STOPPED",last(resumed.receive(client(resumed,PacketType.STOP_RIGHT,2,4),1900))
                .payload().get("errorCode").getAsString());
        resumed.receive(client(resumed,PacketType.STOP_CENTER,2,7),1900);
        resumed.receive(client(resumed,PacketType.STOP_LEFT,3,11),2000);
        assertTrue(resumed.complete());
        assertArrayEquals(new int[]{14,14,14},resumed.snapshot().round().history().stops());
        assertEquals(3,resumed.snapshot().round().lastSequence());
        assertTrue(resumed.canStartNextGame(2000));
    }
    @Test void stopPacketsRoundTripViaProductionCodecAndSnapshotRecovery() {
        var s=new HyperDogStopSession(UUID.randomUUID(),25,UUID.randomUUID(),
                HyperDogReelControl.Role.GRAPE,3000,new double[]{1,2,3});
        Envelope begin=EnvelopeCodec.decode(EnvelopeCodec.encode(s.begin()));
        assertEquals(PacketType.SPIN_START,begin.packetType());
        assertFalse(begin.payload().has("role"));
        var early=s.receive(client(s,PacketType.STOP_RIGHT,1,4),3600);
        assertEquals("STOP_TOO_EARLY",last(early).payload().get("errorCode").getAsString());
        assertEquals(-1,s.snapshot().round().lastSequence());
        var one=s.receive(client(s,PacketType.STOP_RIGHT,1,4),3700);
        assertEquals(PacketType.REEL_STOP,last(one).packetType());
        for(var envelope:one)assertEquals(envelope,EnvelopeCodec.decode(EnvelopeCodec.encode(envelope)));
        JsonObject durable=s.toJson();
        var restored=HyperDogStopSession.fromJson(durable);
        assertEquals(durable,restored.toJson());
        var replay=restored.replayOnReconnect();
        assertEquals(PacketType.SPIN_START,replay.get(0).packetType());
        assertEquals(PacketType.REEL_STOP,replay.get(1).packetType());
        restored.receive(client(restored,PacketType.STOP_CENTER,2,5),3900);
        restored.receive(client(restored,PacketType.STOP_LEFT,3,6),4100);
        assertTrue(restored.complete());
        assertTrue(HyperDogReelControl.legalFinal(HyperDogReelControl.Role.GRAPE,restored.snapshot().round().history().stops()));
    }
    private static Envelope last(List<Envelope> packets){return packets.get(packets.size()-1);}
    private static Envelope client(HyperDogStopSession s,PacketType action,long sequence,int pressed) {
        JsonObject b=new JsonObject();
        b.addProperty("sessionId",s.snapshot().sessionId().toString());
        b.addProperty("machineId",s.snapshot().machineId());
        b.addProperty("clientSequence",sequence);
        b.addProperty("pressedIndex",pressed);
        return Envelope.current(action,b);
    }
}
