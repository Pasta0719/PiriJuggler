package jp.pirijuggler.runtime.paper;

import com.google.gson.*;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import org.bukkit.*;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import jp.pirijuggler.common.protocol.*;

/** Test-only world fixture and read-only production-state observer. */
public final class Phase02Observer implements Listener {
    private final JavaPlugin plugin;
    private final JsonArray commands = new JsonArray(), clicks = new JsonArray();
    private final JsonArray inbound = new JsonArray(), fixtures = new JsonArray();
    private long fixtureCompleted;
    private final World world;
    public Phase02Observer(JavaPlugin plugin) {
        this.plugin = plugin; world = Bukkit.getWorlds().getFirst();
        world.setTime(6000); world.setStorm(false); world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false); world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
        for (int x = -3; x <= 7; x++) for (int z = -3; z <= 6; z++) {
            world.getBlockAt(x,64,z).setType(Material.STONE);
            for (int y = 65; y <= 69; y++) world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        for (int x : new int[]{0,2,3}) {
            world.getBlockAt(x,66,-1).setType(Material.STONE);
            Switch button = (Switch) Bukkit.createBlockData(Material.STONE_BUTTON);
            button.setAttachedFace(FaceAttachable.AttachedFace.WALL); button.setFacing(org.bukkit.block.BlockFace.SOUTH);
            world.getBlockAt(x,66,0).setBlockData(button,false);
        }
        if ("phase12".equals(System.getProperty("piri.runtime.phase", ""))) {
            for (int x=-3;x<=3;x++) for (int z=-3;z<=2;z++) {
                world.getBlockAt(x,66,z-1).setType(Material.STONE,false);
                Switch button=(Switch)Bukkit.createBlockData(Material.STONE_BUTTON);
                button.setAttachedFace(FaceAttachable.AttachedFace.WALL);
                button.setFacing(org.bukkit.block.BlockFace.SOUTH);
                world.getBlockAt(x,66,z).setBlockData(button,false);
            }
        }
        Bukkit.getPluginManager().registerEvents(this,plugin);
        if (java.util.Set.of("phase03","phase05").contains(System.getProperty("piri.runtime.phase", ""))) {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,Protocol.CHANNEL);
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin,Protocol.CHANNEL,(channel,player,bytes)->{
                var packet=EnvelopeCodec.decode(bytes);var record=new JsonObject();record.addProperty("type",packet.packetType().name());record.add("payload",packet.payload());record.addProperty("player",player.getName());inbound.add(record);
            });
        }
        Bukkit.getScheduler().runTaskTimer(plugin,this::record,5,5);
    }
    @EventHandler public void joined(PlayerJoinEvent event) {
        if (!event.getPlayer().getName().startsWith("PiriRuntimeTest")) return;
        var player = event.getPlayer(); player.setOp(true); player.setGameMode(GameMode.CREATIVE); player.setCollidable(false); player.getInventory().clear();
        player.teleport(new Location(world,.5,65,3.5,180,2));
    }
    @EventHandler(priority=EventPriority.MONITOR) public void command(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().startsWith("/piri ")) { JsonObject item = new JsonObject(); item.addProperty("player",event.getPlayer().getName()); item.addProperty("command",event.getMessage());
            var hit = event.getPlayer().rayTraceBlocks(5); item.addProperty("rayTarget",hit == null || hit.getHitBlock() == null ? null : hit.getHitBlock().getType() + ":" + hit.getHitBlock().getX()); commands.add(item); }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void click(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !Tag.BUTTONS.isTagged(event.getClickedBlock().getType())) return;
        JsonObject item = new JsonObject(); item.addProperty("player",event.getPlayer().getName()); item.addProperty("x",event.getClickedBlock().getX());
        item.addProperty("action",event.getAction().name()); item.addProperty("denied",event.useInteractedBlock() == Event.Result.DENY); clicks.add(item);
    }
    private void record() {
        if ("phase03".equals(System.getProperty("piri.runtime.phase"))) {
            Path fixture=Path.of(System.getProperty("piri.runtime.serverResult")).resolveSibling("fixture-"+(fixtureCompleted+1)+".json");
            try {if(Files.exists(fixture)){
                var request=JsonParser.parseString(Files.readString(fixture)).getAsJsonObject();var player=Bukkit.getPlayerExact("PiriRuntimeTest");
                if(player!=null){var packet=Envelope.current(PacketType.valueOf(request.get("type").getAsString()),request.getAsJsonObject("payload"));player.sendPluginMessage(plugin,Protocol.CHANNEL,EnvelopeCodec.encode(packet));fixtures.add(request);fixtureCompleted++;}
            }}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
        }
        var production = (PiriJugglerPlugin) Bukkit.getPluginManager().getPlugin("PiriJuggler");
        JsonObject result = new JsonObject(); result.addProperty("serverVersion",Bukkit.getVersion()); result.addProperty("enabled",production != null && production.isEnabled());
        if (production != null && production.machines() != null && production.machines().ready()) {
            var state = production.machines().snapshot(); result.addProperty("ready",true); result.addProperty("period",state.period());
            result.add("machines",new Gson().toJsonTree(state.machines())); result.add("sessions",new Gson().toJsonTree(state.sessions().stream().map(s -> s.snapshot()).toList()));
        }
        result.add("commands",commands); result.add("clicks",clicks);
        if("phase04".equals(System.getProperty("piri.runtime.phase"))) result.add("phase04",Phase04Harness.snapshot());
        if("phase05".equals(System.getProperty("piri.runtime.phase")))result.add("phase05",Phase05Fixture.snapshot());
        result.add("inbound",inbound);result.add("fixtures",fixtures);result.addProperty("fixtureCompleted",fixtureCompleted);
        JsonArray blocks = new JsonArray(); for (int x : new int[]{0,2,3}) { JsonObject block = new JsonObject(); block.addProperty("x",x); block.addProperty("type",world.getBlockAt(x,66,0).getType().name()); block.addProperty("powered",((Switch) world.getBlockAt(x,66,0).getBlockData()).isPowered()); blocks.add(block); } result.add("buttons",blocks);
        try { Path output = Path.of(System.getProperty("piri.runtime.serverResult")); Files.createDirectories(output.getParent()); Files.writeString(output,result.toString()); }
        catch (java.nio.file.FileSystemException sharingConflict) { plugin.getLogger().fine("Retrying observation write on next tick: " + sharingConflict); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
