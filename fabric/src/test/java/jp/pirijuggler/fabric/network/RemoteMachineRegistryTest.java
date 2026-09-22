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
