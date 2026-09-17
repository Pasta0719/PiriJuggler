package jp.pirijuggler.paper.security;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.machine.MachineService;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Persistent doors that can only be toggled with the existing machine key. */
public final class SecurityDoorService implements Listener {
    private record DoorLocation(UUID world, int x, int y, int z) {
        String serialize() { return world + "," + x + "," + y + "," + z; }
        static DoorLocation parse(String value) {
            String[] p=value.split(",",-1);
            if(p.length!=4)throw new IllegalArgumentException("door location");
            return new DoorLocation(UUID.fromString(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]));
        }
    }

    private final PiriJugglerPlugin plugin;
    private final File file;
    private final Set<DoorLocation> secured=new HashSet<>();

    public SecurityDoorService(PiriJugglerPlugin plugin) {
        this.plugin=plugin;
        this.file=new File(plugin.getDataFolder(),"security-doors.yml");
        load();
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }

    public boolean handle(CommandSender sender,String[] args) {
        if(args.length==0||!args[0].equalsIgnoreCase("door"))return false;
        if(!sender.isOp())return true;
        if(!(sender instanceof Player player)){sender.sendMessage("この操作はゲーム内から実行してください。");return true;}
        if(args.length!=2||(!args[1].equalsIgnoreCase("secure")&&!args[1].equalsIgnoreCase("normal"))){
            sender.sendMessage("使い方: /piri door <secure|normal>");return true;
        }
        Block door=targetDoor(player);
        if(door==null){sender.sendMessage("5ブロック以内のドアに照準を合わせてください。");return true;}
        Block lower=lowerHalf(door);
        DoorLocation location=key(lower);
        if(args[1].equalsIgnoreCase("secure")){
            secured.add(location);save();sender.sendMessage("このドアを台鍵専用にしました。");
        }else{
            secured.remove(location);save();sender.sendMessage("このドアの台鍵ロックを解除しました。");
        }
        return true;
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void onInteract(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND||event.getAction()!=Action.RIGHT_CLICK_BLOCK||event.getClickedBlock()==null)return;
        Block clicked=event.getClickedBlock();
        if(!(clicked.getBlockData() instanceof Door))return;
        Block lower=lowerHalf(clicked);
        if(!secured.contains(key(lower)))return;
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if(!MachineService.validKey(event.getItem()))return;
        toggle(lower);
    }

    /** A secured door must not be opened by redstone instead of the key. */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void onRedstone(BlockRedstoneEvent event) {
        Block block=event.getBlock();
        if(block.getBlockData() instanceof Door && secured.contains(key(lowerHalf(block))))event.setNewCurrent(0);
    }

    /** Prevent neighboring redstone physics from changing a secured door's state. */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block=event.getBlock();
        if(block.getBlockData() instanceof Door && secured.contains(key(lowerHalf(block))))event.setCancelled(true);
    }

    private void toggle(Block lower) {
        if(!(lower.getBlockData() instanceof Door lowerDoor))return;
        boolean open=!lowerDoor.isOpen();
        lowerDoor.setOpen(open);
        lower.setBlockData(lowerDoor,false);
        Block upper=lower.getRelative(0,1,0);
        if(upper.getBlockData() instanceof Door upperDoor){upperDoor.setOpen(open);upper.setBlockData(upperDoor,false);}
    }

    private Block targetDoor(Player player) {
        var hit=player.rayTraceBlocks(5.0, FluidCollisionMode.NEVER);
        if(hit==null||hit.getHitBlock()==null||!Tag.DOORS.isTagged(hit.getHitBlock().getType()))return null;
        return hit.getHitBlock();
    }

    private static Block lowerHalf(Block block) {
        if(!(block.getBlockData() instanceof Door door))return block;
        return door.getHalf()== Bisected.Half.TOP?block.getRelative(0,-1,0):block;
    }

    private static DoorLocation key(Block lower) {return new DoorLocation(lower.getWorld().getUID(),lower.getX(),lower.getY(),lower.getZ());}

    private void load() {
        secured.clear();
        if(!file.isFile())return;
        YamlConfiguration yaml=YamlConfiguration.loadConfiguration(file);
        for(String raw:yaml.getStringList("doors")){
            try{secured.add(DoorLocation.parse(raw));}
            catch(RuntimeException bad){plugin.getLogger().warning("Ignored invalid security door entry: "+raw);}
        }
    }

    private void save() {
        YamlConfiguration yaml=new YamlConfiguration();
        List<String> rows=secured.stream().map(DoorLocation::serialize).sorted().toList();
        yaml.set("doors",rows);
        try{yaml.save(file);}catch(IOException error){plugin.getLogger().log(Level.SEVERE,"Could not save security-doors.yml",error);}
    }
}
