package jp.pirijuggler.paper.game.hyperdog;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogEventJournalTest {
    @Test void initialAndBarAreSeparateAndDoNotInventBigOrReg(){
        var game=new HyperDogFullGame(3,10,100000);
        assertTrue(HyperDogEventJournal.classify(null,game).isEmpty());
        game.bet();
        var bet=HyperDogFullGame.restore(game.snapshot());
        game.leverForcedNormal(HyperDogNormalGame.Role.BAR);
        var lever=HyperDogFullGame.restore(game.snapshot());
        game.finish();
        var entries=HyperDogEventJournal.classify(lever,game);
        assertTrue(entries.contains(new HyperDogEventJournal.Event(HyperDogEventJournal.Type.AT_FIRST_HIT,0)));
        assertTrue(entries.contains(new HyperDogEventJournal.Event(HyperDogEventJournal.Type.BAR,0)));
        assertFalse(entries.stream().anyMatch(e->e.medals()!=0));
        assertTrue(HyperDogEventJournal.classify(bet,lever).isEmpty());
    }
    @Test void historyIsMachineAndPeriodScopedAndHasNoPrivateValues()throws Exception{
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            c.createStatement().execute("CREATE TABLE hyper_dog_events(machine_id INTEGER NOT NULL,business_period_id TEXT NOT NULL,event_id TEXT NOT NULL,event_type TEXT NOT NULL,medals INTEGER NOT NULL,occurred_at INTEGER NOT NULL,PRIMARY KEY(machine_id,event_id))");
            c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(7,'today','1','AT_FIRST_HIT',0,100)");
            c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(7,'today','2','UPPER_ENTRY',0,110)");
            c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(7,'today','3','CZ_CHALLENGE',0,120)");
            c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(7,'today','4','CZ_SUCCESS',0,121)");
            c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(7,'today','5','AT_END',3456,130)");
            c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(7,'yesterday','6','AT_END',999999,50)");
            c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(8,'today','7','BAR',0,10)");
            var data=HyperDogEventJournal.read(c,7,"today");
            assertEquals(1,data.get("atFirstHits").getAsLong());
            assertEquals(1,data.get("upperEntries").getAsLong());
            assertEquals(1,data.get("czSuccesses").getAsLong());
            assertEquals(1,data.get("czAttempts").getAsLong());
            assertEquals(3456,data.get("totalAtNet").getAsLong());
            assertEquals(0,data.get("barCount").getAsLong());
            assertEquals(0,data.get("bigCount").getAsLong());
            assertEquals(0,data.get("regCount").getAsLong());
            assertEquals(5,data.getAsJsonArray("history").size());
            var plain=data.toString();
            for(String secret:List.of("rngState","savedHitMode","setting","internalRole","premium","stock"))
                assertFalse(plain.contains(secret));
            assertEquals(0,HyperDogEventJournal.read(c,9,"today").get("atFirstHits").getAsLong());
        }
    }
}
