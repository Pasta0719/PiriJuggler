package jp.pirijuggler.paper.data;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.game.RandomStreams;
import jp.pirijuggler.paper.game.RoleWeights;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import java.nio.file.Path;
import java.util.Map;

/** Admin-only /piri sim <machineId> <games>: advances real current-period machine data. */
public final class RealDataSimCommand {
    private final PiriJugglerPlugin plugin;
    private final RoleWeights weights;
    private boolean running;

    public RealDataSimCommand(PiriJugglerPlugin plugin, Map<String,Object> config) {
        this.plugin=plugin;
        this.weights=new RoleWeights(config);
    }

    public boolean handle(CommandSender sender,String[] args) {
        if(args.length==0 || !args[0].equalsIgnoreCase("sim")) return false;
        if(!sender.isOp()){sender.sendMessage(Component.text("NOT_OP"));return true;}
        if(args.length!=3){sender.sendMessage(Component.text("Usage: /piri sim <machineId> <games>"));return true;}
        final int machineId;
        final long count;
        try { machineId=Integer.parseInt(args[1]); count=Long.parseLong(args[2]); }
        catch(NumberFormatException bad){sender.sendMessage(Component.text("INVALID_STATE"));return true;}
        if(count<1 || count>100_000L){sender.sendMessage(Component.text("INVALID_STATE"));return true;}
        var state=plugin.machines().snapshot();
        var machine=state.machine(machineId);
        if(machine==null){sender.sendMessage(Component.text("INVALID_STATE"));return true;}
        if(state.busy(machineId)){sender.sendMessage(Component.text("MACHINE_OCCUPIED"));return true;}
        if(running){sender.sendMessage(Component.text("BUSY"));return true;}
        running=true;
        int setting=machine.setting();
        String period=state.period();
        Path db=plugin.getDataFolder().toPath().resolve("piri.db");
        long now=System.currentTimeMillis();
        var rng=RandomStreams.production().runtimeSimulation();
        sender.sendMessage(Component.text("SIM_STARTED machine="+machineId+" setting="+setting+" games="+count));
        plugin.executors().simulator(
            ()->MachineDataSimulator.run(db,weights,machineId,setting,count,period,rng,now),
            (result,error)->{
                running=false;
                if(error!=null){
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,"Real data simulation failed",error);
                    sender.sendMessage(Component.text("DB_ERROR"));
                    return;
                }
                sender.sendMessage(Component.text("SIM_DONE machine="+result.machineId()+" setting="+result.setting()+" games="+result.games()+" BIG="+result.big()+" REG="+result.reg()+" DIFF="+result.difference()+" MAX="+result.maxDifference()+" CURRENT="+result.currentGames()));
            });
        return true;
    }
}
