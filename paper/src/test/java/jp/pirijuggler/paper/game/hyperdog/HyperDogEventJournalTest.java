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
    private static HyperDogFullGame phase(HyperDogFullGame template,HyperDogAtGame.Stage stage){
        var snapshot=template.snapshot();
        var at=snapshot.getAsJsonObject("at");
        at.addProperty("stage",stage.name());
        at.addProperty("initialIndex",0);
        return HyperDogFullGame.restore(snapshot);
    }
    @Test void upperContinuationsCountEveryCzSuccessWithoutExtraInitialHits(){
        var initial=new HyperDogFullGame(3,128,100000);
        initial.bet();
        initial.leverForcedNormal(HyperDogNormalGame.Role.BAR);
        initial.finish();
        assertEquals(HyperDogAtGame.Stage.RED7_CONFIRM,initial.at().stage());
        var red=phase(initial,HyperDogAtGame.Stage.RED7_CONFIRM);
        var upperInitial=phase(initial,HyperDogAtGame.Stage.UPPER_INITIAL);
        var upperAt=phase(initial,HyperDogAtGame.Stage.UPPER_AT);
        var cz=phase(initial,HyperDogAtGame.Stage.UPPER_CZ);
        var lowerInitial=phase(initial,HyperDogAtGame.Stage.LOWER_INITIAL);
        java.util.function.BiFunction<HyperDogFullGame,HyperDogFullGame,
                List<HyperDogEventJournal.Event>> event=HyperDogEventJournal::classify;
        assertEquals(List.of(new HyperDogEventJournal.Event(HyperDogEventJournal.Type.UPPER_ENTRY,0)),
                event.apply(red,upperInitial));
        assertTrue(event.apply(upperInitial,upperAt).isEmpty(),
                "entering paid upper AT from free 5G must not double count");
        assertEquals(List.of(new HyperDogEventJournal.Event(HyperDogEventJournal.Type.CZ_CHALLENGE,0)),
                event.apply(upperAt,cz),"5G CZ is one attempt, not five");
        var continued=event.apply(cz,upperInitial);
        assertEquals(1,continued.stream().filter(e->e.type()==HyperDogEventJournal.Type.UPPER_ENTRY).count());
        assertEquals(1,continued.stream().filter(e->e.type()==HyperDogEventJournal.Type.CZ_SUCCESS).count());
        assertFalse(continued.stream().anyMatch(e->e.type()==HyperDogEventJournal.Type.AT_FIRST_HIT));
        assertEquals(List.of(new HyperDogEventJournal.Event(HyperDogEventJournal.Type.CZ_FAILURE,0)),
                event.apply(cz,lowerInitial));
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
