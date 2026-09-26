package jp.pirijuggler.paper.data;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataLampSnapshotTest {
    @Test void snapshotUsesOnlyRequestedPeriodUpToHundredHistoryAndExactPiriChainBoundary() throws Exception {
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
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'current',1,-3,2)");
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'current',120,-3,3)");
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'current',120,45,4)");

            var json=DataLampSnapshot.read(c,1,"current");
            assertEquals(120,json.get("totalGames").getAsLong());assertEquals(3,json.get("bigCount").getAsLong());assertEquals(2,json.get("regCount").getAsLong());
            assertEquals(100,json.get("currentGames").getAsLong());assertEquals(45,json.get("todayDifference").getAsLong());assertEquals(300,json.get("todayMaxDifference").getAsLong());assertTrue(json.get("piriChain").getAsBoolean());assertEquals(12,json.get("piriChainCount").getAsInt());
            assertEquals(12,json.getAsJsonArray("history").size());assertEquals(12,json.getAsJsonArray("history").get(0).getAsJsonObject().get("games").getAsLong());assertEquals(1,json.getAsJsonArray("history").get(11).getAsJsonObject().get("games").getAsLong());
            assertEquals(3,json.getAsJsonArray("graph").size());
            assertEquals(1,json.getAsJsonArray("graph").get(0).getAsJsonObject().get("game").getAsLong(),"visible graph starts at 1G, not the initialization point");
            assertEquals(120,json.getAsJsonArray("graph").get(2).getAsJsonObject().get("game").getAsLong(),"visible graph reaches the current total game");
            assertEquals(45,json.getAsJsonArray("graph").get(2).getAsJsonObject().get("difference").getAsLong());

            c.createStatement().execute("UPDATE machine_period_stats SET current_games=101 WHERE machine_id=1 AND business_period_id='current'");
            var ended=DataLampSnapshot.read(c,1,"current");assertFalse(ended.get("piriChain").getAsBoolean(),"101G must turn Piri Chain off");assertEquals(0,ended.get("piriChainCount").getAsInt(),"ended chain count is transient and resets to zero");
        }
    }

    @Test void chainCountStopsAtLastGapOver100AndDoesNotNeedPersistentState() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            c.createStatement().execute("CREATE TABLE machine_period_stats(machine_id INTEGER,business_period_id TEXT,total_games INTEGER,big_count INTEGER,reg_count INTEGER,current_games INTEGER,today_difference INTEGER,today_max_difference INTEGER)");
            c.createStatement().execute("CREATE TABLE bonus_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,bonus_type TEXT,games INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("CREATE TABLE graph_points(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,game INTEGER,difference INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("INSERT INTO machine_period_stats VALUES(1,'p',500,4,0,20,0,0)");
            c.createStatement().execute("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(1,'p','BIG',200,1)");
            c.createStatement().execute("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(1,'p','BIG',80,2)");
            c.createStatement().execute("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(1,'p','BIG',40,3)");
            c.createStatement().execute("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(1,'p','BIG',20,4)");
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'p',0,0,1)");
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'p',1,-3,2)");
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'p',480,-3,3)");
            c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'p',480,10,4)");
            var json=DataLampSnapshot.read(c,1,"p");assertTrue(json.get("piriChain").getAsBoolean());assertEquals(4,json.get("piriChainCount").getAsInt());
        }
    }

    @Test void godAndBonusHistoryStayInExactEventOrder() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            c.createStatement().execute("CREATE TABLE machine_period_stats(machine_id INTEGER,business_period_id TEXT,total_games INTEGER,big_count INTEGER,reg_count INTEGER,current_games INTEGER,today_difference INTEGER,today_max_difference INTEGER)");
            c.createStatement().execute("CREATE TABLE bonus_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,bonus_type TEXT,games INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("CREATE TABLE juggler_god_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,event_type TEXT,games INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("CREATE TABLE graph_points(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,game INTEGER,difference INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("INSERT INTO machine_period_stats VALUES(1,'p',50,2,1,0,100,200)");
            c.createStatement().execute("INSERT INTO juggler_god_history(machine_id,business_period_id,event_type,games,occurred_at) VALUES(1,'p','GOD',42,1000)");
            c.createStatement().execute("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(1,'p','BIG',0,1000)");
            c.createStatement().execute("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(1,'p','BIG',0,1001)");
            c.createStatement().execute("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(1,'p','REG',8,1002)");
            var json=DataLampSnapshot.read(c,1,"p");
            var h=json.getAsJsonArray("history");
            assertEquals(4,h.size());
            assertEquals("REG",h.get(0).getAsJsonObject().get("type").getAsString());
            assertEquals("BIG",h.get(1).getAsJsonObject().get("type").getAsString());
            assertEquals("GOD",h.get(2).getAsJsonObject().get("type").getAsString());
            assertEquals("BIG",h.get(3).getAsJsonObject().get("type").getAsString());
            assertEquals(0,h.get(3).getAsJsonObject().get("games").getAsInt());
        }
    }

    @Test void noCompletedBonusKeepsPiriChainOffEvenAtZeroGames() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")){
            c.createStatement().execute("CREATE TABLE machine_period_stats(machine_id INTEGER,business_period_id TEXT,total_games INTEGER,big_count INTEGER,reg_count INTEGER,current_games INTEGER,today_difference INTEGER,today_max_difference INTEGER)");
            c.createStatement().execute("CREATE TABLE bonus_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,bonus_type TEXT,games INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("CREATE TABLE graph_points(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER,business_period_id TEXT,game INTEGER,difference INTEGER,occurred_at INTEGER)");
            c.createStatement().execute("INSERT INTO machine_period_stats VALUES(1,'p',0,0,0,0,0,0)");c.createStatement().execute("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(1,'p',0,0,1)");
            var json=DataLampSnapshot.read(c,1,"p");assertFalse(json.get("piriChain").getAsBoolean());assertEquals(0,json.get("piriChainCount").getAsInt());assertTrue(json.getAsJsonArray("graph").isEmpty());
        }
    }
}
