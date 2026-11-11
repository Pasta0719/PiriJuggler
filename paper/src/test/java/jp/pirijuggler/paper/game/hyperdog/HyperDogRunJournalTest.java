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
