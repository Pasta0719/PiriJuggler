package jp.pirijuggler.runtime.paper;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.EnvelopeCodec;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.protocol.Protocol;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Observes the built production plugin; it does not authorize or simulate a session. */
public final class RuntimeObserver extends JavaPlugin {
    @Override public void onEnable() {
        var testCommand=getCommand("piritest");
        if(testCommand==null)throw new IllegalStateException("piritest command missing");
        testCommand.setExecutor(new DevFundCommand(this));
        var balanceCommand=getCommand("piribalance");
        if(balanceCommand==null)throw new IllegalStateException("piribalance command missing");
        balanceCommand.setExecutor(new TestBalanceCommand(this));
        var faultCommand=getCommand("pirifault");
        if(faultCommand==null)throw new IllegalStateException("pirifault command missing");
        faultCommand.setExecutor(new MedalFaultCommand(this));
        var vaultFaultCommand=getCommand("pirivaultfault");
        if(vaultFaultCommand==null)throw new IllegalStateException("pirivaultfault command missing");
        vaultFaultCommand.setExecutor(new VaultFaultCommand());
        var securityCommand=getCommand("pirisecurity");
        if(securityCommand==null)throw new IllegalStateException("pirisecurity command missing");
        securityCommand.setExecutor(new SecurityNegativeCommand(this));

        if ("phase05".equals(System.getProperty("piri.runtime.phase"))) {new Phase02Observer(this);new Phase05Fixture(this);getLogger().info("PIRI_RUNTIME_OBSERVER_READY Phase05");return;}
        if ("phase04".equals(System.getProperty("piri.runtime.phase"))) {
            new Phase02Observer(this); new Phase04Harness(this); getLogger().info("PIRI_RUNTIME_OBSERVER_READY Phase04 " + getServer().getVersion()); return;
        }
        if ("phase02".equals(System.getProperty("piri.runtime.phase")) || "phase03".equals(System.getProperty("piri.runtime.phase"))) {
            new Phase02Observer(this); getLogger().info("PIRI_RUNTIME_OBSERVER_READY Phase02 " + getServer().getVersion()); return;
        }
        getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.CHANNEL, (channel, player, bytes) -> {
            var envelope = EnvelopeCodec.decode(bytes);
            if (!player.getName().equals("PiriRuntimeTest") || envelope.packetType() != PacketType.HELLO) return;
            getServer().getScheduler().runTask(this, () -> {
                var production = (PiriJugglerPlugin) getServer().getPluginManager().getPlugin("PiriJuggler");
                if (production == null) throw new IllegalStateException("Production plugin missing");
                boolean allowed = production.canUseSlot(player.getUniqueId());
                boolean expected = envelope.protocol() == Protocol.VERSION && envelope.payload().get("protocol").getAsInt() == Protocol.VERSION;
                JsonObject result = new JsonObject();
                result.addProperty("timestamp", Instant.now().toString());
                result.addProperty("serverVersion", getServer().getVersion());
                result.addProperty("minecraftVersion", getServer().getMinecraftVersion());
                result.addProperty("player", player.getName());
                result.addProperty("remoteAddress", player.getAddress().getAddress().getHostAddress());
                result.addProperty("envelopeProtocol", envelope.protocol());
                result.add("hello", envelope.payload());
                result.addProperty("productionPluginEnabled", production.isEnabled());
                result.addProperty("gameplayAllowed", allowed);
                result.addProperty("passed", production.isEnabled() && allowed == expected);
                try {
                    Path destination = Path.of(System.getProperty("piri.runtime.serverResult"));
                    Files.createDirectories(destination.toAbsolutePath().getParent());
                    Files.writeString(destination, result.toString());
                } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
                getLogger().info("PIRI_RUNTIME_SERVER " + result);
            });
        });
        getLogger().info("PIRI_RUNTIME_OBSERVER_READY " + getServer().getVersion());
    }
}
