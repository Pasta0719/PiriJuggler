package jp.pirijuggler.fabric.network;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.protocol.Protocol;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RemoteMachineViewStateTest {
    @Test void snapshotSpinAndStopsProduceTypedPublicView() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        RemoteMachineRegistry registry = new RemoteMachineRegistry(now::get);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, snapshot(5)));
        RemoteMachineViewState view = registry.view(5);
        assertNotNull(view);
        assertFalse(view.spinning());
        assertEquals(5, view.machineId());
        assertEquals("NORTH", view.facing());
        assertEquals(12, view.credit());
        assertEquals(3500, view.totalGames());
        assertEquals(14, view.bigCount());
        assertEquals(11, view.regCount());

        String spinId = UUID.randomUUID().toString();
        JsonObject spin = id(5);
        spin.addProperty("spinId", spinId);
        spin.addProperty("animation", "NORMAL");
        spin.addProperty("stoppedMask", 0);
        JsonObject start = new JsonObject();
        start.addProperty("left", 1.0);
        start.addProperty("center", 2.0);
        start.addProperty("right", 3.0);
        spin.add("startPhase", start);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SPIN, spin));
        assertTrue(view.spinning());
        assertEquals(UUID.fromString(spinId), view.spinId());

        now.addAndGet(250_000_000L);
        JsonObject stop = id(5);
        stop.addProperty("spinId", spinId);
        stop.addProperty("reel", "LEFT");
        stop.addProperty("stopIndex", 14);
        stop.addProperty("durationMs", 380);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_STOP, stop));
        assertEquals(14, view.displayStop(0));
        assertEquals(1, view.stoppedMask() & 1);
    }

    @Test void resetClearsTypedViewCache() {
        RemoteMachineRegistry registry = new RemoteMachineRegistry();
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, snapshot(9)));
        assertEquals(1, registry.viewSnapshot().size());
        registry.reset();
        assertTrue(registry.viewSnapshot().isEmpty());
    }

    @Test void remoteGodBlackoutSurvivesSnapshotAndBarWaitsForVisualStop(){
        AtomicLong now=new AtomicLong(1_000_000_000L);
        RemoteMachineRegistry registry=new RemoteMachineRegistry(now::get);
        JsonObject snap=snapshot(6);
        snap.addProperty("machineType","JUGGLER_GOD");
        snap.addProperty("godFreeze",true);
        snap.addProperty("gameState","BIG_READY");
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_SNAPSHOT,snap));
        RemoteMachineViewState restored=registry.view(6);
        assertTrue(restored.godFreeze());
        assertTrue(restored.godRevealed(0,now.get()));
        assertTrue(restored.godRevealed(1,now.get()));
        assertTrue(restored.godRevealed(2,now.get()));

        JsonObject fresh=snapshot(7);
        fresh.addProperty("machineType","JUGGLER_GOD");
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_SNAPSHOT,fresh));
        String spinId=UUID.randomUUID().toString();
        JsonObject spin=id(7);spin.addProperty("spinId",spinId);spin.addProperty("animation","NORMAL");spin.addProperty("godFreeze",true);spin.addProperty("stoppedMask",0);
        JsonObject phase=new JsonObject();phase.addProperty("left",1.0);phase.addProperty("center",2.0);phase.addProperty("right",3.0);spin.add("startPhase",phase);
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_SPIN,spin));
        RemoteMachineViewState live=registry.view(7);
        assertTrue(live.godFreeze());assertFalse(live.godRevealed(0,now.get()));

        JsonObject stop=id(7);stop.addProperty("spinId",spinId);stop.addProperty("reel","LEFT");stop.addProperty("stopIndex",14);stop.addProperty("durationMs",380);
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_STOP,stop));
        assertFalse(live.godRevealed(0,now.get()));
        now.addAndGet(2_000_000_000L);
        assertTrue(live.godRevealed(0,now.get()));
    }

    private static JsonObject snapshot(int id) {
        JsonObject body = id(id);
        body.addProperty("world", UUID.randomUUID().toString());
        body.addProperty("worldName", "world");
        body.addProperty("dimension", "minecraft:overworld");
        body.addProperty("x", 10); body.addProperty("y", 64); body.addProperty("z", 20);
        body.addProperty("facing", "NORTH");
        body.addProperty("enabled", true); body.addProperty("occupied", false);
        body.addProperty("gameState", "IDLE");
        JsonObject stops = new JsonObject();
        stops.addProperty("left", 1); stops.addProperty("center", 2); stops.addProperty("right", 3);
        body.add("displayStops", stops);
        body.addProperty("stoppedMask", 7);
        body.addProperty("lampOn", false);
        body.addProperty("credit", 12); body.addProperty("pay", 3);
        body.addProperty("bonusCount", 0); body.addProperty("bonusMode", "NONE");
        body.addProperty("totalGames", 3500); body.addProperty("bigCount", 14); body.addProperty("regCount", 11);
        body.addProperty("spinning", false);
        return body;
    }

    private static JsonObject id(int id) {
        JsonObject body = new JsonObject();
        body.addProperty("machineId", id);
        return body;
    }
}
