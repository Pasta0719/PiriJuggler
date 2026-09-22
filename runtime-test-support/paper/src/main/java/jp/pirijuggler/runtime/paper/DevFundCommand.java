package jp.pirijuggler.runtime.paper;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.game.NormalGame;
import jp.pirijuggler.paper.game.PremiumPolicy;
import jp.pirijuggler.paper.game.RoleWeights;
import jp.pirijuggler.paper.game.GameEngines;
import jp.pirijuggler.paper.game.GameEngine;
import jp.pirijuggler.paper.game.JugglerGameEngine;
import jp.pirijuggler.paper.game.JugglerGodGameEngine;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.reel.InternalRole;
import jp.pirijuggler.paper.session.Session;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime-test-only funding / Phase06 forcing command. This class is never packaged in the production Paper JAR.
 * Every actual BET / LEVER / STOP still goes through the production handlers; this helper only overrides the next
 * normal-game role/premium draw long enough for a human runtime acceptance test to trigger it deterministically.
 */
public final class DevFundCommand implements CommandExecutor {
    private final JavaPlugin helper;
    private ForceOverride pendingForce;

    private record ForceOverride(UUID player, long baseSequence, InternalRole role, PremiumPolicy.Type premium,
                                 NormalGame game, RoleWeights originalWeights, PremiumPolicy originalPremium,
                                 long startedAtMs, CommandSender sender, Player target, BukkitTask watcher) {}

    public DevFundCommand(JavaPlugin helper) {
        this.helper = helper;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("NOT_OP");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("fund")) return fund(sender,args);
        if (args.length >= 1 && args[0].equalsIgnoreCase("force")) return force(sender,args);
        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            if (pendingForce == null) sender.sendMessage("NO_TEST_FORCE_PENDING");
            else restoreForce("TEST_FORCE_CLEARED");
            return true;
        }
        sender.sendMessage("Usage: /piritest fund [player] | /piritest force <god|big|reg|A|B|C|D|E|F> [player] | /piritest clear");
        return true;
    }

    private boolean fund(CommandSender sender,String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage("Usage: /piritest fund [player]");
            return true;
        }
        Player target = args.length == 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage("PLAYER_REQUIRED");
            return true;
        }

        var production = production(sender);
        if (production == null) return true;
        var session = production.machines().snapshot().session(target.getUniqueId());
        if (session == null) {
            sender.sendMessage("Open a registered Piri machine once, then run the command again.");
            return true;
        }
        if (!session.ready()) {
            sender.sendMessage("Finish or reset the current game before test funding.");
            return true;
        }

        String sessionId = session.id().toString();
        var dbPath = production.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        sender.sendMessage("Funding test session...");

        Bukkit.getScheduler().runTaskAsynchronously(helper, () -> {
            String result;
            try {
                Class.forName("org.sqlite.JDBC");
                try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                    try (var pragma = connection.createStatement()) {
                        pragma.execute("PRAGMA busy_timeout=5000");
                    }
                    try (var statement = connection.prepareStatement("UPDATE player_sessions SET credit=50,held_medals=800 WHERE session_id=?")) {
                        statement.setString(1, sessionId);
                        if (statement.executeUpdate() != 1) throw new IllegalStateException("Session row missing");
                    }
                }
                result = "TEST_FUNDED credit=50 held=800. Close the slot screen and right-click the same machine again to refresh.";
            } catch (Exception error) {
                result = "TEST_FUND_FAILED " + error;
            }
            String message = result;
            Bukkit.getScheduler().runTask(helper, () -> {
                sender.sendMessage(message);
                if (!sender.equals(target)) target.sendMessage(message);
            });
        });
        return true;
    }

    private boolean force(CommandSender sender,String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage("Usage: /piritest force <god|big|reg|A|B|C|D|E|F> [player]");
            return true;
        }
        if (pendingForce != null) {
            sender.sendMessage("TEST_FORCE_ALREADY_PENDING. Use /piritest clear first.");
            return true;
        }
        Player target = args.length == 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage("PLAYER_REQUIRED");
            return true;
        }
        var production = production(sender);
        if (production == null) return true;
        Session session = production.machines().snapshot().session(target.getUniqueId());
        if (session == null) {
            sender.sendMessage("Open a registered Piri machine once, then run the command again.");
            return true;
        }
        if (session.lifecycle()!=Session.Lifecycle.ACTIVE || !switch(session.state()) {
            case SEATED_READY, NORMAL_BETTED, REPLAY_READY -> true;
            default -> false;
        }) {
            sender.sendMessage("TEST_FORCE_REQUIRES_NORMAL_READY_STATE current="+session.state());
            return true;
        }

        String requested=args[1].toUpperCase(Locale.ROOT);
        InternalRole role;
        PremiumPolicy.Type premium=null;
        switch(requested) {
            case "GOD" -> role=InternalRole.GOD;
            case "BIG" -> role=InternalRole.BIG;
            case "REG" -> role=InternalRole.REG;
            case "A","C","D","E","F" -> {role=InternalRole.BIG;premium=PremiumPolicy.Type.valueOf(requested);}
            case "B" -> {role=InternalRole.CHERRY_BIG;premium=PremiumPolicy.Type.B;}
            default -> {
                sender.sendMessage("Usage: /piritest force <big|reg|A|B|C|D|E|F> [player]");
                return true;
            }
        }

        try {
            if(role==InternalRole.GOD){
                Machine machine=production.machines().snapshot().machine(session.machine());
                if(machine==null||machine.type()!=jp.pirijuggler.paper.machine.MachineType.JUGGLER_GOD){
                    sender.sendMessage("TEST_FORCE_GOD_REQUIRES_JUGGLER_GOD");
                    return true;
                }
            }
            NormalGame game=normalGame(production,session);
            Field weightsField=NormalGame.class.getDeclaredField("weights");weightsField.setAccessible(true);
            Field premiumField=NormalGame.class.getDeclaredField("premium");premiumField.setAccessible(true);
            RoleWeights originalWeights=(RoleWeights)weightsField.get(game);
            PremiumPolicy originalPremium=(PremiumPolicy)premiumField.get(game);
            weightsField.set(game,forcedRole(role));
            premiumField.set(game,forcedPremium(premium));

            InternalRole expectedRole=role;PremiumPolicy.Type expectedPremium=premium;
            BukkitTask watcher=Bukkit.getScheduler().runTaskTimer(helper,()->watchForce(expectedRole,expectedPremium),1L,1L);
            pendingForce=new ForceOverride(target.getUniqueId(),session.sequence(),role,premium,game,originalWeights,originalPremium,System.currentTimeMillis(),sender,target,watcher);
            String name=premium==null?role.name():"PREMIUM_"+premium.name()+" ("+role.name()+")";
            sender.sendMessage("TEST_FORCE_ARMED "+name+". Play the next normal game on this machine.");
            if(!sender.equals(target))target.sendMessage("TEST_FORCE_ARMED "+name+". Play the next normal game on this machine.");
        } catch (ReflectiveOperationException error) {
            sender.sendMessage("TEST_FORCE_FAILED "+error);
        }
        return true;
    }

    private void watchForce(InternalRole expectedRole,PremiumPolicy.Type expectedPremium) {
        ForceOverride force=pendingForce;if(force==null)return;
        var production=(PiriJugglerPlugin)Bukkit.getPluginManager().getPlugin("PiriJuggler");
        if(production==null||!production.isEnabled()) {restoreForce("TEST_FORCE_CANCELLED PIRI_NOT_READY");return;}
        Session session=production.machines().snapshot().session(force.player());
        if(session==null) {restoreForce("TEST_FORCE_CANCELLED SESSION_MISSING");return;}
        if(System.currentTimeMillis()-force.startedAtMs()>30_000) {restoreForce("TEST_FORCE_TIMEOUT");return;}
        if(session.sequence()<=force.baseSequence()||session.state()!=Session.GameState.NORMAL_SPINNING)return;
        String actualRole=session.text("internal_role");String actualPremium=session.text("premium_type");
        boolean roleOk=expectedRole.name().equals(actualRole);
        boolean premiumOk=expectedPremium==null?actualPremium==null:expectedPremium.name().equals(actualPremium);
        restoreForce(roleOk&&premiumOk?"TEST_FORCE_CONSUMED role="+actualRole+" premium="+actualPremium:
                "TEST_FORCE_MISMATCH role="+actualRole+" premium="+actualPremium);
    }

    private void restoreForce(String message) {
        ForceOverride force=pendingForce;if(force==null)return;
        try {
            Field weightsField=NormalGame.class.getDeclaredField("weights");weightsField.setAccessible(true);weightsField.set(force.game(),force.originalWeights());
            Field premiumField=NormalGame.class.getDeclaredField("premium");premiumField.setAccessible(true);premiumField.set(force.game(),force.originalPremium());
        } catch (ReflectiveOperationException error) {
            message += " RESTORE_FAILED="+error;
        }
        force.watcher().cancel();pendingForce=null;
        force.sender().sendMessage(message);if(!force.sender().equals(force.target()))force.target().sendMessage(message);
    }

    private PiriJugglerPlugin production(CommandSender sender) {
        var production = (PiriJugglerPlugin) Bukkit.getPluginManager().getPlugin("PiriJuggler");
        if (production == null || !production.isEnabled() || !production.machines().ready()) {
            sender.sendMessage("PIRI_NOT_READY");
            return null;
        }
        return production;
    }

    private static NormalGame normalGame(PiriJugglerPlugin production,Session session) throws ReflectiveOperationException {
        Object machines=production.machines();
        Field field=machines.getClass().getDeclaredField("games");field.setAccessible(true);
        GameEngines registry=(GameEngines)field.get(machines);
        Machine machine=production.machines().snapshot().machine(session.machine());
        if(machine==null)throw new IllegalStateException("Machine missing");
        GameEngine engine=registry.require(machine.type());
        if(engine instanceof JugglerGameEngine){
            Field delegate=JugglerGameEngine.class.getDeclaredField("delegate");delegate.setAccessible(true);
            return (NormalGame)delegate.get(engine);
        }
        if(engine instanceof JugglerGodGameEngine){
            Field delegate=JugglerGodGameEngine.class.getDeclaredField("delegate");delegate.setAccessible(true);
            return (NormalGame)delegate.get(engine);
        }
        throw new IllegalStateException("Machine has no NormalGame delegate: "+machine.type());
    }

    private static RoleWeights forcedRole(InternalRole role) {
        Map<String,Object> settings=new LinkedHashMap<>();
        for(int setting=1;setting<=6;setting++) {
            Map<String,Object> row=new LinkedHashMap<>();
            for(InternalRole candidate:InternalRole.values())row.put(candidate.name().toLowerCase(Locale.ROOT),candidate==role?1_000_000_000:0);
            settings.put(Integer.toString(setting),row);
        }
        return new RoleWeights(Map.of("probabilities",Map.of("settings",settings)));
    }

    private static PremiumPolicy forcedPremium(PremiumPolicy.Type type) {
        Map<String,Object> weights=new LinkedHashMap<>();
        weights.put("reverse",type==PremiumPolicy.Type.A?1:0);
        weights.put("middle_cherry",type==PremiumPolicy.Type.B?1:0);
        weights.put("sound_first_peka",type==PremiumPolicy.Type.C?1:0);
        weights.put("strong_after_peka",type==PremiumPolicy.Type.D?1:0);
        weights.put("five_notice_blink",type==PremiumPolicy.Type.E?1:0);
        weights.put("fake_tenpai",type==PremiumPolicy.Type.F?1:0);
        if(type==null)weights.replaceAll((ignored,value)->1);
        return new PremiumPolicy(Map.of("premium",Map.of("denominator",1,"big_chance_weight",type==null?0:1,"weights",weights)));
    }
}
