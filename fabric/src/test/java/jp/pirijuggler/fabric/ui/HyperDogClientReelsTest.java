package jp.pirijuggler.fabric.ui;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.reel.HyperDogStopSession;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogClientReelsTest {
    @Test void independentRolesPaperPacketsFabricStopsNoRoleDisclosure() {
        for(var role:HyperDogReelControl.Role.values()){
            var now=new AtomicLong(10000L);
            UUID sessionId=UUID.randomUUID(),spinId=UUID.randomUUID();
            var server=new HyperDogStopSession(sessionId,87,spinId,role,10000,new double[]{0,5,10});
            var client=new HyperDogClientReels(now::get);
            client.receive(open(sessionId,87));
            deliver(client,server.begin());
            assertEquals(spinId,client.spinId());
            assertFalse(server.begin().payload().has("role"));
            assertFalse(server.begin().payload().has("internalRole"));
            assertEquals(0,client.stoppedMask());
            if(role==HyperDogReelControl.Role.CZ_WIN||role==HyperDogReelControl.Role.CZ_LOSE){
                now.set(10500);deliver(client,server.tick(now.get()));
                assertEquals(1,client.stoppedMask());
                now.set(10700);deliver(client,server.tick(now.get()));
                assertEquals(5,client.stoppedMask());
                assertFalse(client.canStop(PacketType.STOP_LEFT));
                now.set(10900);assertTrue(client.canStop(PacketType.STOP_CENTER));
                deliver(client,server.receive(request(sessionId,87,1,PacketType.STOP_CENTER,8),now.get()));
            }else if(role==HyperDogReelControl.Role.AT_ENTRY_RED7){
                now.set(10720);deliver(client,server.tick(now.get()));
                assertFalse(client.canStop(PacketType.STOP_CENTER));
            }else{
                now.set(10700);assertTrue(client.canStop(PacketType.STOP_LEFT));
                deliver(client,server.receive(request(sessionId,87,1,PacketType.STOP_LEFT,3),now.get()));
                now.set(10800);deliver(client,server.receive(request(sessionId,87,2,PacketType.STOP_RIGHT,7),now.get()));
                now.set(10900);deliver(client,server.receive(request(sessionId,87,3,PacketType.STOP_CENTER,12),now.get()));
            }
            assertTrue(client.complete(),role.toString());
            assertTrue(server.complete(),role.toString());
            var serverStops=server.snapshot().round().history().stops();
            for(Reel reel:Reel.values()){
                assertEquals(serverStops[reel.ordinal()],client.stopIndex(reel),role+" "+reel);
                assertEquals(HyperDogReelControl.visible(reel,serverStops[reel.ordinal()],0),client.visible(reel,0));
                assertTrue(client.texturePath(reel,-1).startsWith("gui/original_at/symbols/"));
                assertTrue(client.texturePath(reel,1).endsWith(".png"));
            }
            var replayed=new HyperDogClientReels(now::get);
            replayed.receive(open(sessionId,87));
            deliver(replayed,HyperDogStopSession.fromJson(server.toJson()).replayOnReconnect());
            assertTrue(replayed.complete(),role.toString());
            for(Reel reel:Reel.values())assertEquals(client.stopIndex(reel),replayed.stopIndex(reel));
        }
    }
    @Test void ignoreOtherSpinsAndRejectContradictoryStops() {
        UUID sessionId=UUID.randomUUID();
        var server=new HyperDogStopSession(sessionId,87,UUID.randomUUID(),
                HyperDogReelControl.Role.MISS,1000,new double[]{0,5,10});
        var now=new AtomicLong(1700);var client=new HyperDogClientReels(now::get);
        client.receive(open(sessionId,87));deliver(client,server.begin());
        var packet=server.receive(request(sessionId,87,1,PacketType.STOP_LEFT,3),now.get()).getLast();
        assertEquals(PacketType.REEL_STOP,packet.packetType());
        deliver(client,packet);
        deliver(client,packet); // legitimate duplicate is idempotent
        var changed=packet.payload();changed.addProperty("stopIndex",Math.floorMod(changed.get("stopIndex").getAsInt()+1,21));
        assertThrows(IllegalArgumentException.class,()->client.receive(Envelope.current(PacketType.REEL_STOP,changed)));
        var foreign=packet.payload();foreign.addProperty("spinId",UUID.randomUUID().toString());
        client.receive(Envelope.current(PacketType.REEL_STOP,foreign));
        assertEquals(1,client.stoppedMask());
    }
    private static void deliver(HyperDogClientReels client,Envelope packet){
        client.receive(EnvelopeCodec.decode(EnvelopeCodec.encode(packet)));
    }
    private static void deliver(HyperDogClientReels client,List<Envelope> packets){
        for(Envelope packet:packets)if(packet.packetType()==PacketType.REEL_STOP||packet.packetType()==PacketType.SPIN_START)
            deliver(client,packet);
    }
    private static Envelope open(UUID session,int machine) {
        JsonObject b=new JsonObject();b.addProperty("sessionId",session.toString());
        b.addProperty("machineId",machine);b.addProperty("machineType","HYPER_DOG");
        return Envelope.current(PacketType.OPEN_MACHINE,b);
    }
    private static Envelope request(UUID session,int machine,long seq,PacketType type,int pressed){
        JsonObject b=new JsonObject();b.addProperty("sessionId",session.toString());b.addProperty("machineId",machine);
        b.addProperty("clientSequence",seq);b.addProperty("pressedIndex",pressed);return Envelope.current(type,b);
    }
}
