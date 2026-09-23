package jp.pirijuggler.paper.admin;

import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.machine.Machine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AdminStoreTest {
    @TempDir Path directory;
    PiriDatabase db;
    Map<String,Object> config;
    AdminStore store;
    static final long NOW=1_700_000_000_000L;

    @BeforeEach void open() throws Exception {
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"))){config=ConfigValidation.load(reader).values();}
        db=new PiriDatabase(directory.resolve("piri.db"));
        db.open(1,NOW,config,new SplittableRandom(1),ignored->{});
        store=new AdminStore(db,config);
    }
    @AfterEach void close() throws Exception {db.close();}
    int create(int x) throws Exception {return db.create(new Machine.Location(UUID.randomUUID(),"world",x,64,0,"NORTH"),NOW);}
    long scalar(String sql,Object... args) throws Exception {return ((Number)db.rows(sql,args).getFirst().values().iterator().next()).longValue();}

    @Test void manualSettingIsImmediateNoResetAndSameValueIsNoOp() throws Exception {
        int id=create(0);var state=db.state();UUID actor=UUID.randomUUID();
        db.sql("UPDATE machine_period_stats SET total_games=12,today_difference=99 WHERE machine_id=?",id);
        assertEquals(6,store.setSetting(state,id,6,actor,NOW+1));
        assertEquals(6,db.state().machine(id).setting());
        assertEquals(12,scalar("SELECT total_games FROM machine_period_stats WHERE machine_id=?",id));
        assertEquals(1,scalar("SELECT count(*) FROM setting_history WHERE machine_id=?",id));
        var row=db.rows("SELECT reason,actor_uuid FROM setting_history WHERE machine_id=?",id).getFirst();
        assertEquals("MANUAL",row.get("reason"));assertEquals(actor.toString(),row.get("actor_uuid"));
        store.setSetting(db.state(),id,6,actor,NOW+2);
        assertEquals(1,scalar("SELECT count(*) FROM setting_history WHERE machine_id=?",id));
    }

    @Test void autoEnabledAndDailyResetPreserveSettingHistoryAndAssetsTables() throws Exception {
        int id=create(0);var state=db.state();
        store.setSetting(state,id,5,null,NOW+1);state=db.state();
        store.setAuto(state,id,false,NOW+2);state=db.state();store.setEnabled(state,id,false,NOW+3);
        db.sql("UPDATE machine_period_stats SET total_games=33,big_count=2,reg_count=1,current_games=7,today_difference=123,today_max_difference=456,last_bonus_type='BIG',last_bonus_at=? WHERE machine_id=?",NOW,id);
        db.sql("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(?,?, 'BIG',7,?)",id,state.period(),NOW);
        db.sql("INSERT INTO juggler_god_history(machine_id,business_period_id,event_type,games,occurred_at) VALUES(?,?, 'GOD',7,?)",id,state.period(),NOW);
        db.sql("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(?,?,1,3,?)",id,state.period(),NOW);
        store.resetDaily(db.state(),id,NOW+4);
        Machine machine=db.state().machine(id);assertEquals(5,machine.setting());assertFalse(machine.autoSetting());assertFalse(machine.enabled());
        assertEquals(1,scalar("SELECT count(*) FROM setting_history WHERE machine_id=?",id));
        assertEquals(0,scalar("SELECT total_games FROM machine_period_stats WHERE machine_id=?",id));
        assertEquals(0,scalar("SELECT count(*) FROM bonus_history WHERE machine_id=? AND business_period_id=?",id,state.period()));
        assertEquals(0,scalar("SELECT count(*) FROM juggler_god_history WHERE machine_id=? AND business_period_id=?",id,state.period()));
        assertEquals(1,scalar("SELECT count(*) FROM graph_points WHERE machine_id=? AND business_period_id=?",id,state.period()));
        assertEquals(0,scalar("SELECT game FROM graph_points WHERE machine_id=? AND business_period_id=?",id,state.period()));
    }

    @Test void resetAllIsOneTransactionAndNextProfileCanBeSetAndCleared() throws Exception {
        int a=create(0),b=create(1);var state=db.state();
        db.sql("UPDATE machine_period_stats SET total_games=9");
        store.resetDailyAll(state,List.of(a,b),NOW+1);
        assertEquals(0,scalar("SELECT sum(total_games) FROM machine_period_stats"));
        store.setNextProfile("strong");
        assertEquals("strong",store.eventStatus(db.state()).get("nextProfile").getAsString());
        store.clearNextProfile();assertTrue(store.eventStatus(db.state()).get("nextProfile").isJsonNull());
        assertThrows(jp.pirijuggler.paper.machine.DomainException.class,()->store.setNextProfile("missing"));
    }
}
