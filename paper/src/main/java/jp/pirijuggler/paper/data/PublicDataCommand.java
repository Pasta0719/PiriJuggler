package jp.pirijuggler.paper.data;

import com.google.gson.JsonObject;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.machine.Machine;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Handles /piri data without requiring physical access to a machine. */
public final class PublicDataCommand {
    private final PiriJugglerPlugin plugin;
    public PublicDataCommand(PiriJugglerPlugin plugin){this.plugin=plugin;}

    public boolean handle(CommandSender sender,String[] args){
        if(args.length<1||!args[0].equalsIgnoreCase("data"))return false;
        if(args.length>2){tell(sender,"Usage: /piri data [machineId]");return true;}
        if(plugin.machines()==null||!plugin.machines().ready()){tell(sender,"DB_ERROR");return true;}

        String period=plugin.machines().snapshot().period();
        List<Integer> ids=new ArrayList<>();
        if(args.length==2){
            final int id;
            try{id=Integer.parseInt(args[1]);}catch(NumberFormatException invalid){tell(sender,"INVALID_MACHINE");return true;}
            Machine machine=plugin.machines().snapshot().machine(id);
            if(machine==null||!machine.enabled()){tell(sender,"INVALID_MACHINE");return true;}
            ids.add(id);
        }else{
            for(Machine machine:plugin.machines().snapshot().machines())if(!machine.deleted()&&machine.enabled())ids.add(machine.id());
            if(ids.isEmpty()){tell(sender,"No playable slot machines.");return true;}
        }

        var databasePath=plugin.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        plugin.executors().database(()->{
            List<JsonObject> snapshots=new ArrayList<>();
            try(var connection=DriverManager.getConnection("jdbc:sqlite:"+databasePath)){
                connection.createStatement().execute("PRAGMA query_only=ON");
                for(int id:ids)snapshots.add(DataLampSnapshot.read(connection,id,period));
            }
            return snapshots;
        },(snapshots,error)->{
            if(error!=null){plugin.getLogger().log(Level.SEVERE,"Public machine data query failed",error);tell(sender,"DB_ERROR");return;}
            if(sender instanceof Player player&&!player.isOnline())return;
            if(args.length==2){for(String line:PublicDataText.detail(snapshots.getFirst()))tell(sender,line);}
            else{
                tell(sender,"[Slot Data]");
                for(JsonObject snapshot:snapshots)tell(sender,PublicDataText.summary(snapshot));
                tell(sender,"Details: /piri data <machineId>");
            }
        });
        return true;
    }

    private static void tell(CommandSender sender,String message){sender.sendMessage(Component.text(message));}
}
