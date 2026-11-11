package jp.pirijuggler.paper.game.hyperdog;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogRunJournalTest {
    @Test void projectedCountsUseOnlyCurrentPeriodAndShowOneFinalShot()throws Exception{
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            c.createStatement().execute("CREATE TABLE hyper_dog_runs(run_id TEXT PRIMARY KEY,machine_id INTEGER,business_period_id TEXT,first_games INTEGER,medals INTEGER NOT NULL,final INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
            c.createStatement().execute("CREATE TABLE hyper_dog_run_entries(machine_id INTEGER,business_period_id TEXT,event_id TEXT,run_id TEXT,event_type TEXT,games INTEGER,medals INTEGER,occurred_at INTEGER,PRIMARY KEY(machine_id,event_id))");
            c.createStatement().execute("INSERT INTO hyper_dog_runs VALUES('shot',7,'today',86,4280,1,900)");
            c.createStatement().execute("INSERT INTO hyper_dog_runs VALUES('old-shot',7,'yesterday',2,9999,1,999)");
            c.createStatement().execute("INSERT INTO hyper_dog_run_entries VALUES(7,'today','a','shot','RUSH',86,4280,100)");
            c.createStatement().execute("INSERT INTO hyper_dog_run_entries VALUES(7,'today','b','shot','CZ',NULL,NULL,200)");
            c.createStatement().execute("INSERT INTO hyper_dog_run_entries VALUES(7,'today','c','shot','HYPER',NULL,4280,300)");
            c.createStatement().execute("INSERT INTO hyper_dog_run_entries VALUES(7,'today','d','shot','HYPER',NULL,4280,400)");
            c.createStatement().execute("INSERT INTO hyper_dog_run_entries VALUES(7,'yesterday','z','shot','RUSH',2,4280,500)");
            c.createStatement().execute("INSERT INTO hyper_dog_run_entries VALUES(8,'today','other','other','CZ',NULL,NULL,300)");
            var s=HyperDogRunJournal.read(c,7,"today");
            assertEquals(1,s.get("rushCount").getAsInt());
            assertEquals(2,s.get("hyperCount").getAsInt());
            assertEquals(1,s.get("czCount").getAsInt());
            assertTrue(s.get("shotExists").getAsBoolean());
            assertTrue(s.get("shotFinal").getAsBoolean());
            assertEquals(4280,s.get("shotNet").getAsInt());
            var list=s.getAsJsonArray("history");
            assertEquals(4,list.size());
            assertEquals("HYPER",list.get(0).getAsJsonObject().get("type").getAsString());
            assertEquals(86,list.get(3).getAsJsonObject().get("games").getAsInt());
            assertFalse(list.get(2).getAsJsonObject().has("medals"),"CZ is a marker, not an AT payout");
            assertFalse(list.get(0).getAsJsonObject().has("games"),"continuation entry has no G");
            assertFalse(s.toString().contains("rngState"));
            assertFalse(s.toString().contains("setting"));
        }
    }
    @Test void finalizedTotalIsHiddenAfterNextNormalGameAndNeverResurrected()throws Exception{
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            c.createStatement().execute("CREATE TABLE hyper_dog_runs(run_id TEXT PRIMARY KEY,machine_id INTEGER,business_period_id TEXT,first_games INTEGER,medals INTEGER NOT NULL,final INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
            c.createStatement().execute("CREATE TABLE hyper_dog_run_entries(machine_id INTEGER,business_period_id TEXT,event_id TEXT,run_id TEXT,event_type TEXT,games INTEGER,medals INTEGER,occurred_at INTEGER,PRIMARY KEY(machine_id,event_id))");
            c.createStatement().execute("INSERT INTO hyper_dog_runs VALUES('shot-1',7,'today',42,179,1,100)");
            c.createStatement().execute("INSERT INTO hyper_dog_run_entries VALUES(7,'today','first-hit','shot-1','RUSH',42,179,98)");
            var finalized=HyperDogRunJournal.read(c,7,"today");
            assertTrue(finalized.get("shotExists").getAsBoolean());
            assertTrue(finalized.get("shotFinal").getAsBoolean());
            assertEquals(179,finalized.get("shotNet").getAsInt());

            // The next completed normal paid spin is the only event that
            // changes finalized visibility from 1 (TOTAL) to 2 (hidden).
            c.createStatement().executeUpdate(
                    "UPDATE hyper_dog_runs SET final=2 WHERE run_id='shot-1' AND final=1");
            var hidden=HyperDogRunJournal.read(c,7,"today");
            assertFalse(hidden.get("shotExists").getAsBoolean());
            assertFalse(hidden.get("shotFinal").getAsBoolean());
            assertEquals(179,hidden.get("shotNet").getAsInt());
            assertEquals(1,hidden.getAsJsonArray("history").size());
            assertEquals(179,hidden.getAsJsonArray("history").get(0)
                    .getAsJsonObject().get("medals").getAsInt());

            // Later accepted actions and reconnects must never re-show TOTAL.
            c.createStatement().executeUpdate(
                    "UPDATE hyper_dog_runs SET medals=179,final=CASE WHEN final=2 THEN 2 ELSE 1 END,updated_at=102 WHERE run_id='shot-1'");
            assertFalse(HyperDogRunJournal.read(c,7,"today").get("shotExists").getAsBoolean());
            c.createStatement().execute("INSERT INTO hyper_dog_runs VALUES('shot-2',7,'today',43,0,0,103)");
            var nextShot=HyperDogRunJournal.read(c,7,"today");
            assertTrue(nextShot.get("shotExists").getAsBoolean());
            assertFalse(nextShot.get("shotFinal").getAsBoolean());
            assertEquals(0,nextShot.get("shotNet").getAsInt());
        }
    }

    @Test void missingLedgerReturnsEmptyInsteadOfLegacyBigReg()throws Exception{
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            var s=HyperDogRunJournal.read(c,7,"today");
            assertEquals(0,s.get("rushCount").getAsInt());
            assertEquals(0,s.get("hyperCount").getAsInt());
            assertEquals(0,s.getAsJsonArray("history").size());
            assertFalse(s.get("shotExists").getAsBoolean());
        }
    }
}
