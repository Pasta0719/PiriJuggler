package jp.pirijuggler.paper.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.game.RoleWeights;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class MachineDataSimulatorContinuityTest {
    @TempDir Path directory;
    PiriDatabase db;
    Map<String,Object> config;
    RoleWeights weights;
    static final long NOW=1_700_000_000_000L;

    static final class HighRandom implements RandomGenerator {
        @Override public long nextLong(){ return Long.MAX_VALUE; }
        @Override public int nextInt(int bound){ return bound-1; }
        @Override public long nextLong(long bound){ return bound-1; }
    }

    @BeforeEach void open() throws Exception {
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"))){
            config=ConfigValidation.load(reader).values();
        }
        weights=new RoleWeights(config);
        db=new PiriDatabase(directory.resolve("piri.db"));
        db.open(1,NOW,config,new SplittableRandom(1),ignored->{});
    }

    @AfterEach void close() throws Exception { db.close(); }

    private int create(MachineType type,int x)throws Exception{
        return db.create(new Machine.Location(UUID.randomUUID(),"world",x,64,0,"NORTH"),type,NOW);
    }
    private String period()throws Exception{return db.state().period();}
    private String key(int id)throws Exception{return "SIM_CURSOR:"+period()+":"+id;}
    private JsonObject cursor(int id)throws Exception{
        var rows=db.rows("SELECT value FROM metadata WHERE key=?",key(id));
        assertFalse(rows.isEmpty(),"simulation cursor must be persisted");
        return JsonParser.parseString((String)rows.getFirst().get("value")).getAsJsonObject();
    }
    private void putCursor(int id,String json)throws Exception{
        db.sql("INSERT INTO metadata(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",key(id),json);
    }
    private long stat(String column,int id)throws Exception{
        return ((Number)db.rows("SELECT "+column+" FROM machine_period_stats WHERE machine_id=? AND business_period_id=?",id,period()).getFirst().get(column)).longValue();
    }

    @Test void ordinaryPartialBigResumesBeforeAnyNewNormalGame() throws Exception {
        int id=create(MachineType.JUGGLER,1);
        putCursor(id,"{\"version\":2,\"kind\":\"JUGGLER\",\"freeReplay\":false,\"activeBonus\":\"BIG\",\"bonusRemaining\":3,\"entryCharged\":false}");

        var first=MachineDataSimulator.run(directory.resolve("piri.db"),weights,id,1,2,period(),new HighRandom(),NOW+10);
        assertEquals(2,first.games());
        assertEquals(23,stat("today_difference",id),"entry bet once plus two +12 BIG rounds");
        assertEquals(1,cursor(id).get("bonusRemaining").getAsInt());
        assertEquals(0,stat("total_games",id),"resumed bonus must run before any new normal game");

        var second=MachineDataSimulator.run(directory.resolve("piri.db"),weights,id,1,1,period(),new HighRandom(),NOW+20);
        assertEquals(1,second.games());
        assertEquals(35,stat("today_difference",id));
        assertEquals("NONE",cursor(id).get("activeBonus").getAsString());
        assertEquals(0,stat("current_games",id),"finishing the carried BIG resets current games");
        assertEquals(0,stat("total_games",id));
    }

    @Test void jugglerGodCarriesPartialBonusAndExactStockOrderAcrossCommands() throws Exception {
        int id=create(MachineType.JUGGLER_GOD,2);
        putCursor(id,"{\"version\":3,\"kind\":\"JUGGLER_GOD\",\"mode\":\"HEAVEN\",\"heavenTarget\":7,\"heavenProgress\":7,"+
                "\"freeReplay\":false,\"activeBonus\":\"BIG\",\"activeRemaining\":2,\"activeInsideGod\":false,\"activeNeedsEntry\":true,\"activeEntryCharged\":true,\"activeHistoryGames\":9,"+
                "\"stock\":[\"REG\",\"BIG\"],\"stockInsideGod\":false,\"postBonusPending\":true,\"postBonusOrigin\":\"HEAVEN\",\"postBonusType\":\"BIG\","+
                "\"godChainActive\":false,\"godStartPending\":false,\"godGuaranteedRemaining\":0,\"continuationGamePending\":false,\"heavenAfterGod\":false,\"compensationBig\":0}");

        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),new HighRandom(),NOW+30);
        JsonObject afterOne=cursor(id);
        assertEquals(1,afterOne.get("activeRemaining").getAsInt());
        assertEquals("REG",afterOne.getAsJsonArray("stock").get(0).getAsString());
        assertEquals("BIG",afterOne.getAsJsonArray("stock").get(1).getAsString());

        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),new HighRandom(),NOW+40);
        JsonObject boundary=cursor(id);
        assertEquals("NONE",boundary.get("activeBonus").getAsString());
        assertEquals(2,boundary.getAsJsonArray("stock").size(),"stock must not be auto-consumed after the exact cutoff");

        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),new HighRandom(),NOW+50);
        JsonObject resumed=cursor(id);
        assertEquals("REG",resumed.get("activeBonus").getAsString(),"first acquired stock must resume first");
        assertEquals(7,resumed.get("activeRemaining").getAsInt());
        assertEquals(1,resumed.getAsJsonArray("stock").size());
        assertEquals("BIG",resumed.getAsJsonArray("stock").get(0).getAsString());
    }

    @Test void pendingGodStartsCorrectLengthBigOnNextCommandForStandardAndExtreme() throws Exception {
        int standard=create(MachineType.JUGGLER_GOD,3);
        int extreme=create(MachineType.JUGGLER_GOD_EXTREME,4);
        String baseStd="{\"version\":3,\"kind\":\"JUGGLER_GOD\",\"mode\":\"NORMAL\",\"godStartPending\":true,\"heavenAfterGod\":true,\"stock\":[]}";
        String baseExt="{\"version\":3,\"kind\":\"JUGGLER_GOD_EXTREME\",\"mode\":\"NORMAL\",\"godStartPending\":true,\"heavenAfterGod\":true,\"stock\":[]}";
        putCursor(standard,baseStd);putCursor(extreme,baseExt);

        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,standard,1,1,period(),new HighRandom(),NOW+60);
        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,extreme,1,1,period(),new HighRandom(),NOW+70);

        JsonObject s=cursor(standard),e=cursor(extreme);
        assertEquals("BIG",s.get("activeBonus").getAsString());
        assertEquals(19,s.get("activeRemaining").getAsInt(),"standard BIG is 20 payout rounds");
        assertEquals(4,s.get("godGuaranteedRemaining").getAsInt());
        assertEquals("BIG",e.get("activeBonus").getAsString());
        assertEquals(29,e.get("activeRemaining").getAsInt(),"EXTREME BIG is 30 payout rounds");
        assertEquals(7,e.get("godGuaranteedRemaining").getAsInt());
    }

    @Test void guaranteedGodBigDoesNotDisappearAtCommandBoundary() throws Exception {
        int id=create(MachineType.JUGGLER_GOD,5);
        putCursor(id,"{\"version\":3,\"kind\":\"JUGGLER_GOD\",\"mode\":\"NORMAL\",\"activeBonus\":\"NONE\",\"activeRemaining\":0,\"stock\":[],"+
                "\"godChainActive\":true,\"godStartPending\":false,\"godGuaranteedRemaining\":1,\"continuationGamePending\":false,\"heavenAfterGod\":true,\"compensationBig\":0}");

        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),new HighRandom(),NOW+80);
        JsonObject c=cursor(id);
        assertEquals("BIG",c.get("activeBonus").getAsString());
        assertEquals(19,c.get("activeRemaining").getAsInt());
        assertEquals(0,c.get("godGuaranteedRemaining").getAsInt());
        assertTrue(c.get("godChainActive").getAsBoolean());
        assertEquals(1,stat("big_count",id),"guaranteed BIG hit is recorded exactly once");
    }
}
