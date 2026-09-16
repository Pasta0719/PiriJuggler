package jp.pirijuggler.paper.database;

import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.machine.Machine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StartupAllocationIntegrationTest {
    @TempDir Path directory;
    static final long NOW=1_700_000_000_000L;

    @Test void manualNextIsAppliedAsEventAndConsumedBySuccessfulRestartTransaction() throws Exception {
        Map<String,Object> config=loadConfig();Path file=directory.resolve("piri.db");
        PiriDatabase db=new PiriDatabase(file);db.open(1,NOW,config,new SplittableRandom(1),ignored->{});
        db.create(new Machine.Location(UUID.randomUUID(),"world",0,64,0,"NORTH"),NOW);
        db.sql("INSERT INTO metadata(key,value) VALUES('next_start_profile','strong')");db.close();

        db=new PiriDatabase(file);var state=db.open(2,NOW+1,config,new SplittableRandom(2),ignored->{});
        assertEquals("strong",state.profile());
        assertTrue(db.rows("SELECT value FROM metadata WHERE key='next_start_profile'").isEmpty());
        var period=db.rows("SELECT profile_source FROM business_periods WHERE business_period_id=?",state.period()).getFirst();
        assertEquals("MANUAL_NEXT",period.get("profile_source"));
        assertEquals("EVENT",db.rows("SELECT reason FROM setting_history WHERE business_period_id=?",state.period()).getFirst().get("reason"));
        db.close();
    }

    @Test void failedRestartDoesNotConsumeManualNextOrCreatePartialPeriod() throws Exception {
        Map<String,Object> config=loadConfig();Path file=directory.resolve("piri.db");
        PiriDatabase db=new PiriDatabase(file);db.open(1,NOW,config,new SplittableRandom(1),ignored->{});
        db.create(new Machine.Location(UUID.randomUUID(),"world",0,64,0,"NORTH"),NOW);
        db.sql("INSERT INTO metadata(key,value) VALUES('next_start_profile','missing-profile')");db.close();

        PiriDatabase failedDb=new PiriDatabase(file);
        assertThrows(IllegalArgumentException.class,()->failedDb.open(2,NOW+1,config,new SplittableRandom(2),ignored->{}));
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath())){
            try(var s=connection.prepareStatement("SELECT value FROM metadata WHERE key='next_start_profile'");var r=s.executeQuery()){
                assertTrue(r.next());assertEquals("missing-profile",r.getString(1));
            }
            try(var s=connection.prepareStatement("SELECT count(*) FROM business_periods");var r=s.executeQuery()){
                assertTrue(r.next());assertEquals(1,r.getInt(1));
            }
        }
    }

    private static Map<String,Object> loadConfig() throws Exception {
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"))){
            return ConfigValidation.load(reader).values();
        }
    }
}
