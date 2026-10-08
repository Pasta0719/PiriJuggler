package jp.pirijuggler.paper.data;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.game.RoleWeights;
import jp.pirijuggler.paper.game.SkillStopWeights;
import jp.pirijuggler.paper.machine.Machine;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * /piri sim <machineId> <games>: advances one machine's real current-period data.
 * /piri sim all <games>: advances every non-deleted machine by the same number of games.
 */
public final class MachineDataSimulationService {
    private final PiriJugglerPlugin plugin;
    private final RoleWeights weights;
    private final SkillStopWeights skillWeights;
    private final Map<String,Object> config;
    private boolean running;

    public MachineDataSimulationService(PiriJugglerPlugin plugin, Map<String,Object> config) {
        this.plugin=plugin;
        this.config=config;
        this.weights=new RoleWeights(config);
        this.skillWeights=new SkillStopWeights();
    }

    public boolean handle(CommandSender sender,String[] args) {
        if(args.length==0||!args[0].equalsIgnoreCase("sim"))return false;
        if(!sender.isOp()){sender.sendMessage(Component.text("NOT_OP"));return true;}
        if(args.length!=3&&args.length!=4){sender.sendMessage(Component.text("Usage: /piri sim <machineId|all> <games> [skillPercent]"));return true;}

        final long games;
        try{games=Long.parseLong(args[2]);}
        catch(NumberFormatException error){sender.sendMessage(Component.text("INVALID_STATE"));return true;}
        if(games<1||games>100_000L){sender.sendMessage(Component.text("INVALID_STATE (games: 1..100000)"));return true;}
        final int skillPercent;
        try{skillPercent=args.length==4?Integer.parseInt(args[3]):80;}
        catch(NumberFormatException error){sender.sendMessage(Component.text("INVALID_STATE (skillPercent: 0..100)"));return true;}
        if(skillPercent<0||skillPercent>100){sender.sendMessage(Component.text("INVALID_STATE (skillPercent: 0..100)"));return true;}
        if(running){sender.sendMessage(Component.text("BUSY"));return true;}

        var state=plugin.machines().snapshot();
        if(args[1].equalsIgnoreCase("all"))return simulateAll(sender,state,games,skillPercent);

        final int machineId;
        try{machineId=Integer.parseInt(args[1]);}
        catch(NumberFormatException error){sender.sendMessage(Component.text("INVALID_STATE"));return true;}
        var machine=state.machine(machineId);
        if(machine==null){sender.sendMessage(Component.text("INVALID_STATE"));return true;}
        if(state.sessions().stream().anyMatch(session->session.ownsLock())){
            sender.sendMessage(Component.text("LIVE_PLAY_ACTIVE"));
            return true;
        }
        if(!supported(machine)){
            sender.sendMessage(Component.text("SIM_UNSUPPORTED machine="+machineId+" type="+machine.type()));
            return true;
        }

        int setting=machine.setting();
        String period=state.period();
        var dbFile=plugin.getDataFolder().toPath().resolve("piri.db");
        var random=new SplittableRandom(new SecureRandom().nextLong());
        running=true;
        sender.sendMessage(Component.text("SIMULATION_STARTED machine="+machineId+" setting="+setting+" targetSpins="+games+(machine.type()==jp.pirijuggler.paper.machine.MachineType.SKILL_STOP?" skill="+skillPercent+"%":"")));
        plugin.executors().simulator(
                ()->(machine.type()==jp.pirijuggler.paper.machine.MachineType.JUGGLER_GOD||machine.type()==jp.pirijuggler.paper.machine.MachineType.JUGGLER_GOD_EXTREME)
                        ?JugglerGodMachineDataSimulator.run(dbFile,weights,config,machineId,setting,games,period,random,System.currentTimeMillis())
                        :machine.type()==jp.pirijuggler.paper.machine.MachineType.SKILL_STOP
                        ?SkillStopMachineDataSimulator.run(dbFile,skillWeights,machineId,setting,games,period,random,System.currentTimeMillis(),skillPercent)
                        :MachineDataSimulator.run(dbFile,weights,machineId,setting,games,period,random,System.currentTimeMillis()),
                (result,error)->{
                    running=false;
                    if(error!=null){
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,"Real machine data simulation failed",error);
                        sender.sendMessage(Component.text(error.getMessage()==null?"DB_ERROR":error.getMessage()));
                        return;
                    }
                    sender.sendMessage(Component.text("SIMULATION_DONE machine="+result.machineId()+" setting="+result.setting()+" spins="+result.games()+" BIG="+result.big()+" REG="+result.reg()+" DIFF="+signed(result.difference())+" MAX="+signed(result.maxDifference())+" CURRENT="+result.currentGames()));
                });
        return true;
    }

    private boolean simulateAll(CommandSender sender, jp.pirijuggler.paper.database.PiriDatabase.State state, long games, int skillPercent) {
        List<Machine> all=state.machines().stream().filter(machine->!machine.deleted()).toList();
        if(all.isEmpty()){sender.sendMessage(Component.text("INVALID_STATE (no machines)"));return true;}
        List<Machine> unsupported=all.stream().filter(machine->!supported(machine)).toList();
        if(!unsupported.isEmpty()){
            sender.sendMessage(Component.text("SIM_UNSUPPORTED machines="+unsupported.stream().map(m->m.id()+":"+m.type()).toList()));
            return true;
        }
        List<Machine> machines=all;
        for(Machine machine:machines){
            if(state.busy(machine.id())){
                sender.sendMessage(Component.text("MACHINE_OCCUPIED id="+machine.id()));
                return true;
            }
        }

        String period=state.period();
        var dbFile=plugin.getDataFolder().toPath().resolve("piri.db");
        final long seed=new SecureRandom().nextLong();
        running=true;
        sender.sendMessage(Component.text("SIMULATION_ALL_STARTED machines="+machines.size()+" targetSpinsEach="+games+" skill="+skillPercent+"%"));

        plugin.executors().simulator(()->{
            List<MachineDataSimulator.Result> results=new ArrayList<>(machines.size());
            long now=System.currentTimeMillis();
            var masterRandom=new SplittableRandom(seed);
            for(int i=0;i<machines.size();i++){
                Machine machine=machines.get(i);
                var random=masterRandom.split();
                results.add((machine.type()==jp.pirijuggler.paper.machine.MachineType.JUGGLER_GOD||machine.type()==jp.pirijuggler.paper.machine.MachineType.JUGGLER_GOD_EXTREME)
                        ?JugglerGodMachineDataSimulator.run(dbFile,weights,config,machine.id(),machine.setting(),games,period,random,now+(long)i*games)
                        :machine.type()==jp.pirijuggler.paper.machine.MachineType.SKILL_STOP
                        ?SkillStopMachineDataSimulator.run(dbFile,skillWeights,machine.id(),machine.setting(),games,period,random,now+(long)i*games,skillPercent)
                        :MachineDataSimulator.run(dbFile,weights,machine.id(),machine.setting(),games,period,random,now+(long)i*games));
            }
            return results;
        },(results,error)->{
            running=false;
            if(error!=null){
                plugin.getLogger().log(java.util.logging.Level.SEVERE,"All-machine data simulation failed",error);
                sender.sendMessage(Component.text(error.getMessage()==null?"DB_ERROR":error.getMessage()));
                return;
            }
            long totalBig=0,totalReg=0,totalDifference=0,totalSpins=0;
            for(var result:results){
                totalBig=Math.addExact(totalBig,result.big());
                totalReg=Math.addExact(totalReg,result.reg());
                totalDifference=Math.addExact(totalDifference,result.difference());
                totalSpins=Math.addExact(totalSpins,result.games());
                sender.sendMessage(Component.text("SIM machine="+result.machineId()+" setting="+result.setting()+" spins="+result.games()+" BIG="+result.big()+" REG="+result.reg()+" DIFF="+signed(result.difference())+" CURRENT="+result.currentGames()));
            }
            sender.sendMessage(Component.text("SIMULATION_ALL_DONE machines="+results.size()+" targetSpinsEach="+games+" actualSpins="+totalSpins+" BIG="+totalBig+" REG="+totalReg+" DIFF_SUM="+signed(totalDifference)));
        });
        return true;
    }

    private static boolean supported(Machine machine){
        return switch(machine.type()){
            case JUGGLER,JUGGLER_GOD,JUGGLER_GOD_EXTREME,SKILL_STOP -> true;
            case GOD,OKIDOKI,DISC,PACHINKO -> false;
        };
    }

    private static String signed(long value){return value>0?"+"+value:Long.toString(value);}
}
