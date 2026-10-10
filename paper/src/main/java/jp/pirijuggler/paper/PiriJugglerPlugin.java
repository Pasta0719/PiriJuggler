package jp.pirijuggler.paper;

import jp.pirijuggler.common.protocol.Protocol;
import jp.pirijuggler.common.protocol.EnvelopeCodec;
import jp.pirijuggler.common.protocol.ProtocolException;
import jp.pirijuggler.paper.machine.MachineService;
import jp.pirijuggler.paper.mobile.MobileRemoteGateway;
import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.config.JugglerGodPremonitionMigration;
import jp.pirijuggler.paper.network.ServerHandshake;
import jp.pirijuggler.paper.threading.PaperMainThread;
import jp.pirijuggler.paper.threading.TaskExecutors;
import jp.pirijuggler.paper.economy.EconomyStartupRecovery;
import jp.pirijuggler.paper.economy.MedalAutoMergeService;
import jp.pirijuggler.paper.economy.MedalMergeCommand;
import jp.pirijuggler.paper.economy.MedalRecoveryListener;
import jp.pirijuggler.paper.economy.PrizeNpcService;
import jp.pirijuggler.paper.economy.PrizeService;
import jp.pirijuggler.paper.economy.ProfitService;
import jp.pirijuggler.paper.data.DataLampPublisher;
import jp.pirijuggler.paper.data.MachineDataInteractionService;
import jp.pirijuggler.paper.data.PublicDataCommand;
import jp.pirijuggler.paper.data.MachineDataSimulationService;
import jp.pirijuggler.paper.security.SecurityDoorService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;
import jp.pirijuggler.paper.reel.ReelEngine;

public final class PiriJugglerPlugin extends JavaPlugin implements PluginMessageListener, Listener {
    private static final String BUILD_IDENTITY = "MOBILE_REMOTE_20261006_FINAL";
    private PaperMainThread mainThread;
    private TaskExecutors executors;
    private ServerHandshake handshake;
    private boolean configurationValid;
    private MachineService machines;
    private MobileRemoteGateway mobileRemote;
    private PrizeService prizes;
    private PrizeNpcService prizeNpcs;
    private ProfitService profit;
    private SecurityDoorService securityDoors;
    private ReelEngine reels;
    private DataLampPublisher dataLamp;
    private PublicDataCommand publicData;
    private MachineDataSimulationService machineSimulation;

    @Override public void onEnable() {
        mainThread = new PaperMainThread(this);
        handshake = new ServerHandshake(mainThread);
        executors = new TaskExecutors(mainThread);
        getLogger().info("PIRI_BUILD_IDENTITY " + BUILD_IDENTITY + " source=" + codeSource(PiriJugglerPlugin.class));

        var buildInfoCommand = getCommand("piribuildinfo");
        if (buildInfoCommand == null) throw new IllegalStateException("piribuildinfo command missing");
        buildInfoCommand.setExecutor((sender, command, label, args) -> {
            sendBuildIdentity(sender);
            return true;
        });

        try {
            reels = new ReelEngine();
            getLogger().info("PIRI_REELS_READY " + reels.verification());
        } catch (RuntimeException failure) {
            getLogger().log(java.util.logging.Level.SEVERE,"Gameplay disabled: reel startup self-test failed",failure);
        }
        try {
            saveDefaultConfig();
            migrateProtocolConfig(getDataFolder().toPath().resolve("config.yml"));
            migrateJugglerGodConfig(getDataFolder().toPath().resolve("config.yml"));
            migrateJugglerGodExtremeConfig(getDataFolder().toPath().resolve("config.yml"));
            migrateJugglerGodPremonitionConfig(getDataFolder().toPath().resolve("config.yml"));
            try (var reader = Files.newBufferedReader(getDataFolder().toPath().resolve("config.yml"), StandardCharsets.UTF_8)) {
                ConfigValidation.Result result = ConfigValidation.load(reader);
                configurationValid = result.valid();
                for (String error : result.errors()) getLogger().severe("Gameplay disabled: " + error);
                if (configurationValid) {
                    machines = new MachineService(this, result.values());
                    mobileRemote = new MobileRemoteGateway(this, machines);
                    dataLamp = new DataLampPublisher(this);
                    publicData = new PublicDataCommand(this);
                    machineSimulation = new MachineDataSimulationService(this,result.values());
                    prizes = new PrizeService(this, result.values());
                    prizeNpcs = new PrizeNpcService(this, prizes);
                    profit = new ProfitService(this);
                    securityDoors = new SecurityDoorService(this);
                    new MachineDataInteractionService(this);
                    EconomyStartupRecovery.reconcile(this);
                    Objects.requireNonNull(getCommand("piri")).setExecutor((sender, command, label, args) ->
                            handleBuildIdentity(sender,args) || mobileRemote.handle(sender,args) || machineSimulation.handle(sender,args) || publicData.handle(sender, args) || prizes.handle(sender, args) || prizeNpcs.handle(sender, args) || profit.handle(sender, args) || securityDoors.handle(sender,args) || machines.onCommand(sender, command, label, args));
                }
            }
        } catch (IOException | RuntimeException exception) {
            configurationValid = false;
            getLogger().log(java.util.logging.Level.SEVERE, "Gameplay disabled: config.yml could not be loaded", exception);
        }
        var mergeCommand = getCommand("pirimerge");
        if (mergeCommand == null) throw new IllegalStateException("pirimerge command missing");
        var medalMerger = new MedalMergeCommand(this);
        mergeCommand.setExecutor(medalMerger);
        new MedalAutoMergeService(this, medalMerger);
        var resetCommand = getCommand("pirireset");
        if (resetCommand == null) throw new IllegalStateException("pirireset command missing");
        resetCommand.setExecutor(new DevResetCommand(this));
        getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new MedalRecoveryListener(this), this);
        getLogger().info("Protocol " + Protocol.VERSION + "; configuration " + (configurationValid ? "valid" : "invalid; gameplay disabled"));
    }

    /**
     * Protocol 2 is a wire compatibility break introduced by Phase12. Preserve every
     * operator setting while migrating only the previous fixed protocol marker.
     */
    private void migrateProtocolConfig(java.nio.file.Path path) throws IOException {
        String original = Files.readString(path, StandardCharsets.UTF_8);
        String migrated = original.replaceFirst("(?m)^protocol_version:\\s*1\\s*$", "protocol_version: " + Protocol.VERSION);
        if (!migrated.equals(original)) {
            Files.writeString(path, migrated, StandardCharsets.UTF_8);
            getLogger().info("Migrated config protocol_version 1 -> " + Protocol.VERSION);
        }
    }

    /**
     * Existing production servers keep their operator config.yml across jar upgrades.
     * saveDefaultConfig() does not merge newly introduced sections, so add the
     * successor-only defaults before strict validation when upgrading an old config.
     */
    private void migrateJugglerGodConfig(java.nio.file.Path path) throws IOException {
        String original = Files.readString(path, StandardCharsets.UTF_8);
        if (original.matches("(?s).*?(?m)^juggler_god\\s*:.*")) return;
        String block = """

juggler_god:
  normal_big_to_heaven_ppm: 125000
  normal_reg_to_heaven_ppm: 62500
  heaven_to_heaven_ppm: 500000
  settings:
    '1': {bonus_scale_ppm: 743613, small_role_scale_ppm: 813500}
    '2': {bonus_scale_ppm: 734884, small_role_scale_ppm: 809600}
    '3': {bonus_scale_ppm: 734653, small_role_scale_ppm: 819100}
    '4': {bonus_scale_ppm: 739194, small_role_scale_ppm: 827000}
    '5': {bonus_scale_ppm: 741839, small_role_scale_ppm: 841000}
    '6': {bonus_scale_ppm: 696323, small_role_scale_ppm: 833500}
""";
        Files.writeString(path, original.stripTrailing() + "\n" + block, StandardCharsets.UTF_8);
        getLogger().info("Migrated existing config with juggler_god defaults");
    }

    private void migrateJugglerGodExtremeConfig(java.nio.file.Path path) throws IOException {
        String original = Files.readString(path, StandardCharsets.UTF_8);
        if (original.matches("(?s).*?(?m)^juggler_god_extreme\\s*:.*")) return;
        String block = """

juggler_god_extreme:
  god_denominator: 16384
  big_payout: 420
  reg_payout: 168
  god_guaranteed_bigs: 8
  god_in_god_big_stock: 10
  normal_big_to_heaven_ppm: 60000
  normal_reg_to_heaven_ppm: 30000
  heaven_to_heaven_ppm: 700000
  settings:
    '1': {bonus_scale_ppm: 564190, small_role_scale_ppm: 700000, god_continuation_percent: 75}
    '2': {bonus_scale_ppm: 554200, small_role_scale_ppm: 700000, god_continuation_percent: 78}
    '3': {bonus_scale_ppm: 558800, small_role_scale_ppm: 700000, god_continuation_percent: 80}
    '4': {bonus_scale_ppm: 562600, small_role_scale_ppm: 700000, god_continuation_percent: 82}
    '5': {bonus_scale_ppm: 571106, small_role_scale_ppm: 700000, god_continuation_percent: 85}
    '6': {bonus_scale_ppm: 537600, small_role_scale_ppm: 700000, god_continuation_percent: 90}
""";
        Files.writeString(path, original.stripTrailing() + "\n" + block, StandardCharsets.UTF_8);
        getLogger().info("Migrated existing config with juggler_god_extreme defaults");
    }

    /**
     * Upgrade only untouched stock JUGGLER GOD tuning rows, keeping operator-modified
     * profiles unchanged. New installs get these values straight from bundled config.
     * Uses text replacement solely for the documented inline per-setting row shape,
     * preserving surrounding comments/formatting instead of rewriting all YAML.
     */
    private void migrateJugglerGodPremonitionConfig(java.nio.file.Path path) throws IOException {
        String original=Files.readString(path,StandardCharsets.UTF_8);
        JugglerGodPremonitionMigration.Result result=JugglerGodPremonitionMigration.migrate(original);
        if(result.changed()>0){
            Files.writeString(path,result.text(),StandardCharsets.UTF_8);
            getLogger().info("Migrated "+result.changed()+" untouched JUGGLER GOD setting rows to context-led defaults");
        }
        if(result.skipped()>0)
            getLogger().warning("Kept "+result.skipped()+" custom JUGGLER GOD tuning rows; review base odds and precursor_two_high_ppm manually");
    }

    private boolean handleBuildIdentity(CommandSender sender,String[] args) {
        if(args.length!=1||!args[0].equalsIgnoreCase("buildinfo"))return false;
        sendBuildIdentity(sender);
        return true;
    }

    private void sendBuildIdentity(CommandSender sender) {
        sender.sendMessage("PIRI_BUILD_IDENTITY " + BUILD_IDENTITY);
        sender.sendMessage("PLUGIN_SOURCE " + codeSource(PiriJugglerPlugin.class));
        sender.sendMessage("DATABASE_SOURCE " + codeSource(jp.pirijuggler.paper.database.PiriDatabase.class));
        sender.sendMessage("PLUGIN_VERSION " + getDescription().getVersion());
    }

    private static String codeSource(Class<?> type) {
        try {
            var source=type.getProtectionDomain().getCodeSource();
            return source==null||source.getLocation()==null?"UNKNOWN":source.getLocation().toString();
        } catch (RuntimeException ignored) {
            return "UNAVAILABLE";
        }
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!Protocol.CHANNEL.equals(channel)) return;
        byte[] ownedMessage = message.clone();
        mainThread.execute(() -> {
            if (isEnabled() && player.isOnline()) {
                boolean compatibleBefore = handshake.canUseSlot(player.getUniqueId());
                handshake.receive(player.getUniqueId(), ownedMessage).ifPresent(reply -> player.sendPluginMessage(this, Protocol.CHANNEL, reply));
                boolean compatibleAfter = handshake.canUseSlot(player.getUniqueId());
                if (!compatibleBefore && compatibleAfter && machines != null && machines.ready()) machines.remoteViewerReady(player);
                if (canUseSlot(player.getUniqueId())) try { machines.receive(player, EnvelopeCodec.decode(ownedMessage)); }
                catch (ProtocolException invalid) { getLogger().fine("Rejected invalid gameplay envelope"); }
            }
        });
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (machines != null && machines.ready() && player.isOnline()) machines.recoverPendingWallet(player);
            if (prizes != null && player.isOnline()) prizes.recoverMobilePrizes(player);
        }, 20L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        handshake.disconnect(player.getUniqueId());
        if (prizes != null) prizes.stashPrizesForMobile(player);
        if (machines != null) {
            machines.stashInventoryForMobile(player);
            machines.remoteViewerGone(player.getUniqueId());
            machines.disconnect(player.getUniqueId());
        }
    }

    public boolean canUseSlot(UUID player) {
        mainThread.requireMainThread();
        return configurationValid && reels != null && machines != null && machines.ready() && handshake.canUseSlot(player);
    }

    public TaskExecutors executors() { mainThread.requireMainThread(); return executors; }
    public MachineService machines() { mainThread.requireMainThread(); return machines; }
    public PrizeService prizes() { mainThread.requireMainThread(); return prizes; }
    /** ReelEngine is immutable except for the solver's concurrent cache, so recovery DB work may safely read it. */
    public ReelEngine reels() { return reels; }

    @Override public void onDisable() {
        configurationValid = false;
        // Do not depend on PlayerQuitEvent ordering during a restart.  Persist every
        // currently-online player's physical mobile assets before MachineService closes.
        // The stash methods are idempotent at the inventory boundary: successfully
        // journaled items are removed immediately, so a later quit event cannot duplicate them.
        if (machines != null && machines.ready()) {
            for (Player player : getServer().getOnlinePlayers()) {
                if (prizes != null) prizes.stashPrizesForMobile(player);
                machines.stashInventoryForMobile(player);
            }
        }
        if (dataLamp != null) dataLamp.close();
        if (mobileRemote != null) mobileRemote.close();
        if (machines != null) machines.shutdown();
        if (handshake != null) handshake.clear();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (executors != null) executors.close();
    }
}
