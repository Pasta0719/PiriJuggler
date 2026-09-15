package jp.pirijuggler.paper.data;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.game.RoleWeights;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import java.security.SecureRandom;
import java.util.Map;
import java.util.SplittableRandom;

/** /piri sim <machineId> <games>: advances the selected machine's real current-period data. */
public final class MachineDataSimulationService {
    private final PiriJugglerPlugin plugin;
    private final RoleWeights weights;
    private boolean running;

    public MachineDataSimulationService(PiriJugglerPlugin plugin, Map<String,Object> config) {
        this.plugin=plugin;
        this.weights=new RoleWeights(config);
    }

    public boolean handle(CommandSender sender,String[] args) {
        if(args.length==0||!args[0].equalsIgnoreCase("sim"))return false;
        if(!sender.isOp()){sender.sendMessage(Component.text("NOT_OP"));return true;}
        if(args.length!=3){sender.sendMessage(Component.text("Usage: /piri sim <machineId> <games>"));return true;}
        final int machineId;final long games;
        try{machineId=Integer.parseInt(args[1]);games=Long.parseLong(args[2]);}
        catch(NumberFormatException error){sender.sendMessage(Component.text("INVALID_STATE"));return true;}
        if(games<1||games>100_000L){sender.sendMessage(Component.text("INVALID_STATE (games: 1..100000)"));return true;}
        var state=plugin.machines().snapshot();
        var machine=state.machine(machineId);
        if(machine==null){sender.sendMessage(Component.text("INVALID_STATE"));return true;}
        if(state.busy(machineId)){sender.sendMessage(Component.text("MACHINE_OCCUPIED"));return true;}
        if(running){sender.sendMessage(Component.text("BUSY"));return true;}

        int setting=machine.setting();
        String period=state.period();
        var dbFile=plugin.getDataFolder().toPath().resolve("piri.db");
        var random=new SplittableRandom(new SecureRandom().nextLong());
        running=true;
        sender.sendMessage(Component.text("SIMULATION_STARTED machine="+machineId+" setting="+setting+" games="+games));
        plugin.executors().database(
                ()->MachineDataSimulator.run(dbFile,weights,machineId,setting,games,period,random,System.currentTimeMillis()),
                (result,error)->{
                    running=false;
                    if(error!=null){
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,"Real machine data simulation failed",error);
                        sender.sendMessage(Component.text(error.getMessage()==null?"DB_ERROR":error.getMessage()));
                        return;
                    }
                    sender.sendMessage(Component.text("SIMULATION_DONE machine="+result.machineId()+" setting="+result.setting()+" games="+result.games()+" BIG="+result.big()+" REG="+result.reg()+" DIFF="+signed(result.difference())+" MAX="+signed(result.maxDifference())+" CURRENT="+result.currentGames()));
                });
        return true;
    }

    private static String signed(long value){return value>0?"+"+value:Long.toString(value);}
}
