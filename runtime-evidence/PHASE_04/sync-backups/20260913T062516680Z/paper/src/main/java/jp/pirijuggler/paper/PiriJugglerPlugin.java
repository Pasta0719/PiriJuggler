package jp.pirijuggler.paper;

import jp.pirijuggler.common.protocol.Protocol;
import jp.pirijuggler.common.protocol.EnvelopeCodec;
import jp.pirijuggler.common.protocol.ProtocolException;
import jp.pirijuggler.paper.machine.MachineService;
import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.network.ServerHandshake;
import jp.pirijuggler.paper.threading.PaperMainThread;
import jp.pirijuggler.paper.threading.TaskExecutors;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

public final class PiriJugglerPlugin extends JavaPlugin implements PluginMessageListener, Listener {
    private PaperMainThread mainThread;
    private TaskExecutors executors;
    private ServerHandshake handshake;
    private boolean configurationValid;
    private MachineService machines;

    @Override public void onEnable() {
        mainThread = new PaperMainThread(this);
        handshake = new ServerHandshake(mainThread);
        executors = new TaskExecutors(mainThread);
        try {
            saveDefaultConfig();
            try (var reader = Files.newBufferedReader(getDataFolder().toPath().resolve("config.yml"), StandardCharsets.UTF_8)) {
                ConfigValidation.Result result = ConfigValidation.load(reader);
                configurationValid = result.valid();
                for (String error : result.errors()) getLogger().severe("Gameplay disabled: " + error);
                if (configurationValid) machines = new MachineService(this, result.values());
            }
        } catch (IOException | RuntimeException exception) {
            configurationValid = false;
            getLogger().log(java.util.logging.Level.SEVERE, "Gameplay disabled: config.yml could not be loaded", exception);
        }
        getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Protocol " + Protocol.VERSION + "; configuration " + (configurationValid ? "valid" : "invalid; gameplay disabled"));
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!Protocol.CHANNEL.equals(channel)) return;
        byte[] ownedMessage = message.clone();
        mainThread.execute(() -> {
            if (isEnabled() && player.isOnline()) {
                handshake.receive(player.getUniqueId(), ownedMessage).ifPresent(reply -> player.sendPluginMessage(this, Protocol.CHANNEL, reply));
                if (canUseSlot(player.getUniqueId())) try { machines.receive(player, EnvelopeCodec.decode(ownedMessage)); }
                catch (ProtocolException invalid) { getLogger().fine("Rejected invalid gameplay envelope"); }
            }
        });
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        handshake.disconnect(event.getPlayer().getUniqueId());
        if (machines != null) machines.disconnect(event.getPlayer().getUniqueId());
    }

    public boolean canUseSlot(UUID player) {
        mainThread.requireMainThread();
        return configurationValid && machines != null && machines.ready() && handshake.canUseSlot(player);
    }

    public TaskExecutors executors() { mainThread.requireMainThread(); return executors; }
    public MachineService machines() { mainThread.requireMainThread(); return machines; }

    @Override public void onDisable() {
        configurationValid = false;
        if (machines != null) machines.shutdown();
        if (handshake != null) handshake.clear();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (executors != null) executors.close();
    }
}
