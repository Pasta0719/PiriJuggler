package jp.pirijuggler.paper.data;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogDataLampTest {
    @Test void threeCountersReplaceBigRegOnlyForHyperDog()throws Exception{
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            c.createStatement().execute("CREATE TABLE machine_period_stats(machine_id INTEGER,business_period_id TEXT,total_games INTEGER,big_count INTEGER,reg_count INTEGER,current_games INTEGER,today_difference INTEGER,today_max_difference INTEGER)");
            c.createStatement().execute("CREATE TABLE bonus_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,bonus_type TEXT,games INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("CREATE TABLE graph_points(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,game INTEGER,difference INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("CREATE TABLE hyper_dog_events(machine_id INTEGER,business_period_id TEXT,event_id TEXT,event_type TEXT,medals INTEGER,occurred_at INTEGER,PRIMARY KEY(machine_id,event_id))");
            c.createStatement().execute("INSERT INTO machine_period_stats VALUES(5,'today',125,0,0,8,2500,3600)");
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(5,'today',0,0,1)");
            for(int i=0;i<4;i++)c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(5,'today','hit"+i+"','AT_FIRST_HIT',0,"+(10+i)+")");
            for(int i=0;i<3;i++)c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(5,'today','cz"+i+"','CZ_CHALLENGE',0,"+(20+i)+")");
            for(int i=0;i<5;i++)c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(5,'today','up"+i+"','UPPER_ENTRY',0,"+(30+i)+")");
            c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(5,'old','old','AT_FIRST_HIT',0,1)");
            c.createStatement().execute("INSERT INTO hyper_dog_events VALUES(6,'today','another','AT_FIRST_HIT',0,1)");
            var j=HyperDogDataLamp.read(c,5,"today");
            assertEquals(4,j.get("atFirstHits").getAsInt());
            assertEquals(3,j.get("czAttempts").getAsInt());
            assertEquals(5,j.get("upperEntries").getAsInt(),"upper AT includes continuation entries");
            assertEquals(0,j.get("bigCount").getAsInt());
            assertEquals(0,j.get("regCount").getAsInt());
            assertEquals(125,j.get("totalGames").getAsInt());
            assertEquals(0,j.getAsJsonArray("history").size(),
                    "legacy AT/CZ internals must not leak into new RUSH/CZ/HYPER history");
            assertEquals(0,j.get("rushCount").getAsInt());
            assertEquals(0,j.get("hyperCount").getAsInt());
            assertEquals(4,j.get("atCount").getAsInt());
            assertFalse(j.toString().contains("setting"));
            assertFalse(j.toString().contains("rngState"));
        }
    }
}
