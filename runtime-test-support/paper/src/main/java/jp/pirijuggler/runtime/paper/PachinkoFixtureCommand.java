package jp.pirijuggler.runtime.paper;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.game.pachinko.PachinkoRuntime;
import jp.pirijuggler.paper.machine.MachineService;
import jp.pirijuggler.paper.machine.MachineType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;

/**
 * Test-only Phase10 state fixture. It changes only authoritative persisted pachinko
 * runtime; gameplay packets still travel through the production MachineService.
 */
public final class PachinkoFixtureCommand implements CommandExecutor {
    private final JavaPlugin helper;
    private final Field databaseField;
    private final Field stateField;

    public PachinkoFixtureCommand(JavaPlugin helper) {
        this.helper=helper;
        try {
            databaseField=MachineService.class.getDeclaredField("database");
            stateField=MachineService.class.getDeclaredField("state");
            databaseField.setAccessible(true);
            stateField.setAccessible(true);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length<1||args.length>2) {
            sender.sendMessage(Component.text("Usage: /piripachinko <normal|left_v|left_out|rush|right_out|right_1500|right_3000> [player]"));
            return true;
        }
        Player player=args.length==2?Bukkit.getPlayerExact(args[1]):sender instanceof Player p?p:null;
        if(player==null){sender.sendMessage(Component.text("PLAYER_REQUIRED"));return true;}
        PiriJugglerPlugin production=(PiriJugglerPlugin)Bukkit.getPluginManager().getPlugin("PiriJuggler");
        if(production==null||production.machines()==null||!production.machines().ready()){sender.sendMessage(Component.text("PACHINKO_FIXTURE_NOT_READY"));return true;}
        var snapshot=production.machines().snapshot();
        var session=snapshot.session(player.getUniqueId());
        if(session==null){sender.sendMessage(Component.text("PACHINKO_FIXTURE_NO_SESSION"));return true;}
        var machine=snapshot.machine(session.machine());
        if(machine==null||machine.type()!=MachineType.PACHINKO){sender.sendMessage(Component.text("PACHINKO_FIXTURE_WRONG_MACHINE"));return true;}
        final String mode=args[0].toLowerCase(java.util.Locale.ROOT);
        final long now=System.currentTimeMillis();
        final PachinkoRuntime current=PachinkoRuntime.fromJson(session.machineState()!=null?session.machineState().toString():machine.runtimeJson());
        final PachinkoRuntime next;
        try { next=fixture(current,mode,now); }
        catch(IllegalArgumentException invalid){sender.sendMessage(Component.text("PACHINKO_FIXTURE_INVALID_MODE"));return true;}

        production.executors().database(()->{
            PiriDatabase db=(PiriDatabase)databaseField.get(production.machines());
            db.sql("UPDATE player_sessions SET machine_state_json=?,last_activity=? WHERE session_id=?",next.toJsonString(),now,session.id().toString());
            db.sql("UPDATE machines SET machine_runtime_json=?,updated_at=? WHERE machine_id=?",next.toJsonString(),now,session.machine());
            return db.state();
        },(state,error)->{
            if(error!=null){helper.getLogger().warning("Pachinko fixture failed: "+error);sender.sendMessage(Component.text("PACHINKO_FIXTURE_FAIL"));return;}
            try { stateField.set(production.machines(),state); }
            catch(IllegalAccessException reflection){throw new IllegalStateException(reflection);}
            sender.sendMessage(Component.text("PACHINKO_FIXTURE_READY machine="+session.machine()+" mode="+mode+" sequence="+next.ballSequenceId()));
        });
        return true;
    }

    private static PachinkoRuntime fixture(PachinkoRuntime r,String mode,long now) {
        long held=Math.max(r.ballsHeld(),5000L);
        long loaned=Math.max(r.ballsLoaned(),held);
        long sequence=Math.addExact(r.ballSequenceId(),1L);
        long cumulative=Math.max(r.cumulativePayout(),1500L);
        return switch(mode) {
            case "normal" -> new PachinkoRuntime(PachinkoRuntime.Mode.NORMAL,held,loaned,r.totalFired(),r.totalStarts(),sequence,
                    PachinkoRuntime.Presentation.IDLE,false,PachinkoRuntime.InitialOutcome.NONE,false,r.rushWins(),
                    PachinkoRuntime.RightOutcome.NONE,0,r.cumulativePayout(),r.statistics(),now);
            case "left_v" -> new PachinkoRuntime(PachinkoRuntime.Mode.NORMAL,held,loaned,r.totalFired(),r.totalStarts(),sequence,
                    PachinkoRuntime.Presentation.LEFT_KURUN,true,PachinkoRuntime.InitialOutcome.NONE,false,r.rushWins(),
                    PachinkoRuntime.RightOutcome.NONE,0,r.cumulativePayout(),r.statistics(),now);
            case "left_out" -> new PachinkoRuntime(PachinkoRuntime.Mode.NORMAL,held,loaned,r.totalFired(),r.totalStarts(),sequence,
                    PachinkoRuntime.Presentation.LEFT_KURUN,false,PachinkoRuntime.InitialOutcome.NONE,false,r.rushWins(),
                    PachinkoRuntime.RightOutcome.NONE,0,r.cumulativePayout(),r.statistics(),now);
            case "rush" -> new PachinkoRuntime(PachinkoRuntime.Mode.RUSH,held,loaned,r.totalFired(),r.totalStarts(),sequence,
                    PachinkoRuntime.Presentation.IDLE,true,PachinkoRuntime.InitialOutcome.RUSH_1500,true,r.rushWins(),
                    PachinkoRuntime.RightOutcome.NONE,1500,cumulative,r.statistics(),now);
            case "right_out" -> new PachinkoRuntime(PachinkoRuntime.Mode.RUSH,held,loaned,r.totalFired(),r.totalStarts(),sequence,
                    PachinkoRuntime.Presentation.RIGHT_KURUN,true,PachinkoRuntime.InitialOutcome.RUSH_1500,true,r.rushWins(),
                    PachinkoRuntime.RightOutcome.OUT,0,cumulative,r.statistics(),now);
            case "right_1500" -> new PachinkoRuntime(PachinkoRuntime.Mode.RUSH,held,loaned,r.totalFired(),r.totalStarts(),sequence,
                    PachinkoRuntime.Presentation.RIGHT_KURUN,true,PachinkoRuntime.InitialOutcome.RUSH_1500,true,r.rushWins(),
                    PachinkoRuntime.RightOutcome.WIN_1500,0,cumulative,r.statistics(),now);
            case "right_3000" -> new PachinkoRuntime(PachinkoRuntime.Mode.RUSH,held,loaned,r.totalFired(),r.totalStarts(),sequence,
                    PachinkoRuntime.Presentation.RIGHT_KURUN,true,PachinkoRuntime.InitialOutcome.RUSH_1500,true,r.rushWins(),
                    PachinkoRuntime.RightOutcome.WIN_3000,0,cumulative,r.statistics(),now);
            default -> throw new IllegalArgumentException(mode);
        };
    }
}
