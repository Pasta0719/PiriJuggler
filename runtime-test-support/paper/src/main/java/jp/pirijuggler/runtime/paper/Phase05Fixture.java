package jp.pirijuggler.runtime.paper;

import com.google.gson.*;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.sql.*;

/** Test-only funds/config fixture. Every actual BET/LEVER/STOP uses production handlers. */
public final class Phase05Fixture {
    private static long completed;private boolean busy;private static String failure;
    public Phase05Fixture(JavaPlugin helper) {Bukkit.getScheduler().runTaskTimer(helper,this::tick,5,5);}
    private void tick() {
        if(busy)return;Path path=Path.of(System.getProperty("piri.runtime.serverResult")).resolveSibling("fund-"+(completed+1)+".json");
        if(!Files.isRegularFile(path))return;
        var production=(PiriJugglerPlugin)Bukkit.getPluginManager().getPlugin("PiriJuggler");var player=Bukkit.getPlayerExact("PiriRuntimeTest");
        if(player==null||!production.canUseSlot(player.getUniqueId()))return;
        var session=production.machines().snapshot().session(player.getUniqueId());if(session==null)return;
        try {
            JsonObject request=JsonParser.parseString(Files.readString(path)).getAsJsonObject();boolean bonus=request.get("bonus").getAsBoolean();
            if(bonus&&production.machines().snapshot().busy(session.machine()))throw new IllegalStateException("Test setting change requires a free machine");
            if(!bonus&&session.number("credit")+session.number("held_medals")!=0)throw new IllegalStateException("Test funding is allowed only for an empty session");
            busy=true;
            production.executors().database(()->{
                Class.forName("org.sqlite.JDBC");
                try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+production.getDataFolder().toPath().resolve("piri.db"))) {
                    c.setAutoCommit(false);
                    String sql=bonus?"UPDATE machines SET setting=2 WHERE machine_id=?":"UPDATE player_sessions SET credit=2,held_medals=800 WHERE session_id=?";
                    try(var statement=c.prepareStatement(sql)){if(bonus)statement.setInt(1,session.machine());else statement.setString(1,session.id().toString());if(statement.executeUpdate()!=1)throw new SQLException("Fixture row absent");}
                    c.commit();
                }return null;
            },(unused,error)->{busy=false;if(error!=null)failure=error.toString();else completed++;});
        }catch(Exception error){failure=error.toString();}
    }
    public static JsonObject snapshot(){JsonObject b=new JsonObject();b.addProperty("funded",completed);b.addProperty("failure",failure);return b;}
}
