package jp.pirijuggler.paper.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.database.StartupProfile;
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

    static final class SequenceRandom implements RandomGenerator {
        private final ArrayDeque<Integer> ints=new ArrayDeque<>();
        SequenceRandom(int... values){for(int value:values)ints.addLast(value);}
        @Override public long nextLong(){ return Long.MAX_VALUE; }
        @Override public int nextInt(int bound){
            if(ints.isEmpty())return bound-1;
            return Math.floorMod(ints.removeFirst(),bound);
        }
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

    private JsonObject liveGodRuntime(int machineId)throws Exception {
        String raw=(String)db.rows("SELECT machine_runtime_json FROM machines WHERE machine_id=?",machineId)
                .getFirst().get("machine_runtime_json");
        return raw==null||raw.isBlank()?new JsonObject():JsonParser.parseString(raw).getAsJsonObject();
    }

    @Test void godSimAdvancesHintThrough99AndRevealsOn100thMiss() throws Exception {
        int id=create(MachineType.JUGGLER_GOD,51);
        JsonObject seeded=liveGodRuntime(id);
        seeded.addProperty("settingHintChain",7);
        seeded.addProperty("settingHintGames",0);
        seeded.addProperty("settingHintColor","OFF");
        db.sql("UPDATE machines SET machine_runtime_json=? WHERE machine_id=?",seeded.toString(),id);
        // Guaranteed no bonus from HIGH random: samples normal miss over 100 spins.
        var first=JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,
                id,1,99,period(),new HighRandom(),NOW+900);
        assertEquals(99,first.games());
        JsonObject at99=liveGodRuntime(id);
        assertEquals(7,at99.get("settingHintChain").getAsInt());
        assertEquals(99,at99.get("settingHintGames").getAsInt());
        assertEquals("OFF",at99.get("settingHintColor").getAsString());
        assertEquals(99,stat("current_games",id));

        // 100th completed simulated third-stop is a miss: setting 1,
        // seven-chain hint is deterministically PURPLE.
        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,
                id,1,1,period(),new HighRandom(),NOW+1000);
        JsonObject at100=liveGodRuntime(id);
        assertEquals("PURPLE",at100.get("settingHintColor").getAsString());
        assertEquals(0,at100.get("settingHintChain").getAsInt());
        assertEquals(0,at100.get("settingHintGames").getAsInt());
        assertEquals(100,stat("current_games",id));

        // The public UI and the next actual session must see the same color.
        var seat=db.seat(UUID.randomUUID(),id,NOW+1100);
        assertEquals("PURPLE",seat.publicState().get("settingHintColor").getAsString());
    }

    @Test void godSimBonusAndNormalSpinsCarryHintAcrossSeparateCommands() throws Exception {
        int id=create(MachineType.JUGGLER_GOD_EXTREME,52);
        // Simulator's normal data includes bonus payout rounds as budget.
        // Starting a guaranteed simulated BIG counts one chain bonus.
        JsonObject synthetic=new JsonObject();
        synthetic.addProperty("version",4);
        synthetic.addProperty("kind","JUGGLER_GOD_EXTREME");
        com.google.gson.JsonArray stock=new com.google.gson.JsonArray();
        stock.add("BIG");
        synthetic.add("stock",stock);
        putCursor(id,synthetic.toString());
        var bonus=JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,
                id,1,30,period(),new HighRandom(),NOW+1200);
        assertEquals(1,bonus.big());
        JsonObject afterBonus=liveGodRuntime(id);
        assertEquals(1,afterBonus.get("settingHintChain").getAsInt());
        assertEquals(0,afterBonus.get("settingHintGames").getAsInt());
        assertEquals("OFF",afterBonus.get("settingHintColor").getAsString());

        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,
                id,1,99,period(),new HighRandom(),NOW+1300);
        JsonObject at99=liveGodRuntime(id);
        assertEquals(1,at99.get("settingHintChain").getAsInt());
        assertEquals(99,at99.get("settingHintGames").getAsInt());
        assertEquals(99,stat("current_games",id));
        var resumed=db.seat(UUID.randomUUID(),id,NOW+1400);
        assertEquals(99,resumed.machineState().get("settingHintGames").getAsInt());
    }

    @Test void godSimPreservesLiveGameplayEntitlementsWhileChangingOnlyHint() throws Exception {
        int id=create(MachineType.JUGGLER_GOD,53);
        JsonObject original=liveGodRuntime(id);
        original.addProperty("settingHintChain",3);
        original.addProperty("settingHintGames",41);
        original.addProperty("settingHintColor","OFF");
        db.sql("UPDATE machines SET machine_runtime_json=? WHERE machine_id=?",original.toString(),id);
        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,
                id,1,1,period(),new HighRandom(),NOW+1500);
        JsonObject updated=liveGodRuntime(id);
        assertEquals(42,updated.get("settingHintGames").getAsInt());
        for(String property:new String[]{"settingHintChain","settingHintGames","settingHintColor"}){
            original.remove(property);
            updated.remove(property);
        }
        assertEquals(original,updated,"simulation must not overwrite the real HEAVEN/GOD state");
    }

    @Test void simIgnoresAnUnrelatedActiveOrDisconnectedMachineSession() throws Exception {
        int other=create(MachineType.SKILL_STOP,61);
        int target=create(MachineType.JUGGLER_GOD,62);
        UUID player=UUID.randomUUID();
        db.seat(player,other,NOW+10);
        assertNotNull(MachineDataSimulationService.blockingSession(db.state(),other));
        assertNull(MachineDataSimulationService.blockingSession(db.state(),target),
                "unrelated live seat must not prevent simulation of the requested cabinet");

        db.disconnect(player,NOW+20,60_000,null);
        assertNotNull(MachineDataSimulationService.blockingSession(db.state(),other),
                "disconnect grace still owns the original cabinet lock");
        assertNull(MachineDataSimulationService.blockingSession(db.state(),target),
                "unrelated disconnected seat must not prevent simulation");

        // A genuinely occupied requested machine is still protected.
        assertNotNull(MachineDataSimulationService.blockingSession(db.state(),other));
    }

    @Test void simDoesNotCountSafeSuspendedSessionsAsOccupied() throws Exception {
        int id=create(MachineType.JUGGLER_GOD,63);
        UUID player=UUID.randomUUID();
        db.seat(player,id,NOW+10);
        assertNotNull(MachineDataSimulationService.blockingSession(db.state(),id));
        db.disconnect(player,NOW+20,60_000,null);
        db.expire(NOW+60_021);
        assertNull(MachineDataSimulationService.blockingSession(db.state(),id),
                "expired grace is SUSPENDED_SAFE and cannot block simulation");
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
    @Test void godChainStockRunsBeforeDeferredPreGodStock() throws Exception {
        int id=create(MachineType.JUGGLER_GOD,6);
        putCursor(id,"{\"version\":3,\"kind\":\"JUGGLER_GOD\",\"mode\":\"NORMAL\",\"activeBonus\":\"NONE\",\"activeRemaining\":0,"+
                "\"stock\":[\"REG\"],\"godStock\":[\"BIG\"],\"stockInsideGod\":true,"+
                "\"godChainActive\":true,\"godStartPending\":false,\"godGuaranteedRemaining\":0,\"continuationGamePending\":false,\"heavenAfterGod\":true,\"compensationBig\":1}");

        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),new HighRandom(),NOW+90);
        JsonObject after=cursor(id);
        assertEquals("BIG",after.get("activeBonus").getAsString(),"GOD-in-GOD stock must release inside the chain first");
        assertEquals("REG",after.getAsJsonArray("stock").get(0).getAsString(),"pre-GOD stock must remain deferred");
        assertEquals(0,after.getAsJsonArray("godStock").size());
        assertEquals(1,after.get("compensationBig").getAsInt(),"compensation also waits until the parent GOD chain ends");
    }

    @Test void bonusOverlayDuringGodChainStaysInGodChainQueue() throws Exception {
        int id=create(MachineType.JUGGLER_GOD,7);
        putCursor(id,"{\"version\":3,\"kind\":\"JUGGLER_GOD\",\"mode\":\"NORMAL\","+
                "\"activeBonus\":\"BIG\",\"activeRemaining\":2,\"activeInsideGod\":true,\"activeNeedsEntry\":false,\"activeEntryCharged\":false,\"activeHistoryGames\":0,"+
                "\"stock\":[],\"godStock\":[],\"stockInsideGod\":false,\"godChainActive\":true,\"godStartPending\":false,\"godGuaranteedRemaining\":1,"+
                "\"continuationGamePending\":false,\"heavenAfterGod\":true,\"compensationBig\":0}");

        // Derive the first BIG interval from the current setting instead of freezing an old weight.
        var probabilities=StartupProfile.map(StartupProfile.map(config.get("probabilities")).get("settings"));
        var roles=StartupProfile.map(probabilities.get("1"));
        var god=StartupProfile.map(config.get("juggler_god"));
        var setting=StartupProfile.map(StartupProfile.map(god.get("settings")).get("1"));
        long smallScale=((Number)setting.get("small_role_scale_ppm")).longValue();
        int bigRoll=Math.toIntExact(((Number)roles.get("replay")).longValue()
                +((Number)roles.get("grape")).longValue()*smallScale/1_000_000
                +((Number)roles.get("bell")).longValue()*smallScale/1_000_000
                +((Number)roles.get("cherry")).longValue()*smallScale/1_000_000
                +((Number)roles.get("piero")).longValue()*smallScale/1_000_000);
        assertEquals(jp.pirijuggler.paper.reel.InternalRole.BIG,
                weights.drawJugglerGod(1,new SequenceRandom(bigRoll),
                        ((Number)setting.get("bonus_scale_ppm")).intValue(),(int)smallScale));
        // First int avoids GOD; second draws BIG during the carried GOD-chain bonus.
        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),
                new SequenceRandom(1,bigRoll),NOW+100);

        JsonObject after=cursor(id);
        assertEquals(1,after.getAsJsonArray("godStock").size());
        assertEquals("BIG",after.getAsJsonArray("godStock").get(0).getAsString());
        assertEquals(0,after.getAsJsonArray("stock").size(),"GOD-chain overlay must not leak into deferred post-GOD stock");
    }

    @Test void godHistorySimPersistsHighModeAndConsumesExactGameBudget() throws Exception {
        int id=create(MachineType.JUGGLER_GOD,8);
        putCursor(id,"{\"version\":4,\"kind\":\"JUGGLER_GOD\",\"mode\":\"HIGH\",\"hotRemaining\":3,\"roleStreak\":0}");
        for(int spin=1;spin<=3;spin++){
            var result=JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),
                    new HighRandom(),NOW+200+spin);
            assertEquals(1,result.games());
            var c=cursor(id);
            assertEquals(Math.max(0,3-spin),c.get("hotRemaining").getAsInt());
            assertEquals(spin==3?"NORMAL":"HIGH",c.get("mode").getAsString());
        }
        assertEquals(3,stat("total_games",id));
    }

    @Test void godHistorySimPromotesAfterTwoGrapesAndSurvivesCommandBoundary() throws Exception {
        int id=create(MachineType.JUGGLER_GOD,9);
        putCursor(id,"{\"version\":4,\"kind\":\"JUGGLER_GOD\",\"mode\":\"NORMAL\",\"roleStreak\":2}");
        RandomGenerator promote=new RandomGenerator(){
            @Override public long nextLong(){return 0L;}
            @Override public int nextInt(int bound){return bound-1;}
            @Override public double nextDouble(){return 0.0;}
        };
        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),promote,NOW+250);
        assertEquals("HIGH",cursor(id).get("mode").getAsString());
        assertEquals(19,cursor(id).get("hotRemaining").getAsInt());
        assertEquals(0,cursor(id).get("roleStreak").getAsInt());
        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),new HighRandom(),NOW+251);
        assertEquals("HIGH",cursor(id).get("mode").getAsString());
        assertEquals(18,cursor(id).get("hotRemaining").getAsInt());
    }

    @Test void godHistorySimActuallyRecordsHighModeBigRatherThanOrdinaryMysteryRate() throws Exception {
        int id=create(MachineType.JUGGLER_GOD,10);
        var profiles=StartupProfile.map(StartupProfile.map(config.get("probabilities")).get("settings"));
        var roles=StartupProfile.map(profiles.get("1"));
        var cfg=StartupProfile.map(StartupProfile.map(config.get("juggler_god")).get("settings"));
        var row=StartupProfile.map(cfg.get("1"));
        int small=((Number)row.get("small_role_scale_ppm")).intValue();
        int base=((Number)row.get("bonus_scale_ppm")).intValue();
        long raw=weights.unscaledBonusFamilyWeight(1);
        int high=(int)Math.round((1-Math.pow(.60,1.0/20.0))*1_000_000_000_000_000.0/raw);
        int bigRoll=Math.toIntExact(((Number)roles.get("replay")).longValue()
                +((Number)roles.get("grape")).longValue()*small/1_000_000L
                +((Number)roles.get("bell")).longValue()*small/1_000_000L
                +((Number)roles.get("cherry")).longValue()*small/1_000_000L
                +((Number)roles.get("piero")).longValue()*small/1_000_000L
                +((Number)roles.get("big")).longValue()*((long)base+high)/2_000_000L);
        assertNotEquals(jp.pirijuggler.paper.reel.InternalRole.BIG,
                weights.drawJugglerGod(1,new SequenceRandom(bigRoll),base,small));
        assertEquals(jp.pirijuggler.paper.reel.InternalRole.BIG,
                weights.drawJugglerGod(1,new SequenceRandom(bigRoll),high,small));
        putCursor(id,"{\"version\":4,\"kind\":\"JUGGLER_GOD\",\"mode\":\"HIGH\",\"hotRemaining\":4}");
        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),
                new SequenceRandom(1,bigRoll),NOW+280);
        assertEquals(1,stat("big_count",id),"high-mode BIG must appear in real machine statistics");
        assertEquals(1,stat("total_games",id));
        assertEquals("NORMAL",cursor(id).get("mode").getAsString(),"bonus consumes high mode");
        assertEquals(0,cursor(id).get("hotRemaining").getAsInt());
        var history=db.rows("SELECT bonus_type,games FROM bonus_history WHERE machine_id=? AND business_period_id=?",id,period());
        assertEquals(1,history.size(),"bonus must be in the real historical table");
        assertEquals("BIG",history.getFirst().get("bonus_type"));
    }

    @Test void extremeGodHistorySimAlsoPersistsUltraAndExpiresAfterFifteenGames() throws Exception {
        int id=create(MachineType.JUGGLER_GOD_EXTREME,11);
        putCursor(id,"{\"version\":4,\"kind\":\"JUGGLER_GOD_EXTREME\",\"mode\":\"ULTRA\",\"hotRemaining\":2}");
        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),new HighRandom(),NOW+300);
        assertEquals("ULTRA",cursor(id).get("mode").getAsString());
        assertEquals(1,cursor(id).get("hotRemaining").getAsInt());
        JugglerGodMachineDataSimulator.run(directory.resolve("piri.db"),weights,config,id,1,1,period(),new HighRandom(),NOW+301);
        assertEquals("NORMAL",cursor(id).get("mode").getAsString());
        assertEquals(0,cursor(id).get("hotRemaining").getAsInt());
    }

}
