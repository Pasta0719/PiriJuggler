package jp.pirijuggler.runtime.paper;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.game.NormalGame;
import jp.pirijuggler.paper.game.PremiumPolicy;
import jp.pirijuggler.paper.game.RoleWeights;
import jp.pirijuggler.paper.game.GameEngines;
import jp.pirijuggler.paper.game.GameEngine;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.economy.MedalToken;
import jp.pirijuggler.paper.economy.PrizeItem;
import jp.pirijuggler.paper.game.JugglerGameEngine;
import jp.pirijuggler.paper.game.JugglerGodGameEngine;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import jp.pirijuggler.paper.reel.InternalRole;
import jp.pirijuggler.paper.session.Session;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
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
                                 JugglerGodGameEngine godEngine, RoleWeights originalGodWeights,
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
        if (args.length >= 1 && args[0].equalsIgnoreCase("phase13fund")) return phase13Fund(sender,args);
        if (args.length >= 1 && args[0].equalsIgnoreCase("phase13snapshot")) return phase13Snapshot(sender,args);
        if (args.length >= 1 && args[0].equalsIgnoreCase("force")) return force(sender,args);
        if (args.length >= 1 && args[0].equalsIgnoreCase("heaven")) return heaven(sender,args);
        if (args.length >= 1 && args[0].equalsIgnoreCase("skillreset")) return skillReset(sender,args);
        if (args.length >= 1 && args[0].equalsIgnoreCase("remoteorphan")) return remoteOrphan(sender,args);
        if (args.length >= 1 && args[0].equalsIgnoreCase("phase12grid")) return phase12Grid(sender,args);
        if (args.length >= 1 && args[0].equalsIgnoreCase("phase13grid")) return phase13Grid(sender,args);
        if (args.length >= 1 && args[0].equalsIgnoreCase("entitycount")) return entityCount(sender,args);
        if (args.length >= 1 && args[0].equalsIgnoreCase("mobilefund")) return mobileFund(sender,args);
        if (args.length >= 1 && args[0].equalsIgnoreCase("mobilecheck")) return mobileCheck(sender,args);
        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            if (pendingForce == null) sender.sendMessage("NO_TEST_FORCE_PENDING");
            else restoreForce("TEST_FORCE_CLEARED");
            return true;
        }
        sender.sendMessage("Usage: /piritest fund [player] | mobilefund [player] | mobilecheck [player] | force <god|big|reg|A|B|C|D|E|F> [player] | heaven <1-32> [player] | skillreset [player] | clear");
        return true;
    }

    private boolean mobileFund(CommandSender sender,String[] args) {
        if(args.length<1||args.length>2){sender.sendMessage("Usage: /piritest mobilefund [player]");return true;}
        Player target=args.length==2?Bukkit.getPlayerExact(args[1]):sender instanceof Player p?p:null;
        if(target==null){sender.sendMessage("PLAYER_REQUIRED");return true;}
        var production=production(sender); if(production==null)return true;
        UUID bundle=UUID.randomUUID(); int medals=600; long now=System.currentTimeMillis();
        var dbPath=production.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(helper,()->{
            String result;
            try{
                Class.forName("org.sqlite.JDBC");
                try(var connection=DriverManager.getConnection("jdbc:sqlite:"+dbPath)){
                    try(var pragma=connection.createStatement()){pragma.execute("PRAGMA busy_timeout=5000");}
                    try(var create=connection.createStatement()){
                        create.execute("CREATE TABLE IF NOT EXISTS medal_tokens_unlimited (bundle_id TEXT PRIMARY KEY,amount INTEGER NOT NULL CHECK(amount>=1),state TEXT NOT NULL CHECK(state IN ('PENDING_DELIVERY','ACTIVE','RETIRED')),source_transaction_id TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
                    }
                    try(var insert=connection.prepareStatement("INSERT INTO medal_tokens_unlimited(bundle_id,amount,state,source_transaction_id,created_at,updated_at) VALUES(?,?,'ACTIVE',NULL,?,?)")){
                        insert.setString(1,bundle.toString());insert.setInt(2,medals);insert.setLong(3,now);insert.setLong(4,now);insert.executeUpdate();
                    }
                }
                result="OK";
            }catch(Exception error){result="TEST_MOBILE_FUND_FAILED "+error;}
            String done=result;
            Bukkit.getScheduler().runTask(helper,()->{
                if(!"OK".equals(done)){sender.sendMessage(done);return;}
                target.getInventory().addItem(MedalToken.create(bundle,medals));
                target.getInventory().addItem(PrizeItem.create(PrizeItem.Type.SMALL,2));
                target.getInventory().addItem(PrizeItem.create(PrizeItem.Type.MEDIUM,1));
                target.getInventory().addItem(PrizeItem.create(PrizeItem.Type.LARGE,1));
                target.updateInventory();
                sender.sendMessage("TEST_MOBILE_FUNDED medals=600 small=2 medium=1 large=1");
                if(!sender.equals(target))target.sendMessage("TEST_MOBILE_FUNDED medals=600 small=2 medium=1 large=1");
            });
        });
        return true;
    }

    private boolean mobileCheck(CommandSender sender,String[] args) {
        if(args.length<1||args.length>2){sender.sendMessage("Usage: /piritest mobilecheck [player]");return true;}
        Player target=args.length==2?Bukkit.getPlayerExact(args[1]):sender instanceof Player p?p:null;
        if(target==null){sender.sendMessage("PLAYER_REQUIRED");return true;}
        int medals=0,small=0,medium=0,large=0;
        for(int slot=0;slot<36;slot++){
            var item=target.getInventory().getItem(slot);
            MedalToken.Value medal=MedalToken.read(item);
            if(medal!=null)medals=Math.addExact(medals,medal.amount());
            PrizeItem.Type type=PrizeItem.read(item);
            if(type!=null){
                switch(type){
                    case SMALL -> small=Math.addExact(small,item.getAmount());
                    case MEDIUM -> medium=Math.addExact(medium,item.getAmount());
                    case LARGE -> large=Math.addExact(large,item.getAmount());
                }
            }
        }
        sender.sendMessage("TEST_MOBILE_INVENTORY medals="+medals+" small="+small+" medium="+medium+" large="+large);
        return true;
    }

    /**
     * Runtime-only Phase12 setup. Creates the 42 registered machines directly through
     * the production database API so the acceptance test spends its time on remote
     * synchronization/gameplay rather than repeated player ray-trace registration.
     */
    private boolean phase12Grid(CommandSender sender,String[] args) {
        if(args.length!=1){sender.sendMessage("Usage: /piritest phase12grid");return true;}
        Player target=sender instanceof Player player?player:null;
        if(target==null){sender.sendMessage("PLAYER_REQUIRED");return true;}
        var production=production(sender);if(production==null)return true;
        var service=production.machines();
        try{
            Field dbField=service.getClass().getDeclaredField("database");dbField.setAccessible(true);
            PiriDatabase database=(PiriDatabase)dbField.get(service);
            Field stateField=service.getClass().getDeclaredField("state");stateField.setAccessible(true);
            UUID worldId=target.getWorld().getUID();String worldName=target.getWorld().getName();
            int[] xs={-12,-8,-4,0,4,8,12};int[] zs={-10,-6,-2,2,6,10};
            production.executors().database(()->{
                long now=System.currentTimeMillis();int count=0;
                for(int z:zs)for(int x:xs){
                    database.create(new Machine.Location(worldId,worldName,x,66,z,"SOUTH"),MachineType.JUGGLER,now+count);
                    count++;
                }
                return database.state();
            },(fresh,error)->{
                if(error!=null){sender.sendMessage("TEST_PHASE12_GRID_FAILED "+error);return;}
                try{
                    stateField.set(service,fresh);
                    sender.sendMessage("TEST_PHASE12_GRID count="+fresh.machines().stream().filter(m->!m.deleted()).count());
                }catch(IllegalAccessException setError){sender.sendMessage("TEST_PHASE12_GRID_FAILED "+setError);}
            });
        }catch(ReflectiveOperationException error){sender.sendMessage("TEST_PHASE12_GRID_FAILED "+error);}
        return true;
    }

    private boolean entityCount(CommandSender sender,String[] args) {
        if(args.length!=1){sender.sendMessage("Usage: /piritest entitycount");return true;}
        Player target=sender instanceof Player player?player:null;
        if(target==null){sender.sendMessage("PLAYER_REQUIRED");return true;}
        long displays=target.getWorld().getEntities().stream().filter(e->e instanceof org.bukkit.entity.Display).count();
        sender.sendMessage("TEST_ENTITY_COUNT total="+target.getWorld().getEntities().size()+" displays="+displays+" armorstands="+target.getWorld().getEntitiesByClass(ArmorStand.class).size());
        return true;
    }

    private boolean phase13Grid(CommandSender sender,String[] args) {
        if(args.length!=1){sender.sendMessage("Usage: /piritest phase13grid");return true;}
        Player target=sender instanceof Player player?player:null;
        if(target==null){sender.sendMessage("PLAYER_REQUIRED");return true;}
        var production=production(sender);if(production==null)return true;
        var service=production.machines();
        try{
            Field dbField=service.getClass().getDeclaredField("database");dbField.setAccessible(true);
            PiriDatabase database=(PiriDatabase)dbField.get(service);
            Field stateField=service.getClass().getDeclaredField("state");stateField.setAccessible(true);
            UUID worldId=target.getWorld().getUID();String worldName=target.getWorld().getName();
            String[] facings={"NORTH","SOUTH","EAST","WEST","UP","DOWN"};
            production.executors().database(()->{
                long now=System.currentTimeMillis();
                for(int i=0;i<42;i++){
                    int x=(i%7-3)*3,z=(i/7-3)*3;
                    String facing=i<6?facings[i]:"SOUTH";
                    database.create(new Machine.Location(worldId,worldName,x,100,z,facing),MachineType.JUGGLER,now+i);
                }
                return database.state();
            },(fresh,error)->{
                if(error!=null){sender.sendMessage("TEST_PHASE13_GRID_FAILED "+error);return;}
                try{stateField.set(service,fresh);sender.sendMessage("TEST_PHASE13_GRID count="+fresh.machines().stream().filter(m->!m.deleted()).count());}
                catch(IllegalAccessException setError){sender.sendMessage("TEST_PHASE13_GRID_FAILED "+setError);}
            });
        }catch(ReflectiveOperationException error){sender.sendMessage("TEST_PHASE13_GRID_FAILED "+error);}
        return true;
    }

    /** Runtime-only fixture for proving production stale REMOTE ArmorStand cleanup. */
    private boolean remoteOrphan(CommandSender sender,String[] args) {
        if(args.length!=1){sender.sendMessage("Usage: /piritest remoteorphan");return true;}
        Player target=sender instanceof Player player?player:null;
        if(target==null){sender.sendMessage("PLAYER_REQUIRED");return true;}
        ArmorStand stand=target.getWorld().spawn(target.getLocation(),ArmorStand.class,npc->{
            npc.setPersistent(true);
            npc.setGravity(false);
            npc.setInvulnerable(true);
            npc.addScoreboardTag("piri_remote");
            npc.addScoreboardTag("piri_remote_runtime_orphan");
        });
        sender.sendMessage("TEST_REMOTE_ORPHAN_CREATED "+stand.getUniqueId());
        return true;
    }

    /**
     * Runtime-acceptance-only hard reset for SKILL_STOP scenario isolation.
     * This helper JAR is never distributed. Production deliberately preserves unfinished
     * SKILL_STOP rights, so final acceptance must not weaken production recovery semantics.
     */
    private boolean skillReset(CommandSender sender,String[] args) {
        if(args.length<1||args.length>2){sender.sendMessage("Usage: /piritest skillreset [player]");return true;}
        Player target=args.length==2?Bukkit.getPlayerExact(args[1]):sender instanceof Player player?player:null;
        if(target==null){sender.sendMessage("PLAYER_REQUIRED");return true;}
        var production=production(sender);if(production==null)return true;
        var service=production.machines();
        Session session=service.snapshot().session(target.getUniqueId());
        if(session==null){sender.sendMessage("TEST_SKILL_RESET_NO_SESSION");return true;}
        Machine machine=service.snapshot().machine(session.machine());
        if(machine==null||machine.type()!=jp.pirijuggler.paper.machine.MachineType.SKILL_STOP){
            sender.sendMessage("TEST_SKILL_RESET_REQUIRES_SKILL_STOP");return true;
        }
        if(pendingForce!=null)restoreForce("TEST_FORCE_CLEARED_FOR_SKILL_RESET");
        try{
            Field dbField=service.getClass().getDeclaredField("database");dbField.setAccessible(true);
            PiriDatabase database=(PiriDatabase)dbField.get(service);
            Field stateField=service.getClass().getDeclaredField("state");stateField.setAccessible(true);
            Field gamesField=service.getClass().getDeclaredField("games");gamesField.setAccessible(true);
            GameEngines engines=(GameEngines)gamesField.get(service);
            UUID owner=target.getUniqueId();UUID oldSession=session.id();int machineId=session.machine();
            production.executors().database(()->{
                database.sql("DELETE FROM player_sessions WHERE player_uuid=?",owner.toString());
                return database.state();
            },(fresh,error)->{
                if(error!=null){sender.sendMessage("TEST_SKILL_RESET_FAILED "+error);return;}
                try{
                    stateField.set(service,fresh);
                    engines.require(machine.type()).forget(oldSession);
                    sender.sendMessage("TEST_SKILL_RESET machine="+machineId);
                    if(!sender.equals(target))target.sendMessage("TEST_SKILL_RESET machine="+machineId);
                }catch(ReflectiveOperationException resetError){
                    sender.sendMessage("TEST_SKILL_RESET_FAILED "+resetError);
                }
            });
        }catch(ReflectiveOperationException error){sender.sendMessage("TEST_SKILL_RESET_FAILED "+error);}
        return true;
    }


    private boolean phase13Snapshot(CommandSender sender,String[] args) {
        Player target=sender instanceof Player player?player:null;
        if(target==null){sender.sendMessage("PLAYER_REQUIRED");return true;}
        var production=production(sender);if(production==null)return true;
        Session session=production.machines().snapshot().session(target.getUniqueId());
        if(session==null){sender.sendMessage("TEST_PHASE13_SNAPSHOT_NO_SESSION");return true;}
        try{
            Field remoteField=production.machines().getClass().getDeclaredField("remote");remoteField.setAccessible(true);
            var remote=(jp.pirijuggler.paper.machine.RemoteMachineSync)remoteField.get(production.machines());
            for(Player viewer:Bukkit.getOnlinePlayers()) remote.viewerReady(viewer);
            sender.sendMessage("TEST_PHASE13_SNAPSHOT machine="+session.machine());
        }catch(ReflectiveOperationException error){sender.sendMessage("TEST_PHASE13_SNAPSHOT_FAILED "+error);}
        return true;
    }

    private boolean phase13Fund(CommandSender sender,String[] args) {
        Player target=sender instanceof Player player?player:null;
        if(target==null){sender.sendMessage("PLAYER_REQUIRED");return true;}
        var production=production(sender);if(production==null)return true;
        Session session=production.machines().snapshot().session(target.getUniqueId());
        if(session==null){sender.sendMessage("TEST_PHASE13_FUND_NO_SESSION");return true;}
        try{
            Field stateField=production.machines().getClass().getDeclaredField("state");stateField.setAccessible(true);
            Field dbField=production.machines().getClass().getDeclaredField("database");dbField.setAccessible(true);
            PiriDatabase database=(PiriDatabase)dbField.get(production.machines());
            UUID sid=session.id();
            production.executors().database(()->{
                database.sql("UPDATE player_sessions SET credit=50,held_medals=800 WHERE session_id=?",sid.toString());
                return database.state();
            },(fresh,error)->{
                if(error!=null){sender.sendMessage("TEST_PHASE13_FUND_FAILED "+error);return;}
                try{stateField.set(production.machines(),fresh);sender.sendMessage("TEST_PHASE13_FUNDED");}
                catch(IllegalAccessException e){sender.sendMessage("TEST_PHASE13_FUND_FAILED "+e);}
            });
        }catch(ReflectiveOperationException error){sender.sendMessage("TEST_PHASE13_FUND_FAILED "+error);}
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

    private boolean heaven(CommandSender sender,String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage("Usage: /piritest heaven <1-32> [player]");
            return true;
        }
        int target;
        try { target=Integer.parseInt(args[1]); }
        catch(NumberFormatException error){ sender.sendMessage("TEST_HEAVEN_TARGET 1-32"); return true; }
        if(target<1||target>32){ sender.sendMessage("TEST_HEAVEN_TARGET 1-32"); return true; }

        Player targetPlayer = args.length == 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player player ? player : null;
        if(targetPlayer==null){ sender.sendMessage("PLAYER_REQUIRED"); return true; }

        var production=production(sender); if(production==null)return true;
        Session session=production.machines().snapshot().session(targetPlayer.getUniqueId());
        if(session==null || session.lifecycle()!=Session.Lifecycle.ACTIVE || session.state()!=Session.GameState.SEATED_READY){
            sender.sendMessage("TEST_HEAVEN_REQUIRES_SEATED_READY");
            return true;
        }
        Machine machine=production.machines().snapshot().machine(session.machine());
        if(machine==null || machine.type()!=jp.pirijuggler.paper.machine.MachineType.JUGGLER_GOD){
            sender.sendMessage("TEST_HEAVEN_REQUIRES_JUGGLER_GOD");
            return true;
        }

        var runtime=new jp.pirijuggler.paper.game.JugglerGodRuntime(
                jp.pirijuggler.paper.game.JugglerGodRuntime.Mode.HEAVEN,target,0,0,false,false,
                "NONE",0,false,"TEST_HEAVEN");
        String runtimeJson=runtime.toJsonString();
        String sessionId=session.id().toString();
        long machineId=session.machine();
        var dbPath=production.getDataFolder().toPath().resolve("piri.db").toAbsolutePath();
        Bukkit.getScheduler().runTaskAsynchronously(helper,()->{
            String result;
            try{
                Class.forName("org.sqlite.JDBC");
                try(var connection=DriverManager.getConnection("jdbc:sqlite:"+dbPath)){
                    try(var pragma=connection.createStatement()){pragma.execute("PRAGMA busy_timeout=5000");}
                    connection.setAutoCommit(false);
                    try(var ps=connection.prepareStatement("UPDATE player_sessions SET machine_state_json=? WHERE session_id=?")){
                        ps.setString(1,runtimeJson);ps.setString(2,sessionId);
                        if(ps.executeUpdate()!=1)throw new IllegalStateException("Session row missing");
                    }
                    try(var ps=connection.prepareStatement("UPDATE machines SET machine_runtime_json=? WHERE machine_id=?")){
                        ps.setString(1,runtimeJson);ps.setLong(2,machineId);
                        if(ps.executeUpdate()!=1)throw new IllegalStateException("Machine row missing");
                    }
                    connection.commit();
                }
                result="TEST_HEAVEN_SET target="+target+". Close and reopen the machine.";
            }catch(Exception error){result="TEST_HEAVEN_FAILED "+error;}
            String message=result;
            Bukkit.getScheduler().runTask(helper,()->{
                sender.sendMessage(message);
                if(!sender.equals(targetPlayer))targetPlayer.sendMessage(message);
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
            RoleWeights forcedWeights=forcedRole(role);
            weightsField.set(game,forcedWeights);
            premiumField.set(game,forcedPremium(premium));

            JugglerGodGameEngine godEngine=jugglerGodEngine(production,session);
            RoleWeights originalGodWeights=null;
            if(godEngine!=null){
                Field godWeightsField=JugglerGodGameEngine.class.getDeclaredField("weights");godWeightsField.setAccessible(true);
                originalGodWeights=(RoleWeights)godWeightsField.get(godEngine);
                godWeightsField.set(godEngine,forcedWeights);
            }

            InternalRole expectedRole=role;PremiumPolicy.Type expectedPremium=premium;
            BukkitTask watcher=Bukkit.getScheduler().runTaskTimer(helper,()->watchForce(expectedRole,expectedPremium),1L,1L);
            pendingForce=new ForceOverride(target.getUniqueId(),session.sequence(),role,premium,game,originalWeights,originalPremium,godEngine,originalGodWeights,System.currentTimeMillis(),sender,target,watcher);
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
            if(force.godEngine()!=null){
                Field godWeightsField=JugglerGodGameEngine.class.getDeclaredField("weights");godWeightsField.setAccessible(true);
                godWeightsField.set(force.godEngine(),force.originalGodWeights());
            }
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

    private static JugglerGodGameEngine jugglerGodEngine(PiriJugglerPlugin production,Session session) throws ReflectiveOperationException {
        Object machines=production.machines();
        Field field=machines.getClass().getDeclaredField("games");field.setAccessible(true);
        GameEngines registry=(GameEngines)field.get(machines);
        Machine machine=production.machines().snapshot().machine(session.machine());
        if(machine==null)throw new IllegalStateException("Machine missing");
        GameEngine engine=registry.require(machine.type());
        return engine instanceof JugglerGodGameEngine god?god:null;
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




