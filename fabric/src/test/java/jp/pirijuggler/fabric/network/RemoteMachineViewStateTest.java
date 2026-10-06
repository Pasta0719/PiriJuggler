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
    @Test void skillTargetAndGamesSurviveSnapshotAndSuccessSoundIsOnceOnly(){
        var registry=new RemoteMachineRegistry();var snap=snapshot(7);snap.addProperty("machineType","SKILL_STOP");
        snap.addProperty("gameState","BIG_SPINNING");snap.addProperty("bonusMode","BIG");snap.addProperty("skillRemaining",1);snap.addProperty("skillChallenge","PIERO");
        registry.receive(Envelope.current(PacketType.REMOTE_MACHINE_SNAPSHOT,snap));
        assertNotNull(registry.view(7));assertEquals("symbols/piero.png",registry.view(7).skillChallengeTexture());assertEquals(1,registry.view(7).skillRemaining());
        var sound=id(7);sound.addProperty("spinId",UUID.randomUUID().toString());sound.addProperty("skillChallengeSuccess",true);sound.addProperty("sound","NOTICE");
        registry.receive(Envelope.current(PacketType.REMOTE_MACHINE_SOUND,sound));assertEquals(7,registry.pollSkillSuccessSound());assertNull(registry.pollSkillSuccessSound());
        snap.addProperty("skillRemaining",3);snap.addProperty("skillChallenge","AUTO");registry.receive(Envelope.current(PacketType.REMOTE_MACHINE_SNAPSHOT,snap));
        assertNull(registry.view(7).skillChallengeTexture());assertEquals(3,registry.view(7).skillRemaining());
        registry.receive(Envelope.current(PacketType.REMOTE_MACHINE_SOUND,sound));assertNull(registry.pollSkillSuccessSound());
        snap.addProperty("skillChallenge","UNKNOWN");registry.receive(Envelope.current(PacketType.REMOTE_MACHINE_SNAPSHOT,snap));assertNull(registry.view(7));
    }
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

    @Test void publicNoticeDrivesDeterministicLampOffOnAndBlink() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        RemoteMachineRegistry registry = new RemoteMachineRegistry(now::get);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, snapshot(13)));
        RemoteMachineViewState view = registry.view(13);
        assertFalse(view.lampVisible(now.get()));
        JsonObject notice = id(13); notice.addProperty("lamp","ON"); notice.addProperty("pattern","FAST_BLINK_1S");
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_NOTICE, notice));
        assertTrue(view.lampVisible(now.get()));
        now.addAndGet(100_000_000L); assertFalse(view.lampVisible(now.get()));
        now.addAndGet(100_000_000L); assertTrue(view.lampVisible(now.get()));
        now.addAndGet(800_000_000L); assertTrue(view.lampVisible(now.get()));
        JsonObject off = id(13); off.addProperty("lamp","OFF"); off.addProperty("pattern","NONE");
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_NOTICE, off));
        assertFalse(view.lampVisible(now.get()));
    }

    @Test void redefineSnapshotImmediatelyReplacesPlacementAndFacing() {
        RemoteMachineRegistry registry = new RemoteMachineRegistry();
        JsonObject first = snapshot(21);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, first));
        assertEquals("NORTH", registry.view(21).facing());
        assertEquals(10, registry.view(21).x());
        JsonObject moved = snapshot(21);
        moved.addProperty("x", 14); moved.addProperty("y", 100); moved.addProperty("z", 0); moved.addProperty("facing", "EAST");
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, moved));
        assertEquals(14, registry.view(21).x());
        assertEquals(100, registry.view(21).y());
        assertEquals(0, registry.view(21).z());
        assertEquals("EAST", registry.view(21).facing());
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

    @Test void spectatorGodPresentationAlsoReverseAlignsToCenterSevens(){
        AtomicLong nanos=new AtomicLong();
        AtomicLong millis=new AtomicLong(200_000L);
        RemoteMachineRegistry registry=new RemoteMachineRegistry(nanos::get,millis::get);
        JsonObject snap=snapshot(8);
        snap.addProperty("machineType","JUGGLER_GOD");
        snap.addProperty("gameState","BIG_READY");
        snap.addProperty("godPresentationStartMs",200_000L);
        JsonObject stops=snap.getAsJsonObject("displayStops");
        stops.addProperty("left",9);stops.addProperty("center",11);stops.addProperty("right",4);
        registry.receive(new Envelope(Protocol.VERSION,PacketType.REMOTE_MACHINE_SNAPSHOT,snap));
        RemoteMachineViewState view=registry.view(8);
        assertEquals(9,view.phase(0,nanos.get()),1e-9);
        nanos.set(400_000_000L);
        assertEquals(9,view.phase(0,nanos.get()),1e-9);
        nanos.set(800_000_000L);
        assertNotEquals(9,view.phase(0,nanos.get()),1e-6);
        nanos.set(12_700_000_000L);
        assertEquals(3,view.phase(0,nanos.get()),1e-9);
        assertEquals(3,view.phase(1,nanos.get()),1e-9);
        assertEquals(3,view.phase(2,nanos.get()),1e-9);
    }


    @Test void publicNoticeBonusAndRedefineUpdateWorldCabinetStateDeterministically() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        RemoteMachineRegistry registry = new RemoteMachineRegistry(now::get);
        JsonObject snap = snapshot(13);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, snap));
        RemoteMachineViewState view = registry.view(13);
        assertNotNull(view);
        assertFalse(view.lampVisible(now.get()));
        assertEquals("NONE", view.bonusMode());

        JsonObject notice = id(13);
        notice.addProperty("on", true);
        notice.addProperty("blinkMs", 200);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_NOTICE, notice));
        assertTrue(view.lampVisible(now.get()));
        now.addAndGet(250_000_000L);
        assertFalse(view.lampVisible(now.get()));

        JsonObject bonus = id(13);
        bonus.addProperty("active", true);
        bonus.addProperty("mode", "REG");
        bonus.addProperty("bonusCount", 4);
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_BONUS, bonus));
        assertEquals("REG", view.bonusMode());
        assertEquals(4, view.bonusCount());

        JsonObject moved = snapshot(13);
        moved.addProperty("x", 30);
        moved.addProperty("y", 80);
        moved.addProperty("z", -4);
        moved.addProperty("facing", "EAST");
        registry.receive(new Envelope(Protocol.VERSION, PacketType.REMOTE_MACHINE_SNAPSHOT, moved));
        RemoteMachineViewState redefined = registry.view(13);
        assertEquals(30, redefined.x());
        assertEquals(80, redefined.y());
        assertEquals(-4, redefined.z());
        assertEquals("EAST", redefined.facing());
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
