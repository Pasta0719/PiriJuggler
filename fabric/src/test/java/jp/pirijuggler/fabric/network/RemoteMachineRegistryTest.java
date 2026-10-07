package jp.pirijuggler.fabric.network;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.protocol.Protocol;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RemoteMachineRegistryTest {
    @Test void snapshotAndPublicEventsUpdateOnlyRemoteCache() {
        RemoteMachineRegistry registry = new RemoteMachineRegistry();
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, snapshot(7)));

        JsonObject stored = registry.machine(7);
        assertNotNull(stored);
        assertEquals("NONE", stored.get("bonusMode").getAsString());
        assertFalse(stored.get("spinning").getAsBoolean());

        JsonObject spin = id(7);
        spin.addProperty("spinId", UUID.randomUUID().toString());
        spin.addProperty("animation", "NORMAL");
        JsonObject phase = new JsonObject();
        phase.addProperty("left", 1.0); phase.addProperty("center", 2.0); phase.addProperty("right", 3.0);
        spin.add("startPhase", phase);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SPIN, spin));
        assertTrue(registry.machine(7).get("spinning").getAsBoolean());

        JsonObject stop = id(7);
        stop.addProperty("spinId", spin.get("spinId").getAsString());
        stop.addProperty("reel", "LEFT");
        stop.addProperty("stopIndex", 14);
        stop.addProperty("durationMs", 380);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_STOP, stop));
        assertEquals(14, registry.machine(7).getAsJsonObject("displayStops").get("left").getAsInt());

        JsonObject notice = id(7);
        notice.addProperty("spinId", spin.get("spinId").getAsString());
        notice.addProperty("lamp", "ON");
        notice.addProperty("pattern", "STEADY");
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_NOTICE, notice));
        assertTrue(registry.machine(7).get("lampOn").getAsBoolean());

        JsonObject bonus = id(7);
        bonus.addProperty("active", true);
        bonus.addProperty("bonusType", "BIG");
        bonus.addProperty("count", 0);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_BONUS, bonus));
        assertEquals("BIG", registry.machine(7).get("bonusMode").getAsString());
    }

    @Test void jugglerGodSnapshotIsAcceptedWithoutChangingJugglerDefault() {
        RemoteMachineRegistry registry = new RemoteMachineRegistry();
        JsonObject normal=snapshot(20);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, normal));
        assertEquals("JUGGLER",registry.machine(20).get("machineType").getAsString());

        JsonObject successor=snapshot(21);
        successor.addProperty("machineType","JUGGLER_GOD");
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, successor));
        assertEquals("JUGGLER_GOD",registry.machine(21).get("machineType").getAsString());
        assertEquals("JUGGLER",registry.machine(20).get("machineType").getAsString());
    }

    @Test void malformedRemotePacketDropsOnlyReferencedMachine() {
        RemoteMachineRegistry registry = new RemoteMachineRegistry();
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, snapshot(1)));
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, snapshot(2)));

        JsonObject bad = id(1);
        bad.addProperty("reel", "LEFT");
        bad.addProperty("stopIndex", 999);
        bad.addProperty("durationMs", 380);
        bad.addProperty("spinId", UUID.randomUUID().toString());
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_STOP, bad));

        assertNull(registry.machine(1));
        assertNotNull(registry.machine(2));
    }


    @Test void staleStopCannotOverwriteNewerSpin() {
        RemoteMachineRegistry registry = new RemoteMachineRegistry();
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, snapshot(8)));

        String oldSpin = UUID.randomUUID().toString();
        JsonObject first = id(8);
        first.addProperty("spinId", oldSpin);
        first.addProperty("animation", "NORMAL");
        JsonObject phase = new JsonObject();
        phase.addProperty("left", 0.0); phase.addProperty("center", 0.0); phase.addProperty("right", 0.0);
        first.add("startPhase", phase);
        first.addProperty("stoppedMask", 0);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SPIN, first));

        String newSpin = UUID.randomUUID().toString();
        JsonObject second = first.deepCopy();
        second.addProperty("spinId", newSpin);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SPIN, second));

        JsonObject stale = id(8);
        stale.addProperty("spinId", oldSpin);
        stale.addProperty("reel", "LEFT");
        stale.addProperty("stopIndex", 17);
        stale.addProperty("durationMs", 380);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_STOP, stale));

        assertEquals(newSpin, registry.machine(8).get("spinId").getAsString());
        assertEquals(0, registry.machine(8).getAsJsonObject("displayStops").get("left").getAsInt());
    }

    @Test void removeAndResetDiscardState() {
        RemoteMachineRegistry registry = new RemoteMachineRegistry();
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, snapshot(3)));
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_REMOVE, id(3)));
        assertNull(registry.machine(3));

        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, snapshot(4)));
        registry.reset();
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test void pachinkoSnapshotAndEventKeepObserverOnCommittedBall() {
        RemoteMachineRegistry registry=new RemoteMachineRegistry();
        JsonObject snap=snapshot(44);snap.addProperty("machineType","PACHINKO");snap.addProperty("pachinkoPresentation","LEFT_KURUN");
        snap.addProperty("pachinkoBallSequenceId",9);snap.addProperty("pachinkoOutcome","V");snap.addProperty("pachinkoStartTime",1234L);snap.addProperty("pachinkoSeed",9876L);
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_SNAPSHOT,snap));
        var view=registry.view(44);assertNotNull(view);assertEquals(9,view.pachinkoBallSequenceId());assertEquals("V",view.pachinkoOutcome());assertEquals(9876L,view.pachinkoSeed());
        JsonObject event=id(44);event.addProperty("ballSequenceId",10);event.addProperty("side","LEFT");event.addProperty("outcome","OUT");event.addProperty("seed",111L);event.addProperty("startTime",2000L);
        registry.receive(new Envelope(Protocol.VERSION,PacketType.PACHINKO_EVENT,event));
        assertEquals(10,view.pachinkoBallSequenceId());assertEquals("LEFT",view.pachinkoSide());assertEquals("OUT",view.pachinkoOutcome());assertEquals(111L,view.pachinkoSeed());
    }

    @Test void rightPachinkoSnapshotReconstructsCommittedOutcomeAndMachinesStayIndependent() {
        RemoteMachineRegistry registry=new RemoteMachineRegistry();
        JsonObject left=snapshot(50);left.addProperty("machineType","PACHINKO");left.addProperty("pachinkoPresentation","LEFT_KURUN");left.addProperty("pachinkoBallSequenceId",3);left.addProperty("pachinkoOutcome","OUT");left.addProperty("pachinkoStartTime",1000L);left.addProperty("pachinkoSeed",10L);
        JsonObject right=snapshot(51);right.addProperty("machineType","PACHINKO");right.addProperty("pachinkoPresentation","RIGHT_KURUN");right.addProperty("pachinkoBallSequenceId",8);right.addProperty("pachinkoOutcome","WIN_3000");right.addProperty("pachinkoStartTime",1200L);right.addProperty("pachinkoSeed",20L);
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_SNAPSHOT,left));
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_SNAPSHOT,right));
        assertEquals("LEFT_KURUN",registry.view(50).pachinkoSide());
        assertEquals("OUT",registry.view(50).pachinkoOutcome());
        assertEquals("RIGHT_KURUN",registry.view(51).pachinkoSide());
        assertEquals("WIN_3000",registry.view(51).pachinkoOutcome());
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_REMOVE,id(50)));
        assertNull(registry.view(50));
        assertNotNull(registry.view(51));
        assertEquals(8,registry.view(51).pachinkoBallSequenceId());
    }


    @Test void hallAudioCarriesExactOperationsPerMachine() {
        RemoteMachineRegistry registry=new RemoteMachineRegistry();
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_SNAPSHOT,snapshot(61)));
        var sync=registry.pollAudioEvent();assertNotNull(sync);assertEquals("SYNC",sync.op());assertEquals(61,sync.machineId());

        JsonObject notice=id(61);notice.addProperty("op","PLAY");notice.addProperty("sound","notice");
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_SOUND,notice));
        var sound=registry.pollAudioEvent();assertNotNull(sound);assertEquals(61,sound.machineId());assertEquals("PLAY",sound.op());assertEquals("notice",sound.sound());
        assertEquals(0,sound.delayMs());assertEquals(1,sound.count());assertEquals(0,sound.spacingMs());

        JsonObject burst=id(61);burst.addProperty("op","PLAY");burst.addProperty("sound","notice");burst.addProperty("count",5);burst.addProperty("spacingMs",100);
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_SOUND,burst));
        var repeated=registry.pollAudioEvent();assertNotNull(repeated);assertEquals(5,repeated.count());assertEquals(100,repeated.spacingMs());

        JsonObject loop=id(61);loop.addProperty("op","LOOP_START");loop.addProperty("sound","big_bgm");loop.addProperty("delayMs",4500);
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_SOUND,loop));
        var bgm=registry.pollAudioEvent();assertNotNull(bgm);assertEquals("LOOP_START",bgm.op());assertEquals("big_bgm",bgm.sound());assertEquals(4500,bgm.delayMs());

        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_REMOVE,id(61)));
        assertEquals("REMOVE",registry.pollAudioEvent().op());
    }

    private static JsonObject snapshot(int id) {
        JsonObject body = id(id);
        body.addProperty("world", UUID.randomUUID().toString());
        body.addProperty("worldName", "world");
        body.addProperty("x", id);
        body.addProperty("y", 64);
        body.addProperty("z", 0);
        body.addProperty("facing", "NORTH");
        body.addProperty("enabled", true);
        body.addProperty("occupied", false);
        body.addProperty("gameState", "IDLE");
        JsonObject stops = new JsonObject();
        stops.addProperty("left", 0); stops.addProperty("center", 0); stops.addProperty("right", 0);
        body.add("displayStops", stops);
        body.addProperty("stoppedMask", 7);
        body.addProperty("lampOn", false);
        body.addProperty("credit", 0);
        body.addProperty("pay", 0);
        body.addProperty("bonusCount", 0);
        body.addProperty("bonusMode", "NONE");
        body.addProperty("totalGames", 3500);
        body.addProperty("bigCount", 14);
        body.addProperty("regCount", 11);
        body.addProperty("spinning", false);
        return body;
    }

    private static JsonObject id(int id) {
        JsonObject body = new JsonObject();
        body.addProperty("machineId", id);
        return body;
    }
}
