package jp.pirijuggler.paper.data;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataLampSnapshotTest {
    @Test void snapshotUsesOnlyRequestedPeriodNewestTenHistoryAndExactPiriChainBoundary() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            c.createStatement().execute("CREATE TABLE machine_period_stats(machine_id INTEGER,business_period_id TEXT,total_games INTEGER,big_count INTEGER,reg_count INTEGER,current_games INTEGER,today_difference INTEGER,today_max_difference INTEGER)");
            c.createStatement().execute("CREATE TABLE bonus_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,bonus_type TEXT,games INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("CREATE TABLE graph_points(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,game INTEGER,difference INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("INSERT INTO machine_period_stats VALUES(1,'old',999,99,88,7,9999,9999)");
            c.createStatement().execute("INSERT INTO machine_period_stats VALUES(1,'current',120,3,2,100,45,300)");
            c.createStatement().execute("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(1,'old','BIG',999,1)");
            for(int i=1;i<=12;i++)c.createStatement().execute("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(1,'current','"+(i%2==0?"BIG":"REG")+"',"+i+","+(1000+i)+")");
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'old',0,9999,1)");
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'current',0,0,1)");
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'current',120,-3,2)");
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'current',120,45,3)");

            var json=DataLampSnapshot.read(c,1,"current");
            assertEquals(120,json.get("totalGames").getAsLong());assertEquals(3,json.get("bigCount").getAsLong());assertEquals(2,json.get("regCount").getAsLong());
            assertEquals(100,json.get("currentGames").getAsLong());assertEquals(45,json.get("todayDifference").getAsLong());assertEquals(300,json.get("todayMaxDifference").getAsLong());assertTrue(json.get("piriChain").getAsBoolean());
            assertEquals(10,json.getAsJsonArray("history").size());assertEquals(12,json.getAsJsonArray("history").get(0).getAsJsonObject().get("games").getAsLong());assertEquals(3,json.getAsJsonArray("history").get(9).getAsJsonObject().get("games").getAsLong());
            assertEquals(3,json.getAsJsonArray("graph").size());assertEquals(0,json.getAsJsonArray("graph").get(0).getAsJsonObject().get("difference").getAsLong());assertEquals(45,json.getAsJsonArray("graph").get(2).getAsJsonObject().get("difference").getAsLong());

            c.createStatement().execute("UPDATE machine_period_stats SET current_games=101 WHERE machine_id=1 AND business_period_id='current'");
            assertFalse(DataLampSnapshot.read(c,1,"current").get("piriChain").getAsBoolean(),"101G must turn Piri Chain off");
        }
    }

    @Test void noCompletedBonusKeepsPiriChainOffEvenAtZeroGames() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            c.createStatement().execute("CREATE TABLE machine_period_stats(machine_id INTEGER,business_period_id TEXT,total_games INTEGER,big_count INTEGER,reg_count INTEGER,current_games INTEGER,today_difference INTEGER,today_max_difference INTEGER)");
            c.createStatement().execute("CREATE TABLE bonus_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,bonus_type TEXT,games INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("CREATE TABLE graph_points(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,game INTEGER,difference INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("INSERT INTO machine_period_stats VALUES(1,'p',0,0,0,0,0,0)");c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'p',0,0,1)");
            assertFalse(DataLampSnapshot.read(c,1,"p").get("piriChain").getAsBoolean());
        }
    }
}
