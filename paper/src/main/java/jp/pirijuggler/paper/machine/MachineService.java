package jp.pirijuggler.paper.machine;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.admin.AdminStore;
import jp.pirijuggler.paper.database.GameStore;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.database.RecoveryStore;
import jp.pirijuggler.paper.economy.EconomyStore;
import jp.pirijuggler.paper.economy.MedalToken;
import jp.pirijuggler.paper.economy.VaultBridge;
import jp.pirijuggler.paper.game.*;
import jp.pirijuggler.paper.game.god.*;
import jp.pirijuggler.paper.reel.InternalRole;
import jp.pirijuggler.paper.session.AdminSessions;
import jp.pirijuggler.paper.session.Session;
import jp.pirijuggler.paper.threading.PaperMainThread;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/** All live state and operation reservations are owned by the Paper main thread. */
public final class MachineService implements Listener, CommandExecutor {
    private static final NamespacedKey ITEM_TYPE = new NamespacedKey("piri", "item_type");
    private static final NamespacedKey ITEM_VERSION = new NamespacedKey("piri", "item_version");
    private static final Set<PacketType> ADMIN_MUTATIONS = Set.of(PacketType.ADMIN_SET_SETTING, PacketType.ADMIN_SET_AUTO, PacketType.ADMIN_SET_ENABLED, PacketType.ADMIN_RESET_DAILY);
    private final PiriJugglerPlugin plugin;
    private final AdminSessions admins = new AdminSessions();
    private final Set<UUID> pendingPlayers = new HashSet<>();
    private final Set<Integer> pendingMachines = new HashSet<>();
    private final Map<UUID, Runnable> deferredClose = new HashMap<>();
    private final Set<UUID> deferredDisconnect = new HashSet<>();
    private final long graceMs;
    private final long idleMs;
    private final int loanMedals;
    private final double loanAmount;
    private final VaultBridge vault;
    private final Map<String,Object> config;
    private PiriDatabase database;
    private PiriDatabase.State state;
    private boolean stopped;
    private boolean expiring, simulating;
    private final RandomStreams random;
    private final RoleWeights weights;
    private final GameEngines games;
    private final RemoteMachineSync remote;
    private record Saved<T>(T value, PiriDatabase.State state) {}

    public MachineService(PiriJugglerPlugin plugin, Map<String, Object> config) {
        this.plugin = plugin;
        this.config = config;
        random=RandomStreams.production();weights=new RoleWeights(config);
        var jugglerGame=new NormalGame(weights,random,plugin.reels().solver(),new PaperMainThread(plugin),config);
        var jugglerGodGame=new NormalGame(weights,random,plugin.reels().solver(),new PaperMainThread(plugin),config,false);
        var extremeTuning=jp.pirijuggler.paper.database.StartupProfile.map(config.get("juggler_god_extreme"));
        int extremeBig=((Number)extremeTuning.getOrDefault("big_payout",420)).intValue();
        int extremeReg=((Number)extremeTuning.getOrDefault("reg_payout",168)).intValue();
        var jugglerGodExtremeGame=new NormalGame(weights,random,plugin.reels().solver(),new PaperMainThread(plugin),config,false,extremeBig,extremeReg);
        games=new GameEngines().register(MachineType.JUGGLER,new JugglerGameEngine(jugglerGame))
                .register(MachineType.JUGGLER_GOD,new JugglerGodGameEngine(jugglerGodGame,random,weights,config))
                .register(MachineType.JUGGLER_GOD_EXTREME,new JugglerGodGameEngine(jugglerGodExtremeGame,random,weights,config,"juggler_god_extreme"))
                .register(MachineType.GOD,new jp.pirijuggler.paper.game.god.GodGameEngine(random));
        remote=new RemoteMachineSync(plugin,()->state,plugin::canUseSlot,(saved,nowNanos)->engine(saved.machine()).capture(saved,nowNanos));
        var gameConfig=jp.pirijuggler.paper.database.StartupProfile.map(config.get("game"));
        graceMs = ((Number) gameConfig.get("disconnect_grace_seconds")).longValue() * 1000;
        idleMs = ((Number) gameConfig.get("idle_timeout_seconds")).longValue() * 1000;
        var economyConfig=jp.pirijuggler.paper.database.StartupProfile.map(config.get("economy"));
        loanMedals=((Number)economyConfig.get("loan_medals")).intValue();
        loanAmount=((Number)economyConfig.get("loan_amount")).doubleValue();
        vault=VaultBridge.discover();
        long jvm = ManagementFactory.getRuntimeMXBean().getStartTime();
        long now = System.currentTimeMillis();
        plugin.executors().database(() -> {
            database = new PiriDatabase(plugin.getDataFolder().toPath().resolve("piri.db"));
            PiriDatabase.State opened=database.open(jvm, now, config, random.eventAllocation(), plugin.getLogger()::warning);
            var uncertain=new EconomyStore(database).quarantineStartedVaultTransactions(now);
            for(var row:uncertain)plugin.getLogger().severe("PIRI_VAULT_REVIEW_REQUIRED transactionId="+row.get("transaction_id")+" operation="+row.get("operation")+" expected="+row.get("vault_amount")+" balanceBefore="+row.get("balance_before"));
            return opened;
        }, (loaded, error) -> {
            if (error != null) plugin.getLogger().log(Level.SEVERE, "Gameplay disabled: database initialization failed", error);
            else { state = loaded; plugin.getLogger().info("PIRI_DATABASE_READY schema=4 period=" + state.period() + " machines=" + state.machines().size()); }
        });
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("piri")).setExecutor(this);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
        plugin.getServer().getScheduler().runTaskTimer(plugin, remote::refreshOnlineViewers, 10, 10);
    }
    private void main() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Machine state requires Paper main thread"); }
    public boolean ready() { main(); return !stopped && state != null; }
    public PiriDatabase.State snapshot() { main(); return state; }
    public void updateRemoteDataLamp(JsonObject data) { main(); if (ready()) remote.updateDataLamp(data); }
    private boolean busy(int id) { return pendingMachines.contains(id) || state.busy(id); }
    private GameEngine engine(int machineId) {
        Machine machine=state==null?null:state.machine(machineId);
        if(machine==null)throw new DomainException("INVALID_STATE");
        try { return games.require(machine.type()); }
        catch (IllegalStateException notReady) { throw new DomainException("INVALID_STATE"); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        main();
        if (!ready()) { error(sender, "DB_ERROR"); return true; }
        if(args.length==2&&args[0].equalsIgnoreCase("recover")){
            if(!(sender instanceof Player player)){tell(sender,"PLAYER_REQUIRED");return true;}
            if(args[1].equalsIgnoreCase("status")){recoverStatus(player);return true;}
            if(args[1].equalsIgnoreCase("cashout")){recoverCashout(player);return true;}
        }
        if (!sender.isOp()) { error(sender, "NOT_OP"); return true; }
        try {
            if (args.length==3 && (args[0].equalsIgnoreCase("simulator") || args[0].equalsIgnoreCase("sim"))) {
                int setting=Integer.parseInt(args[1]);long count=Long.parseLong(args[2]);
                if(setting<1||setting>6||count<1||count>100_000_000L)throw new DomainException("INVALID_STATE");
                if(simulating)throw new DomainException("BUSY");
                simulating=true;var rng=random.runtimeSimulation();tell(sender,"SIMULATOR_STARTED setting="+setting+" games="+count);
                plugin.executors().simulator(()->Simulator.run(weights,setting,count,rng),(result,error)->{
                    try {if(!stopped){if(error!=null)failure(sender,error);else tell(sender,"PIRI_SIMULATOR "+new Gson().toJson(result));}}
                    finally {simulating=false;}
                });return true;
            }
            if (args.length==3 && (args[0].equalsIgnoreCase("godsimulator") || args[0].equalsIgnoreCase("godsim"))) {
                int setting=Integer.parseInt(args[1]);long count=Long.parseLong(args[2]);
                if(setting<1||setting>6||count<1||count>100_000_000L)throw new DomainException("INVALID_STATE");
                if(simulating)throw new DomainException("BUSY");
                var tuning=jp.pirijuggler.paper.database.StartupProfile.map(config.get("juggler_god"));
                long normalBigPpm=((Number)tuning.getOrDefault("normal_big_to_heaven_ppm",0)).longValue();
                long normalRegPpm=((Number)tuning.getOrDefault("normal_reg_to_heaven_ppm",0)).longValue();
                long heavenPpm=((Number)tuning.getOrDefault("heaven_to_heaven_ppm",0)).longValue();
                var settingTuning=jp.pirijuggler.paper.database.StartupProfile.map(
                        jp.pirijuggler.paper.database.StartupProfile.map(tuning.get("settings")).get(Integer.toString(setting)));
                int bonusScale=((Number)settingTuning.getOrDefault("bonus_scale_ppm",1_000_000)).intValue();
                int smallRoleScale=((Number)settingTuning.getOrDefault("small_role_scale_ppm",1_000_000)).intValue();
                simulating=true;var rng=random.runtimeSimulation();
                tell(sender,"GOD_SIMULATOR_STARTED setting="+setting+" games="+count+" normalBigToHeavenPpm="+normalBigPpm+" normalRegToHeavenPpm="+normalRegPpm+" heavenToHeavenPpm="+heavenPpm+" bonusScalePpm="+bonusScale+" smallRoleScalePpm="+smallRoleScale);
                plugin.executors().simulator(
                        ()->JugglerGodSimulator.run(weights,setting,count,normalBigPpm,normalRegPpm,heavenPpm,bonusScale,smallRoleScale,rng),
                        (result,error)->{
                            try {if(!stopped){if(error!=null)failure(sender,error);else tell(sender,"PIRI_GOD_SIMULATOR "+new Gson().toJson(result));}}
                            finally {simulating=false;}
                        });
                return true;
            }
            if (args.length==3 && args[0].equalsIgnoreCase("godtest")) {
                commandGodTest(sender,Integer.parseInt(args[1]),args[2]); return true;
            }
            if (args.length==3 && args[0].equalsIgnoreCase("godrole")) {
                commandGodRole(sender,Integer.parseInt(args[1]),args[2]); return true;
            }
            if (args.length==3 && (args[0].equalsIgnoreCase("jugglergodrole") || args[0].equalsIgnoreCase("jgrole"))) {
                commandJugglerGodRole(sender,Integer.parseInt(args[1]),args[2]); return true;
            }
            if(args.length>=2 && (args[0].equalsIgnoreCase("jgextreme")||args[0].equalsIgnoreCase("extreme"))){
                String action=args[1].toLowerCase(Locale.ROOT);
                if(action.equals("create")&&args.length==2){
                    Machine.Location target=target(sender);
                    submit(sender,null,0,()->database.create(target,MachineType.JUGGLER_GOD_EXTREME,System.currentTimeMillis()),
                            id->{tell(sender,"EXTREME_MACHINE_CREATED "+id);remote.machineChanged(id);});
                    return true;
                }
                if(action.equals("setting")&&args.length==4){
                    int id=Integer.parseInt(args[2]);
                    requireExtreme(id);
                    commandSetting(sender,id,Integer.parseInt(args[3]));
                    return true;
                }
                if(action.equals("role")&&args.length==4){
                    int id=Integer.parseInt(args[2]);
                    requireExtreme(id);
                    commandJugglerGodRole(sender,id,args[3]);
                    return true;
                }
                if(action.equals("info")&&args.length==3){
                    int id=Integer.parseInt(args[2]);Machine machine=requireExtreme(id);
                    tell(sender,"EXTREME_INFO id="+id+" setting="+machine.setting()+" world="+machine.location().worldName()+" xyz="+machine.location().x()+","+machine.location().y()+","+machine.location().z()+" busy="+busy(id));
                    return true;
                }
                throw new DomainException("Usage: /piri jgextreme create|setting <id> <1-6>|role <id> <role|clear>|info <id>");
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("give") && args.length <= 3) {
                Player target = args.length == 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player player ? player : null;
                if (target == null) throw new DomainException("PLAYER_REQUIRED");
                if (target.getInventory().firstEmpty() < 0) throw new DomainException("INVENTORY_FULL");
                target.getInventory().addItem(machineKey()); tell(sender, "KEY_GIVEN " + target.getName()); return true;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("setting")) {
                commandSetting(sender,Integer.parseInt(args[1]),Integer.parseInt(args[2])); return true;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("daily")) {
                commandResetDaily(sender,args[2]); return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("event")) {
                commandEvent(sender,args); return true;
            }
            if (args.length < 2 || !args[0].equalsIgnoreCase("machine")) throw new DomainException("Usage: /piri machine create [JUGGLER|JUGGLER_GOD|JUGGLER_GOD_EXTREME|OKIDOKI|GOD|DISC]|type <id> <type>|redefine <id>|remove <id>|list|info <id>, /piri godtest <id> <reset|normal|gg|god|red7|sgg|gzone|zzone|zgame>, /piri godrole <id> <role|clear>, /piri jgrole <id> <MISS|REPLAY|GRAPE|CHERRY|BELL|PIERO|BIG|REG|CHERRY_BIG|CHERRY_REG|PIERO_BIG|PIERO_REG|GOD|clear>, /piri key give [player], /piri setting <id> <1-6>, /piri reset daily <id|all>, /piri event status|next <profile|clear>, /piri recover status|cashout, /piri simulator <setting> <games>");
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("list") && args.length == 2) {
                tell(sender, "MACHINES " + state.machines().stream().filter(m -> !m.deleted()).map(m -> Integer.toString(m.id())).toList()); return true;
            }
            if (action.equals("create") && (args.length == 2 || args.length == 3)) {
                MachineType type=args.length==3?MachineType.valueOf(args[2].toUpperCase(Locale.ROOT)):MachineType.JUGGLER;
                Machine.Location target = target(sender);
                submit(sender, null, 0, () -> database.create(target, type, System.currentTimeMillis()), id -> { tell(sender, "MACHINE_CREATED " + id + " type=" + type); remote.machineChanged(id); }); return true;
            }
            if (action.equals("type") && args.length == 4) {
                int id=Integer.parseInt(args[2]); Machine machine=state.machine(id);
                if(machine==null)throw new DomainException("INVALID_STATE");
                if(busy(id))throw new DomainException("MACHINE_OCCUPIED");
                MachineType type=MachineType.valueOf(args[3].toUpperCase(Locale.ROOT));
                submit(sender,null,id,()->{database.setMachineType(id,type,System.currentTimeMillis());return id;},
                        done->{tell(sender,"MACHINE_TYPE id="+done+" old="+machine.type()+" new="+type);remote.machineChanged(done);});
                return true;
            }
            if (action.equals("setting") && args.length == 4) {
                commandSetting(sender,Integer.parseInt(args[2]),Integer.parseInt(args[3])); return true;
            }
            if (args.length != 3) throw new DomainException("INVALID_STATE");
            int id = Integer.parseInt(args[2]); Machine machine = state.machine(id);
            if (machine == null) throw new DomainException("INVALID_STATE");
            switch (action) {
                case "info" -> tell(sender, "MACHINE_INFO id=" + id + " type=" + machine.type() + " world=" + machine.location().worldName() + " xyz=" + machine.location().x() + "," + machine.location().y() + "," + machine.location().z()
                        + " setting=" + machine.setting() + " enabled=" + machine.enabled() + " autoSetting=" + machine.autoSetting() + " busy=" + busy(id));
                case "redefine" -> {
                    if (busy(id)) throw new DomainException("MACHINE_OCCUPIED");
                    Machine.Location target = target(sender);
                    submit(sender, null, id, () -> { database.redefine(id, target, System.currentTimeMillis()); return id; }, done -> { tell(sender, "MACHINE_REDEFINED " + done); remote.machineChanged(done); });
                }
                case "remove" -> {
                    if (busy(id)) throw new DomainException("MACHINE_OCCUPIED");
                    submit(sender, null, id, () -> { database.remove(id, System.currentTimeMillis()); return id; }, done -> { tell(sender, "MACHINE_REMOVED " + done); remote.machineChanged(done); });
                }
                default -> throw new DomainException("INVALID_STATE");
            }
        } catch (DomainException error) { tell(sender, error.getMessage()); }
        catch (IllegalArgumentException error) { tell(sender, "INVALID_STATE"); }
        return true;
    }

    private void recoverStatus(Player player){
        UUID owner=player.getUniqueId();
        plugin.executors().database(()->new EconomyStore(database).recoveryStatus(owner),(status,error)->{
            if(stopped||!player.isOnline())return;
            if(error!=null){failure(player,error);return;}
            tell(player,"RECOVER_STATUS lifecycle="+status.lifecycle()+" gameState="+status.gameState()+" credit="+status.credit()+" held="+status.held()+" pending="+status.pending());
        });
    }

    private void recoverCashout(Player player){
        UUID owner=player.getUniqueId();Session session=state.session(owner);int machine=session==null?0:session.machine();
        if(pendingPlayers.contains(owner)||(machine!=0&&pendingMachines.contains(machine))){error(player,"BUSY");return;}
        pendingPlayers.add(owner);if(machine!=0)pendingMachines.add(machine);
        long now=System.currentTimeMillis();
        plugin.executors().database(()->new Saved<>(new EconomyStore(database).prepareRecoveryCashout(owner,new RecoveryStore(database,config,plugin.reels().solver()),now),database.state()),(prepared,error)->{
            if(prepared!=null)state=prepared.state;
            if(stopped){releaseEconomy(owner,machine);return;}
            if(error!=null){releaseEconomy(owner,machine);failure(player,error);return;}
            EconomyStore.RecoveryCashoutPlan plan=prepared.value;Set<UUID> delivered=new HashSet<>();long deliveredAmount=0;
            for(var bundle:plan.bundles()){
                int slot=player.getInventory().firstEmpty();if(slot<0)break;
                player.getInventory().setItem(slot,MedalToken.create(bundle.id(),bundle.amount()));delivered.add(bundle.id());deliveredAmount=Math.addExact(deliveredAmount,bundle.amount());
            }
            long finalDelivered=deliveredAmount;
            plugin.executors().database(()->{new EconomyStore(database).finishRecoveryCashout(owner,plan.transactionId(),delivered,System.currentTimeMillis());return new Saved<>(Boolean.TRUE,database.state());},(finished,finishError)->{
                if(finished!=null)state=finished.state;releaseEconomy(owner,machine);
                if(finishError!=null){removeBundleItems(player,delivered);failure(player,finishError);return;}
                if(machine!=0)remote.broadcastSnapshot(machine);
                tell(player,"RECOVER_CASHOUT amount="+plan.amount()+" delivered="+finalDelivered+" pending="+(plan.amount()-finalDelivered));
            });
        });
    }

    private Machine requireExtreme(int id){
        Machine machine=state.machine(id);
        if(machine==null||machine.type()!=MachineType.JUGGLER_GOD_EXTREME)throw new DomainException("INVALID_STATE");
        return machine;
    }

    private void commandJugglerGodRole(CommandSender sender,int id,String rawRole) {
        Machine machine=state.machine(id);
        if(machine==null||(machine.type()!=MachineType.JUGGLER_GOD&&machine.type()!=MachineType.JUGGLER_GOD_EXTREME))throw new DomainException("INVALID_STATE");
        if(busy(id))throw new DomainException("MACHINE_OCCUPIED");

        final String roleName;
        if(rawRole.equalsIgnoreCase("clear")) roleName="NONE";
        else {
            try { roleName=InternalRole.valueOf(rawRole.toUpperCase(Locale.ROOT)).name(); }
            catch(IllegalArgumentException invalid){ throw new DomainException("INVALID_STATE"); }
        }

        JugglerGodRuntime current=JugglerGodRuntime.fromJson(machine.runtimeJson());
        JugglerGodRuntime next=current.forceRole(roleName);
        long now=System.currentTimeMillis();
        submit(sender,null,id,()->{database.setMachineRuntimeJson(id,next.toJsonString(),now);return id;},
                done->{tell(sender,"NONE".equals(roleName)
                        ?"JUGGLER_GOD_ROLE_CLEARED id="+done
                        :"JUGGLER_GOD_ROLE_READY id="+done+" role="+roleName+" nextSpinOnly=true");
                    remote.machineChanged(done);});
    }

    private void commandGodRole(CommandSender sender,int id,String rawRole) {
        Machine machine=state.machine(id);
        if(machine==null||machine.type()!=MachineType.GOD)throw new DomainException("INVALID_STATE");
        if(busy(id))throw new DomainException("MACHINE_OCCUPIED");

        GodMachineRuntime current=GodMachineRuntime.fromJson(machine.runtimeJson());
        if(current.gameplay().phase()==GodPhase.Z_ZONE||current.gameplay().phase()==GodPhase.Z_GAME)
            throw new DomainException("INVALID_STATE");

        final String roleName;
        if(rawRole.equalsIgnoreCase("clear")) roleName=null;
        else {
            try { roleName=GodRole.valueOf(rawRole.toUpperCase(Locale.ROOT)).name(); }
            catch(IllegalArgumentException invalid){ throw new DomainException("INVALID_STATE"); }
        }

        GodMachineRuntime next=current.withForcedRole(roleName);
        long now=System.currentTimeMillis();
        submit(sender,null,id,()->{database.setMachineRuntimeJson(id,next.toJsonString(),now);return id;},
                done->{tell(sender,roleName==null
                        ?"GOD_ROLE_CLEARED id="+done
                        :"GOD_ROLE_READY id="+done+" role="+roleName+" nextSpinOnly=true");remote.machineChanged(done);});
    }

    private void commandGodTest(CommandSender sender,int id,String rawMode) {
        Machine machine=state.machine(id);
        if(machine==null||machine.type()!=MachineType.GOD)throw new DomainException("INVALID_STATE");
        if(busy(id))throw new DomainException("MACHINE_OCCUPIED");

        String mode=rawMode.toLowerCase(Locale.ROOT);
        GodMachineRuntime current=GodMachineRuntime.fromJson(machine.runtimeJson());
        GodSessionState gameplay=switch(mode) {
            case "normal" -> GodSessionState.initial();
            case "gg" -> new GodSessionState(GodPhase.GG,GodProductionSpec.GG_GAMES,0,GodLoopType.A,0,0,0,0,0,0,0,0,"GG_TEST","TEST");
            case "god" -> new GodSessionState(GodPhase.GG,GodProductionSpec.GG_GAMES,GodProductionSpec.GOD_GUARANTEED_GG_SETS-1,GodLoopType.D,0,0,0,0,0,0,0,0,"GOD","GOD");
            case "red7","sgg" -> new GodSessionState(GodPhase.SGG,0,1,GodLoopType.C,0,10,0,1,0,0,0,0,"RED7_SGG","RED7");
            case "gzone" -> new GodSessionState(GodPhase.G_ZONE,0,1,GodLoopType.A,GodProductionSpec.G_ZONE_MAX_GAMES,0,0,0,0,0,0,0,"G_ZONE","TEST");
            case "zzone" -> new GodSessionState(GodPhase.Z_ZONE,0,1,GodLoopType.A,0,0,0,0,GodProductionSpec.Z_ZONE_BASE_GAMES,0,0,0,"Z_ZONE","TEST");
            case "zgame" -> new GodSessionState(GodPhase.Z_GAME,0,1,GodLoopType.A,0,0,0,0,0,0,0,0,"Z_GAME","TEST");
            case "reset" -> null;
            default -> throw new DomainException("INVALID_STATE");
        };

        GodMachineRuntime next;
        if("reset".equals(mode)) {
            next=GodMachineRuntime.initial();
        } else {
            next=new GodMachineRuntime(current.frontMode(),0,0,0,current.gaiaMode(),current.gaiaBellCount(),current.gaiaTarget(),false,0,
                    GodProductionSpec.NORMAL_CEILING_GAMES,current.totalNormalGames(),gameplay);
        }
        long now=System.currentTimeMillis();
        submit(sender,null,id,()->{database.setMachineRuntimeJson(id,next.toJsonString(),now);return id;},
                done->{tell(sender,"GOD_TEST_READY id="+done+" mode="+mode+"; sit on the machine to test");remote.machineChanged(done);});
    }

    private void commandSetting(CommandSender sender,int id,int setting) {
        if(setting<1||setting>6||state.machine(id)==null)throw new DomainException("INVALID_STATE");
        if(busy(id))throw new DomainException("MACHINE_OCCUPIED");
        int old=state.machine(id).setting(); UUID actor=sender instanceof Player p?p.getUniqueId():null;
        submit(sender,null,id,()->new AdminStore(database,config).setSetting(state,id,setting,actor,System.currentTimeMillis()),
                done->tell(sender,"MACHINE_SETTING id="+id+" old="+old+" new="+done));
    }

    private void commandResetDaily(CommandSender sender,String target) {
        long now=System.currentTimeMillis();
        if(target.equalsIgnoreCase("all")) {
            List<Integer> ids=state.machines().stream().filter(m->!m.deleted()).map(Machine::id).toList();
            if(ids.stream().anyMatch(this::busy))throw new DomainException("MACHINE_OCCUPIED");
            submitMany(sender,ids,()->{new AdminStore(database,config).resetDailyAll(state,ids,now);return ids.size();},
                    count->tell(sender,"DAILY_RESET_ALL machines="+count));
            return;
        }
        int id=Integer.parseInt(target); if(state.machine(id)==null)throw new DomainException("INVALID_STATE");
        if(busy(id))throw new DomainException("MACHINE_OCCUPIED");
        submit(sender,null,id,()->{new AdminStore(database,config).resetDaily(state,id,now);return id;},done->tell(sender,"DAILY_RESET "+done));
    }

    private void commandEvent(CommandSender sender,String[] args) {
        AdminStore store=new AdminStore(database,config);
        if(args.length==2&&args[1].equalsIgnoreCase("status")) {
            submit(sender,null,0,()->store.eventStatus(state),status->tell(sender,"EVENT_STATUS active="+status.get("activeProfile").getAsString()+" next="+(status.get("nextProfile").isJsonNull()?"none":status.get("nextProfile").getAsString()))); return;
        }
        if(args.length==3&&args[1].equalsIgnoreCase("next")&&args[2].equalsIgnoreCase("clear")) {
            submit(sender,null,0,()->{store.clearNextProfile();return "cleared";},done->tell(sender,"EVENT_NEXT cleared")); return;
        }
        if(args.length==3&&args[1].equalsIgnoreCase("next")) {
            String profile=args[2]; submit(sender,null,0,()->{store.setNextProfile(profile);return profile;},done->tell(sender,"EVENT_NEXT "+done)); return;
        }
        throw new DomainException("INVALID_STATE");
    }

    private Machine.Location target(CommandSender sender) {
        if (!(sender instanceof Player player)) throw new DomainException("PLAYER_REQUIRED");
        var hit = player.rayTraceBlocks(5.0, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null || !Tag.BUTTONS.isTagged(hit.getHitBlock().getType())) throw new DomainException("BUTTON_REQUIRED");
        return location(hit.getHitBlock());
    }
    private static Machine.Location location(Block block) {
        String facing = block.getBlockData() instanceof Directional directional ? directional.getFacing().name() : "UP";
        return new Machine.Location(block.getWorld().getUID(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), facing);
    }
    public static ItemStack machineKey() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        var meta = key.getItemMeta(); meta.displayName(Component.text("Piri 台鍵"));
        meta.getPersistentDataContainer().set(ITEM_TYPE, PersistentDataType.STRING, "machine_key");
        meta.getPersistentDataContainer().set(ITEM_VERSION, PersistentDataType.INTEGER, 1); key.setItemMeta(meta); return key;
    }
    public static boolean validKey(ItemStack item) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return "machine_key".equals(pdc.get(ITEM_TYPE, PersistentDataType.STRING)) && Integer.valueOf(1).equals(pdc.get(ITEM_VERSION, PersistentDataType.INTEGER));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void interact(PlayerInteractEvent event) {
        main();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || state == null) return;
        var location = location(event.getClickedBlock());
        Machine machine = state.machines().stream().filter(m -> !m.deleted() && m.location().sameBlock(location)).findFirst().orElse(null);
        if (machine == null) return;
        boolean blockedByOther = event.useInteractedBlock() == Event.Result.DENY;
        event.setUseInteractedBlock(Event.Result.DENY); event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND || blockedByOther || !ready()) return;
        Player player = event.getPlayer(); UUID owner = player.getUniqueId();
        if (!plugin.canUseSlot(owner)) { tell(player, "Piri Juggler Client Mod 1.0.0 が必要です"); return; }
        if (player.isOp() && validKey(event.getItem())) {
            var session = admins.open(owner, machine.id(), System.currentTimeMillis());
            sendAdminState(player,session.id(),machine.id()); return;
        }
        if (!machine.enabled()) { error(player, "MACHINE_DISABLED"); return; }
        Session session = state.session(owner);
        if (session != null && session.machine() != machine.id()) {
            tell(player, "すでに台" + session.machine() + "で遊技中です。台" + session.machine() + "を開き、クレジット・メダルが残っている場合は清算してからESCキーで離席してください。");
            return;
        }
        if (pendingMachines.contains(machine.id()) || (state.busy(machine.id()) && (session == null || !session.ownsLock()))) {
            tell(player, "台" + machine.id() + "はほかのプレイヤーが遊技中です。空くまでお待ちください。");
            return;
        }
        submit(player, owner, machine.id(), () -> database.seat(owner, machine.id(), System.currentTimeMillis()), seated -> {
            Machine seatedMachine=state.machine(seated.machine());
            JsonObject open=seated.openPacket();open.addProperty("machineType",seatedMachine.type().name());
            JsonObject publicState=seated.publicState();publicState.addProperty("machineType",seatedMachine.type().name());
            send(player, PacketType.OPEN_MACHINE, open); send(player, PacketType.PUBLIC_STATE, publicState);
            engine(seated.machine()).resume(seated,System.nanoTime()).ifPresent(packet->{send(player,packet);remote.publishOwnerPacket(machine.id(),packet);});
        });
    }

    public void remoteViewerReady(Player player) { main(); if (ready()) remote.viewerReady(player); }
    public void remoteViewerGone(UUID player) { main(); remote.viewerGone(player); }

    @EventHandler
    public void changedWorld(PlayerChangedWorldEvent event) {
        main();
        if (ready() && plugin.canUseSlot(event.getPlayer().getUniqueId())) remote.worldChanged(event.getPlayer());
    }

    private void sendAdminState(Player player,UUID adminId,int machine) {
        plugin.executors().database(() -> database.adminState(machine), (json, error) -> {
            if (stopped || !player.isOnline()) return;
            var current=admins.current(player.getUniqueId(),System.currentTimeMillis());
            if (!player.isOp()) { admins.close(player.getUniqueId()); error(player, "NOT_OP"); return; }
            if(current==null||!current.id().equals(adminId)||current.machine()!=machine)return;
            if (error != null) { failure(player, error); return; }
            json.addProperty("adminSessionId", adminId.toString());
            json.addProperty("busy", busy(machine)); send(player, PacketType.ADMIN_STATE, json);
        });
    }

    public void receive(Player player, Envelope envelope) {
        main(); if (!ready() || envelope.packetType() == PacketType.HELLO) return;
        if(ADMIN_MUTATIONS.contains(envelope.packetType())||envelope.packetType()==PacketType.ADMIN_CLOSE){adminAction(player,envelope);return;}
        try {
            JsonObject body = envelope.payload();
            var keys=new HashSet<>(body.keySet());boolean hasPressed=keys.remove("pressedIndex");
            if (!keys.equals(Set.of("sessionId","machineId","clientSequence"))) throw new IllegalArgumentException();
            Integer pressed=null;
            if(hasPressed){
                if(!Set.of(PacketType.SPACE_ACTION,PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT).contains(envelope.packetType()))throw new IllegalArgumentException();
                pressed=body.get("pressedIndex").getAsBigDecimal().intValueExact();if(pressed<0||pressed>=21)throw new IllegalArgumentException();
            }
            UUID id = UUID.fromString(body.get("sessionId").getAsString());
            int machine = body.get("machineId").getAsBigDecimal().intValueExact();
            long sequence = body.get("clientSequence").getAsBigDecimal().longValueExact();
            switch(envelope.packetType()) {
                case CLOSE_REQUEST -> closeRequest(player,id,machine,sequence);
                case LOAN -> loanAction(player,id,machine,sequence);
                case INSERT_MEDALS -> insertAction(player,id,machine,sequence);
                case CASH_OUT -> cashoutAction(player,id,machine,sequence);
                default -> gameAction(player,id,machine,sequence,envelope.packetType(),pressed);
            }
        } catch (RuntimeException invalid) { error(player, "SESSION_MISMATCH"); }
    }

    private void adminAction(Player player,Envelope envelope) {
        try {
            JsonObject body=envelope.payload(); var keys=new HashSet<>(body.keySet());
            boolean close=envelope.packetType()==PacketType.ADMIN_CLOSE;
            if(close){if(!keys.equals(Set.of("adminSessionId","machineId","adminSequence")))throw new IllegalArgumentException();}
            else if(!keys.equals(Set.of("adminSessionId","machineId","adminSequence","value")))throw new IllegalArgumentException();
            UUID id=UUID.fromString(body.get("adminSessionId").getAsString());
            int machine=body.get("machineId").getAsBigDecimal().intValueExact();
            long sequence=body.get("adminSequence").getAsBigDecimal().longValueExact();
            admins.accept(player.getUniqueId(),player.isOp(),id,machine,sequence,System.currentTimeMillis());
            if(close){admins.close(player.getUniqueId());return;}
            if(state.machine(machine)==null)throw new DomainException("INVALID_STATE");
            if(busy(machine))throw new DomainException("MACHINE_OCCUPIED");
            AdminStore store=new AdminStore(database,config); long now=System.currentTimeMillis();
            Callable<Object> operation=switch(envelope.packetType()){
                case ADMIN_SET_SETTING -> ()->store.setSetting(state,machine,body.get("value").getAsBigDecimal().intValueExact(),player.getUniqueId(),now);
                case ADMIN_SET_AUTO -> ()->store.setAuto(state,machine,body.get("value").getAsBoolean(),now);
                case ADMIN_SET_ENABLED -> ()->store.setEnabled(state,machine,body.get("value").getAsBoolean(),now);
                case ADMIN_RESET_DAILY -> ()->{store.resetDaily(state,machine,now);return Boolean.TRUE;};
                default -> throw new IllegalArgumentException();
            };
            submit(player,null,machine,operation,unused->{
                sendAdminState(player,id,machine);
                if (envelope.packetType() == PacketType.ADMIN_SET_ENABLED) remote.machineChanged(machine);
            });
        } catch(DomainException error){error(player,error.getMessage());}
        catch(RuntimeException error){error(player,"SESSION_MISMATCH");}
    }

    private boolean reserveEconomy(Player player,UUID id,int machine,long sequence) {
        Session session=state.session(player.getUniqueId());
        if(session==null||!session.id().equals(id)||session.machine()!=machine||session.lifecycle()!=Session.Lifecycle.ACTIVE){reject(player,sequence,"SESSION_MISMATCH");return false;}
        if(sequence<=session.sequence()){reject(player,sequence,"SEQUENCE_OLD");return false;}
        if(pendingPlayers.contains(player.getUniqueId())||pendingMachines.contains(machine)){reject(player,sequence,"BUSY");return false;}
        pendingPlayers.add(player.getUniqueId());pendingMachines.add(machine);return true;
    }
    private void releaseEconomy(UUID player,int machine) {
        pendingPlayers.remove(player);if(machine!=0)pendingMachines.remove(machine);
        Runnable close=deferredClose.remove(player);if(close!=null&&!stopped)close.run();
        if(deferredDisconnect.remove(player)&&!stopped)disconnect(player);
    }
    private void loanAction(Player player,UUID id,int machine,long sequence) {
        Session session=state.session(player.getUniqueId());
        if(session==null||!session.id().equals(id)||session.machine()!=machine||session.lifecycle()!=Session.Lifecycle.ACTIVE){reject(player,sequence,"SESSION_MISMATCH");return;}
        if(!EconomyStore.allowed(session.state())){reject(player,sequence,"INVALID_STATE");return;}
        if(vault==null){reject(player,sequence,"ECONOMY_UNAVAILABLE");return;}
        if(!reserveEconomy(player,id,machine,sequence))return;
        UUID owner=player.getUniqueId();
        final double balance;
        try {balance=vault.balance(player);}catch(RuntimeException failure){releaseEconomy(owner,machine);reject(player,sequence,"VAULT_ERROR");return;}
        if(balance<loanAmount){releaseEconomy(owner,machine);reject(player,sequence,"NOT_ENOUGH_VAULT");return;}
        long now=System.currentTimeMillis();
        plugin.executors().database(()->new Saved<>(new EconomyStore(database).prepareLoan(owner,id,machine,sequence,loanMedals,loanAmount,balance,now),database.state()),(prepared,error)->{
            if(prepared!=null)state=prepared.state;
            if(stopped){releaseEconomy(owner,machine);return;}
            if(error!=null){releaseEconomy(owner,machine);failure(player,error);return;}
            EconomyStore.LoanPlan plan=prepared.value;
            if(plan.state()==EconomyStore.JournalState.APPLIED){releaseEconomy(owner,machine);accepted(player,PacketType.LOAN,sequence);send(player,PacketType.PUBLIC_STATE,state.session(owner).publicState());return;}
            if(plan.state()!=EconomyStore.JournalState.PREPARED){releaseEconomy(owner,machine);reject(player,sequence,"VAULT_ERROR");return;}
            plugin.executors().database(()->{new EconomyStore(database).markLoanCallStarted(plan.transactionId(),System.currentTimeMillis());return null;},(unused,markError)->{
                if(markError!=null){releaseEconomy(owner,machine);failure(player,markError);return;}
                final boolean withdrawn;
                try {withdrawn=vault.withdraw(player,plan.vaultAmount());}
                catch(RuntimeException uncertain){
                    plugin.getLogger().log(Level.SEVERE,"Vault call outcome uncertain; transaction left CALL_STARTED: "+plan.transactionId(),uncertain);
                    releaseEconomy(owner,machine);reject(player,sequence,"VAULT_ERROR");return;
                }
                if(!withdrawn){
                    plugin.executors().database(()->{new EconomyStore(database).rollbackLoan(plan.transactionId(),System.currentTimeMillis());return null;},(ignored,rollbackError)->{
                        releaseEconomy(owner,machine);if(rollbackError!=null)failure(player,rollbackError);else reject(player,sequence,"VAULT_ERROR");
                    });return;
                }
                plugin.executors().database(()->new Saved<>(new EconomyStore(database).applyLoan(owner,id,machine,sequence,plan,System.currentTimeMillis()),database.state()),(applied,applyError)->{
                    if(applied!=null)state=applied.state;
                    releaseEconomy(owner,machine);
                    if(applyError!=null){plugin.getLogger().log(Level.SEVERE,"Vault withdrawal succeeded but local apply failed; manual review required tx="+plan.transactionId(),applyError);reject(player,sequence,"VAULT_ERROR");return;}
                    accepted(player,PacketType.LOAN,sequence);send(player,PacketType.PUBLIC_STATE,applied.value.publicState());
                });
            });
        });
    }
    private void insertAction(Player player,UUID id,int machine,long sequence) {
        if(!reserveEconomy(player,id,machine,sequence))return;
        UUID owner=player.getUniqueId();
        List<EconomyStore.InsertCandidate> candidates=new ArrayList<>();
        for(int slot=0;slot<=35;slot++){
            MedalToken.Value value=MedalToken.read(player.getInventory().getItem(slot));
            if(value!=null)candidates.add(new EconomyStore.InsertCandidate(slot,value.bundleId(),value.amount()));
        }
        plugin.executors().database(()->new Saved<>(new EconomyStore(database).prepareInsert(owner,id,machine,sequence,candidates,System.currentTimeMillis()),database.state()),(prepared,error)->{
            if(prepared!=null)state=prepared.state;
            if(stopped){releaseEconomy(owner,machine);return;}
            if(error!=null){releaseEconomy(owner,machine);failureOrReject(player,sequence,error);return;}
            EconomyStore.InsertPlan plan=prepared.value;
            boolean matches=true;
            for(var replacement:plan.replacements()){
                MedalToken.Value current=MedalToken.read(player.getInventory().getItem(replacement.slot()));
                if(current==null||!current.bundleId().equals(replacement.oldBundleId())||current.amount()!=replacement.oldAmount()){matches=false;break;}
            }
            if(!matches){
                plugin.executors().database(()->new Saved<>(new EconomyStore(database).rollbackInsert(owner,id,plan,System.currentTimeMillis()),database.state()),(rolled,rollbackError)->{
                    if(rolled!=null)state=rolled.state;releaseEconomy(owner,machine);
                    if(rollbackError!=null)failure(player,rollbackError);else reject(player,sequence,"BUSY");
                });return;
            }
            for(var replacement:plan.replacements())player.getInventory().setItem(replacement.slot(),replacement.newBundleId()==null?null:MedalToken.create(replacement.newBundleId(),replacement.newAmount()));
            plugin.executors().database(()->{new EconomyStore(database).markInsertApplied(plan.transactionId(),System.currentTimeMillis());return database.state();},(saved,markError)->{
                if(saved!=null)state=saved;releaseEconomy(owner,machine);
                if(markError!=null){failure(player,markError);return;}
                accepted(player,PacketType.INSERT_MEDALS,sequence);send(player,PacketType.PUBLIC_STATE,plan.session().publicState());
            });
        });
    }
    private void cashoutAction(Player player,UUID id,int machine,long sequence) {
        if(!reserveEconomy(player,id,machine,sequence))return;
        UUID owner=player.getUniqueId();
        plugin.executors().database(()->new Saved<>(new EconomyStore(database).prepareCashout(owner,id,machine,sequence,System.currentTimeMillis()),database.state()),(prepared,error)->{
            if(prepared!=null)state=prepared.state;
            if(stopped){releaseEconomy(owner,machine);return;}
            if(error!=null){releaseEconomy(owner,machine);failureOrReject(player,sequence,error);return;}
            EconomyStore.CashoutPlan plan=prepared.value;
            if(plan.alreadyCompleted()){
                releaseEconomy(owner,machine);accepted(player,PacketType.CASH_OUT,sequence);cashoutResult(player,plan.amount(),0,0);send(player,PacketType.PUBLIC_STATE,plan.session().publicState());return;
            }
            Set<UUID> delivered=new HashSet<>();long deliveredAmount=0;
            for(var bundle:plan.bundles()){
                int slot=player.getInventory().firstEmpty();if(slot<0)break;
                player.getInventory().setItem(slot,MedalToken.create(bundle.id(),bundle.amount()));delivered.add(bundle.id());deliveredAmount+=bundle.amount();
            }
            long finalDelivered=deliveredAmount;
            plugin.executors().database(()->new Saved<>(new EconomyStore(database).finishCashout(owner,plan.transactionId(),delivered,System.currentTimeMillis()),database.state()),(finished,finishError)->{
                if(finished!=null)state=finished.state;releaseEconomy(owner,machine);
                if(finishError!=null){removeBundleItems(player,delivered);failure(player,finishError);return;}
                long pending=plan.amount()-finalDelivered;accepted(player,PacketType.CASH_OUT,sequence);cashoutResult(player,plan.amount(),finalDelivered,pending);send(player,PacketType.PUBLIC_STATE,finished.value.publicState());
            });
        });
    }
    private static void removeBundleItems(Player player,Set<UUID> ids){
        for(int slot=0;slot<=35;slot++){MedalToken.Value value=MedalToken.read(player.getInventory().getItem(slot));if(value!=null&&ids.contains(value.bundleId()))player.getInventory().setItem(slot,null);}
    }
    private void cashoutResult(Player player,long amount,long delivered,long pending){
        JsonObject body=new JsonObject();body.addProperty("amount",amount);body.addProperty("delivered",delivered);body.addProperty("pending",pending);send(player,PacketType.CASHOUT_RESULT,body);
    }
    private void accepted(Player player,PacketType action,long sequence){JsonObject body=new JsonObject();body.addProperty("clientSequence",sequence);body.addProperty("action",action.name());send(player,PacketType.ACTION_ACCEPTED,body);}
    private void failureOrReject(Player player,long sequence,Throwable failure){String code=failure instanceof DomainException?failure.getMessage():"DB_ERROR";if(failure instanceof DomainException)reject(player,sequence,code);else failure(player,failure);}

    private void gameAction(Player player,UUID id,int machine,long sequence,PacketType action,Integer pressedIndex) {
        Session session=state.session(player.getUniqueId());
        if(session==null||!session.id().equals(id)||session.machine()!=machine||session.lifecycle()!=Session.Lifecycle.ACTIVE){reject(player,sequence,"SESSION_MISMATCH");return;}
        if(sequence<=session.sequence()){reject(player,sequence,"SEQUENCE_OLD");return;}
        if(pendingPlayers.contains(player.getUniqueId())||pendingMachines.contains(machine)){reject(player,sequence,"BUSY");return;}
        try {
            GameEngine game=engine(machine);
            var transition=game.plan(session,state.machine(machine),action,sequence,System.currentTimeMillis(),System.nanoTime(),player.getPing(),pressedIndex);
            submit(player,player.getUniqueId(),machine,()->new GameStore(database).commit(transition),saved->{
                for(var packet:game.committed(transition,System.nanoTime())){send(player,packet);if(packet.packetType()!=PacketType.PUBLIC_STATE)remote.publishOwnerPacket(machine,packet);}
                for(var event:game.scheduled(transition))schedule(player,id,machine,event);
            });
        } catch(DomainException error){reject(player,sequence,error.getMessage());}
        catch(ArithmeticException overflow){reject(player,sequence,"INVALID_STATE");}
    }
    private void schedule(Player player,UUID sessionId,int machine,GameTransition.Scheduled event){
        long ticks=Math.max(1,(event.delayMs()+49)/50);
        plugin.getServer().getScheduler().runTaskLater(plugin,()->{
            if(stopped||state==null||!player.isOnline())return;Session current=state.session(player.getUniqueId());
            if(current!=null&&current.id().equals(sessionId)){send(player,event.packet());if(event.packet().packetType()!=PacketType.PUBLIC_STATE)remote.publishOwnerPacket(machine,event.packet());}
        },ticks);
    }
    private void closeRequest(Player player, UUID id, int machine, long sequence) {
        UUID owner = player.getUniqueId();
        if (pendingPlayers.contains(owner)) {
            if (deferredClose.putIfAbsent(owner, () -> closeRequest(player, id, machine, sequence)) != null)
                reject(player, sequence, "BUSY");
            return;
        }
        Session session = state.session(owner);
        if (session == null || !session.id().equals(id) || session.machine() != machine) { reject(player, sequence, "SESSION_MISMATCH"); return; }
        if (sequence <= session.sequence()) { reject(player, sequence, "SEQUENCE_OLD"); return; }
        Session motion=engine(machine).capture(session,System.nanoTime());
        submit(player, owner, machine, () -> database.closeSession(owner, id, machine, sequence, System.currentTimeMillis(), graceMs, motion), reason -> {
            engine(machine).forget(session.id());
            JsonObject body = session.identity();
            if (reason.equals("SESSION_END")) { body.remove("machineId"); send(player, PacketType.SESSION_END, body); }
            else { body.addProperty("reason", reason); send(player, PacketType.SESSION_SUSPENDED, body); }
            remote.broadcastSnapshot(machine);
        });
    }
    public void disconnect(UUID player) {
        main(); admins.close(player);
        if (!ready()) return;
        if (pendingPlayers.contains(player)) { deferredDisconnect.add(player); return; }
        Session session = state.session(player); if (session == null) return;
        Session motion=engine(session.machine()).capture(session,System.nanoTime());
        submit(null, player, session.machine(), () -> { database.disconnect(player, System.currentTimeMillis(), graceMs, motion); return null; }, unused -> {engine(session.machine()).forget(session.id());remote.broadcastSnapshot(session.machine());});
    }
    private <T> void submit(CommandSender sender, UUID player, int machine, Callable<T> operation, Consumer<T> success) {
        main();
        if ((player != null && pendingPlayers.contains(player)) || (machine != 0 && pendingMachines.contains(machine))) { if (sender != null) error(sender, "BUSY"); return; }
        if (player != null) pendingPlayers.add(player); if (machine != 0) pendingMachines.add(machine);
        plugin.executors().database(() -> new Saved<>(operation.call(), database.state()), (saved, error) -> {
            if (saved != null) state = saved.state;
            try { if (!stopped) { if (error != null) failure(sender, error); else success.accept(saved.value); } }
            finally {
                if (player != null) pendingPlayers.remove(player); if (machine != 0) pendingMachines.remove(machine);
                if (player != null && !stopped) {
                    Runnable close = deferredClose.remove(player); if (close != null) close.run();
                    if (deferredDisconnect.remove(player)) disconnect(player);
                }
            }
        });
    }
    private <T> void submitMany(CommandSender sender,List<Integer> machines,Callable<T> operation,Consumer<T> success) {
        main();
        if(machines.stream().anyMatch(pendingMachines::contains)){if(sender!=null)error(sender,"BUSY");return;}
        pendingMachines.addAll(machines);
        plugin.executors().database(() -> new Saved<>(operation.call(),database.state()),(saved,error)->{
            if(saved!=null)state=saved.state;
            try{if(!stopped){if(error!=null)failure(sender,error);else success.accept(saved.value);}}
            finally{pendingMachines.removeAll(machines);}
        });
    }
    private void tick() {
        main(); admins.expire(System.currentTimeMillis());
        if (!ready() || expiring || !pendingPlayers.isEmpty()) return;
        long now = System.currentTimeMillis();
        Map<UUID,Integer> dueMachines=new HashMap<>();
        for(Session s:state.sessions()) {
            boolean dueSession=s.lifecycle()==Session.Lifecycle.SUSPENDED_GRACE&&s.number("lock_expires_at")<=now ||
                    s.lifecycle()==Session.Lifecycle.ACTIVE&&now-s.number("last_activity")>=idleMs;
            if(dueSession)dueMachines.put(s.player(),s.machine());
        }
        if(dueMachines.isEmpty())return;
        expiring = true;
        plugin.executors().database(() -> new Saved<>(database.maintain(now,idleMs),database.state()), (saved, error) -> {
            expiring = false;
            if(saved!=null){
                state=saved.state;
                for(UUID id:saved.value){
                    Session session=state.session(id);if(session!=null)engine(session.machine()).forget(session.id());
                    Player player=Bukkit.getPlayer(id);if(player!=null&&player.isOnline()&&session!=null){JsonObject body=session.identity();body.addProperty("reason","IDLE_TIMEOUT");send(player,PacketType.SESSION_SUSPENDED,body);}
                    Integer changedMachine=dueMachines.get(id);if(changedMachine!=null)remote.broadcastSnapshot(changedMachine);
                }
            }
            if (error != null) failure(null, error);
        });
    }
    private void failure(CommandSender sender, Throwable failure) {
        String code = failure instanceof DomainException ? failure.getMessage() : "DB_ERROR";
        if (!(failure instanceof DomainException)) plugin.getLogger().log(Level.SEVERE, "Database operation failed; transaction rolled back", failure);
        if (sender != null) error(sender, code);
    }
    private static void tell(CommandSender sender, String message) { sender.sendMessage(Component.text(message)); }
    private static String friendlyError(String code) {
        try {
            return switch (ErrorCode.valueOf(code)) {
                case BUSY -> "処理中です。少し待ってからもう一度お試しください。";
                case INVALID_STATE -> "今はこの操作を行えません。";
                case NOT_ENOUGH_CREDIT -> "クレジットが足りません。メダルを投入してください。";
                case ECONOMY_UNAVAILABLE -> "現在、入出金機能を利用できません。";
                case NOT_ENOUGH_VAULT -> "所持金が足りません。";
                case NOT_ENOUGH_MEDALS -> "メダルが足りません。";
                case INVENTORY_FULL -> "インベントリに空きがありません。空きを作ってからお試しください。";
                case MACHINE_OCCUPIED -> "この台はほかのプレイヤーが遊技中です。";
                case MACHINE_DISABLED -> "この台は現在利用できません。";
                case SESSION_MISMATCH -> "台との接続状態が変わりました。いったん離席して、もう一度座り直してください。";
                case SEQUENCE_OLD -> "操作が重複しました。もう一度お試しください。";
                case SPIN_MISMATCH -> "遊技状態が更新されました。もう一度お試しください。";
                case STOP_TOO_EARLY -> "まだリールを止められません。";
                case ALREADY_STOPPED -> "このリールはすでに停止しています。";
                case NOT_OP -> "この操作を行う権限がありません。";
                case INVALID_ITEM -> "このアイテムは使用できません。";
                case TOKEN_REVIEW_REQUIRED -> "このメダルは確認が必要です。管理者に連絡してください。";
                case DB_ERROR -> "処理中にエラーが発生しました。少し待ってからもう一度お試しください。";
                case VAULT_ERROR -> "所持金の処理に失敗しました。少し待ってからもう一度お試しください。";
                case PROTOCOL_MISMATCH -> "サーバーとModのバージョンが一致していません。Modを更新してください。";
            };
        } catch (IllegalArgumentException commandOnlyCode) {
            return code;
        }
    }
    private void error(CommandSender sender, String code) {
        tell(sender, friendlyError(code));
        if (sender instanceof Player player) try { send(player, ErrorPackets.error(ErrorCode.valueOf(code))); } catch (IllegalArgumentException commandOnlyCode) { }
    }
    private void reject(Player player, long sequence, String code) { send(player, ErrorPackets.rejected(sequence, ErrorCode.valueOf(code))); }
    private void send(Player player, PacketType type, JsonObject body) { send(player, new Envelope(Protocol.VERSION, type, body)); }
    private void send(Player player, Envelope packet) {
        if (player.isOnline() && !stopped) {
            player.sendPluginMessage(plugin, Protocol.CHANNEL, EnvelopeCodec.encode(packet));
            if (packet.packetType() == PacketType.PUBLIC_STATE && packet.payload().has("machineId")) {
                try { remote.publishOwnerPacket(packet.payload().get("machineId").getAsInt(), packet); }
                catch (RuntimeException invalidPublicState) { plugin.getLogger().warning("Skipped malformed PUBLIC_STATE remote mirror"); }
            }
        }
    }
    public void shutdown() {
        main(); stopped = true; admins.clear(); remote.clear();
        try { plugin.executors().databaseBarrier(() -> { if (database != null) database.shutdown(System.currentTimeMillis(), graceMs); return null; }).get(30, TimeUnit.SECONDS); }
        catch (Exception failure) { plugin.getLogger().log(Level.SEVERE, "Database shutdown flush failed", failure); }
    }
}
