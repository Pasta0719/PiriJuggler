package jp.pirijuggler.paper.data;

import com.google.gson.JsonParser;
import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.game.SkillStopWeights;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SkillStopMachineDataSimulatorTest {
    @TempDir Path directory;
    PiriDatabase db;
    String period;
    int machineId;
    static final long NOW=1_700_000_000_000L;

    @BeforeEach void open() throws Exception {
        var path=Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml");
        Map<String,Object> config;
        try(var reader=Files.newBufferedReader(path)){config=ConfigValidation.load(reader).values();}
        db=new PiriDatabase(directory.resolve("piri.db"));
        db.open(1,NOW,config,new SplittableRandom(1),ignored->{});
        machineId=db.create(new Machine.Location(UUID.randomUUID(),"world",0,64,0,"NORTH"),MachineType.SKILL_STOP,NOW);
        period=db.state().period();
    }
    @AfterEach void close() throws Exception {db.close();}
    private String cursor() throws Exception {
        return (String)db.rows("SELECT value FROM metadata WHERE key=?","SIM_CURSOR:"+period+":"+machineId).getFirst().get("value");
    }
    @Test void skillPercentBoundsAreValidated() throws Exception {
        assertThrows(IllegalArgumentException.class,()->SkillStopMachineDataSimulator.run(directory.resolve("piri.db"),new SkillStopWeights(),machineId,1,1,period,new SplittableRandom(1),NOW+1,-1));
        assertThrows(IllegalArgumentException.class,()->SkillStopMachineDataSimulator.run(directory.resolve("piri.db"),new SkillStopWeights(),machineId,1,1,period,new SplittableRandom(1),NOW+1,101));
    }

    @Test void pendingAndBonusContinuationSurviveSeparateCommands() throws Exception {
        db.sql("INSERT INTO metadata(key,value) VALUES(?,?)",
            "SIM_CURSOR:"+period+":"+machineId,
            "{\"kind\":\"SKILL_STOP\",\"activeBonus\":\"BIG\",\"bonusRemaining\":3,\"entryCharged\":false}");
        var first=SkillStopMachineDataSimulator.run(directory.resolve("piri.db"),new SkillStopWeights(),machineId,1,2,period,new SplittableRandom(1),NOW+10);
        assertEquals(2,first.games());
        assertEquals(1,JsonParser.parseString(cursor()).getAsJsonObject().get("bonusRemaining").getAsInt());
        var second=SkillStopMachineDataSimulator.run(directory.resolve("piri.db"),new SkillStopWeights(),machineId,1,1,period,new SplittableRandom(2),NOW+20);
        assertEquals(1,second.games());
        assertEquals(0,JsonParser.parseString(cursor()).getAsJsonObject().get("bonusRemaining").getAsInt());
    }
}
