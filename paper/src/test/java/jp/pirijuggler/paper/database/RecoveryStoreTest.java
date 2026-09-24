package jp.pirijuggler.paper.database;

import jp.pirijuggler.paper.config.ConfigValidation;
import com.google.gson.JsonObject;
import jp.pirijuggler.paper.game.god.*;
import jp.pirijuggler.paper.game.JugglerGodRuntime;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import jp.pirijuggler.paper.reel.StopCatalogue;
import jp.pirijuggler.paper.reel.StopSolver;
import jp.pirijuggler.paper.session.Session;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryStoreTest {
    @TempDir Path directory;
    PiriDatabase db;
    Map<String,Object> config;
    static final long NOW=1_700_000_000_000L;

    @BeforeEach void open() throws Exception {
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"))){
            config=ConfigValidation.load(reader).values();
        }
        db=new PiriDatabase(directory.resolve("piri.db"));
        db.open(1,NOW,config,new SplittableRandom(1),ignored->{});
    }

    @AfterEach void close() throws Exception { db.close(); }

    @Test void sameSnapshotForceSettlementIsIdempotent() throws Exception {
        int machine=db.create(new Machine.Location(UUID.randomUUID(),"world",0,64,0,"NORTH"),NOW);
        UUID player=UUID.randomUUID();
        db.seat(player,machine,NOW);
        db.sql("UPDATE player_sessions SET game_state='BONUS_PENDING_BIG',credit=10,held_medals=0,bonus_type='BIG',last_client_sequence=7 WHERE player_uuid=?",player.toString());
        Session stale=db.state().session(player);
        RecoveryStore recovery=new RecoveryStore(db,config,new StopSolver(new StopCatalogue()),123L);

        db.transaction(()->{recovery.settle(stale,NOW+1);return null;});
        Session first=db.state().session(player);
        assertEquals(Session.GameState.SEATED_READY,first.state());
        assertEquals(50,first.number("credit"));
        assertEquals(199,first.number("held_medals"));
        assertEquals(1,scalar("SELECT big_count FROM machine_period_stats"));
        assertEquals(1,scalar("SELECT count(*) FROM bonus_history"));
        assertEquals(1,scalar("SELECT count(*) FROM economy_transactions WHERE operation='FORCE_SETTLEMENT'"));
        assertEquals("SETTLE:"+stale.id()+":7",RecoveryStore.settlementTransactionId(stale));

        db.transaction(()->{recovery.settle(stale,NOW+2);return null;});
        Session second=db.state().session(player);
        assertEquals(first.number("credit"),second.number("credit"));
        assertEquals(first.number("held_medals"),second.number("held_medals"));
        assertEquals(1,scalar("SELECT big_count FROM machine_period_stats"));
        assertEquals(1,scalar("SELECT count(*) FROM bonus_history"));
        assertEquals(1,scalar("SELECT count(*) FROM economy_transactions WHERE operation='FORCE_SETTLEMENT'"));
    }

    @Test void godPendingSpinRecoveryUsesPersistedResultWithoutJugglerRedraw() throws Exception {
        int machine=db.create(new Machine.Location(UUID.randomUUID(),"world",1,64,0,"NORTH"),MachineType.GOD,NOW);
        UUID player=UUID.randomUUID();
        db.seat(player,machine,NOW);

        GodSessionState finalGameplay=new GodSessionState(
                GodPhase.GG,50,3,GodLoopType.D,0,0,0,0,0,0,0,0,
                "GOD","GOD");
        GodMachineRuntime pending=GodMachineRuntime.initial().withGameplay(finalGameplay);
        JsonObject wrapper=GodSessionState.initial().toJson();
        wrapper.add("_pendingRuntime",pending.toJson());
        wrapper.addProperty("_pendingPayout",15);
        wrapper.addProperty("_pendingReplay",false);
        wrapper.addProperty("_pendingRole","GOD");
        wrapper.addProperty("_pendingDisplayRole","GOD");

        db.sql("UPDATE player_sessions SET game_state='NORMAL_SPINNING',credit=47,held_medals=0,internal_role='GOD',machine_state_json=?,stopped_mask=0,phase_left=4.0,phase_center=4.0,phase_right=4.0,last_client_sequence=9 WHERE player_uuid=?",
                wrapper.toString(),player.toString());
        Session stale=db.state().session(player);
        RecoveryStore recovery=new RecoveryStore(db,config,new StopSolver(new StopCatalogue()),123L);

        db.transaction(()->{recovery.settle(stale,NOW+1);return null;});
        Session settled=db.state().session(player);
        assertEquals(Session.GameState.SEATED_READY,settled.state());
        assertEquals(50,settled.number("credit"));
        assertEquals(12,settled.number("held_medals"));
        assertEquals("GG",settled.machineState().get("phase").getAsString());
        assertEquals(1,scalar("SELECT total_games FROM machine_period_stats WHERE machine_id="+machine));

        String stored=(String)db.rows("SELECT machine_runtime_json FROM machines WHERE machine_id="+machine).getFirst().get("machine_runtime_json");
        assertEquals(GodPhase.GG,GodMachineRuntime.fromJson(stored).gameplay().phase());
    }

    @Test void godOrderedYellowOneMedalRecoveryKeepsDedicatedVisibleForm() throws Exception {
        int machine=db.create(new Machine.Location(UUID.randomUUID(),"world",3,64,0,"NORTH"),MachineType.GOD,NOW);
        UUID player=UUID.randomUUID();
        db.seat(player,machine,NOW);

        GodMachineRuntime pending=GodMachineRuntime.initial();
        JsonObject wrapper=GodSessionState.initial().toJson();
        wrapper.add("_pendingRuntime",pending.toJson());
        wrapper.addProperty("_pendingPayout",1);
        wrapper.addProperty("_pendingReplay",false);
        wrapper.addProperty("_pendingRole","ORDERED_YELLOW7");
        wrapper.addProperty("_pendingDisplayRole","ORDERED_YELLOW7_ONE");

        db.sql("UPDATE player_sessions SET game_state='NORMAL_SPINNING',credit=47,held_medals=0,internal_role='ORDERED_YELLOW7',machine_state_json=?,stopped_mask=0,phase_left=7.0,phase_center=11.0,phase_right=3.0,last_client_sequence=12 WHERE player_uuid=?",
                wrapper.toString(),player.toString());
        Session stale=db.state().session(player);
        RecoveryStore recovery=new RecoveryStore(db,config,new StopSolver(new StopCatalogue()),123L);

        db.transaction(()->{recovery.settle(stale,NOW+1);return null;});
        Session settled=db.state().session(player);
        assertEquals(Session.GameState.SEATED_READY,settled.state());
        assertEquals(48,settled.number("credit"));
        assertTrue(jp.pirijuggler.common.reel.GodStopControl.matchesPublishedForm(
                "ORDERED_YELLOW7_ONE",
                (int)settled.number("display_left_stop"),
                (int)settled.number("display_center_stop"),
                (int)settled.number("display_right_stop")));
        assertFalse(jp.pirijuggler.common.reel.GodStopControl.isSafeMiss(
                (int)settled.number("display_left_stop"),
                (int)settled.number("display_center_stop"),
                (int)settled.number("display_right_stop")));
    }

    @Test void godReplayRecoveryPreservesFreeGameValue() throws Exception {
        int machine=db.create(new Machine.Location(UUID.randomUUID(),"world",2,64,0,"NORTH"),MachineType.GOD,NOW);
        UUID player=UUID.randomUUID();
        db.seat(player,machine,NOW);
        db.setMachineRuntimeJson(machine,GodMachineRuntime.initial().toJsonString(),NOW);
        db.sql("UPDATE player_sessions SET game_state='REPLAY_READY',credit=47,held_medals=0,current_bet=3,machine_state_json=?,last_client_sequence=11 WHERE player_uuid=?",
                GodSessionState.initial().toJsonString(),player.toString());
        Session stale=db.state().session(player);
        RecoveryStore recovery=new RecoveryStore(db,config,new StopSolver(new StopCatalogue()),123L);

        db.transaction(()->{recovery.settle(stale,NOW+1);return null;});
        Session settled=db.state().session(player);
        assertEquals(Session.GameState.SEATED_READY,settled.state());
        assertEquals(50,settled.number("credit"));
        assertEquals(0,settled.number("held_medals"));
        assertEquals(3,scalar("SELECT today_difference FROM machine_period_stats WHERE machine_id="+machine));
    }

    @Test void jugglerGodGraceExpiryKeepsGuaranteedZeroGameContinuation() throws Exception {
        int machine=db.create(new Machine.Location(UUID.randomUUID(),"world",8,64,0,"NORTH"),MachineType.JUGGLER_GOD,NOW);
        UUID player=UUID.randomUUID();
        db.seat(player,machine,NOW);

        var runtime=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,4,false,false,
                "GOD_CHAIN",1,false,"GOD_BIG_STARTED");
        db.setMachineRuntimeJson(machine,runtime.toJsonString(),NOW);
        db.sql("UPDATE player_sessions SET game_state='BIG_READY',lifecycle='SUSPENDED_GRACE',lock_expires_at=?,bonus_type='BIG',bonus_payout_count=0,machine_state_json=?,last_client_sequence=21 WHERE player_uuid=?",
                NOW,runtime.toJsonString(),player.toString());

        Session reseated=db.seat(player,machine,NOW+1);
        assertEquals(Session.GameState.SEATED_READY,reseated.state());
        JugglerGodRuntime recovered=JugglerGodRuntime.fromJson(reseated.machineState().toString());
        assertEquals(JugglerGodRuntime.Mode.GOD_CHAIN,recovered.mode());
        assertEquals(3,recovered.guaranteedRemaining());
        assertTrue(recovered.forceChainBig(),"next guaranteed BIG must survive leave/recovery");
        assertFalse(recovered.countNextChainGame(),"guaranteed BIG remains a 0G continuation");

        String stored=(String)db.rows("SELECT machine_runtime_json FROM machines WHERE machine_id="+machine).getFirst().get("machine_runtime_json");
        JugglerGodRuntime machineRuntime=JugglerGodRuntime.fromJson(stored);
        assertTrue(machineRuntime.forceChainBig());
        assertEquals(3,machineRuntime.guaranteedRemaining());
    }

    private long scalar(String sql) throws Exception {
        return ((Number)db.rows(sql).getFirst().values().iterator().next()).longValue();
    }
}
