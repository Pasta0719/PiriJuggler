package jp.pirijuggler.paper.data;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.EnvelopeCodec;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.protocol.Protocol;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Periodic read-only projection publisher for the Slot Screen data lamp. */
public final class DataLampPublisher implements AutoCloseable {
    private record SnapshotBatch(Map<UUID,com.google.gson.JsonObject> owners, Map<Integer,com.google.gson.JsonObject> remotes) {}
    private final PiriJugglerPlugin plugin;
    private final Path databaseFile;
    private final BukkitTask task;
    private boolean inFlight;
    private boolean warned;

    public DataLampPublisher(PiriJugglerPlugin plugin) {
        this.plugin=plugin;
        this.databaseFile=plugin.getDataFolder().toPath().resolve("piri.db");
        this.task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,40,20);
    }

    private void tick() {
        if(inFlight||!plugin.isEnabled())return;
        inFlight=true;
        plugin.executors().database(this::readAll,(packets,error)->{
            inFlight=false;
            if(!plugin.isEnabled())return;
            if(error!=null){
                if(!warned){warned=true;plugin.getLogger().warning("PIRI_DATA_LAMP_READ_FAILED "+error.getMessage());}
                return;
            }
            warned=false;
            for(var entry:packets.owners().entrySet()){
                var player=Bukkit.getPlayer(entry.getKey());
                if(player!=null&&player.isOnline())player.sendPluginMessage(plugin,Protocol.CHANNEL,EnvelopeCodec.encode(Envelope.current(PacketType.DATA_LAMP,entry.getValue())));
            }
            if(plugin.machines()!=null)for(var snapshot:packets.remotes().values())plugin.machines().updateRemoteDataLamp(snapshot);
        });
    }

    private SnapshotBatch readAll() throws Exception {
        var owners=new LinkedHashMap<UUID,com.google.gson.JsonObject>();
        var remotes=new LinkedHashMap<Integer,com.google.gson.JsonObject>();
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+databaseFile.toAbsolutePath())){
            String period=null;
            try(var ps=connection.prepareStatement("SELECT value FROM metadata WHERE key='current_business_period_id'");var rs=ps.executeQuery()){
                if(rs.next())period=rs.getString(1);
            }
            if(period==null||period.isBlank())return new SnapshotBatch(owners,remotes);

            try(var ps=connection.prepareStatement("""
                    SELECT s.machine_id,s.total_games,s.big_count,s.reg_count
                    FROM machine_period_stats s
                    JOIN machines m ON m.machine_id=s.machine_id
                    WHERE s.business_period_id=? AND m.deleted=0
                    ORDER BY s.machine_id
                    """)){
                ps.setString(1,period);
                try(var rs=ps.executeQuery()){
                    while(rs.next()){
                        var data=new com.google.gson.JsonObject();
                        data.addProperty("machineId",rs.getInt(1));
                        data.addProperty("totalGames",rs.getLong(2));
                        data.addProperty("bigCount",rs.getLong(3));
                        data.addProperty("regCount",rs.getLong(4));
                        remotes.put(rs.getInt(1),data);
                    }
                }
            }

            try(var ps=connection.prepareStatement("SELECT player_uuid,machine_id FROM player_sessions WHERE lifecycle='ACTIVE' ORDER BY machine_id");var rs=ps.executeQuery()){
                var byMachine=new LinkedHashMap<Integer,com.google.gson.JsonObject>();
                while(rs.next()){
                    UUID player=UUID.fromString(rs.getString(1));int machine=rs.getInt(2);
                    com.google.gson.JsonObject snapshot=byMachine.get(machine);
                    if(snapshot==null){snapshot=DataLampSnapshot.read(connection,machine,period);byMachine.put(machine,snapshot);}
                    owners.put(player,snapshot.deepCopy());
                }
            }
        }
        return new SnapshotBatch(owners,remotes);
    }

    @Override public void close(){task.cancel();inFlight=false;}
}
