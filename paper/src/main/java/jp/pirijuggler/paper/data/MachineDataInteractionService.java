package jp.pirijuggler.paper.data;

import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineService;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Shift-right-clicking a slot machine shows its public data instead of seating the player. */
public final class MachineDataInteractionService implements Listener {
    private final PiriJugglerPlugin plugin;
    private final PublicDataCommand data;

    public MachineDataInteractionService(PiriJugglerPlugin plugin) {
        this.plugin = plugin;
        this.data = new PublicDataCommand(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (!event.getPlayer().isSneaking()) return;
        if (event.getPlayer().isOp() && MachineService.validKey(event.getItem())) return;
        if (plugin.machines() == null || !plugin.machines().ready()) return;

        Machine.Location clicked = location(event.getClickedBlock());
        Machine machine = plugin.machines().snapshot().machines().stream()
                .filter(candidate -> !candidate.deleted() && candidate.enabled() && candidate.location().sameBlock(clicked))
                .findFirst().orElse(null);
        if (machine == null) return;

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        data.handle(event.getPlayer(), new String[]{"data", Integer.toString(machine.id())});
    }

    private static Machine.Location location(Block block) {
        String facing = block.getBlockData() instanceof Directional directional ? directional.getFacing().name() : "UP";
        return new Machine.Location(block.getWorld().getUID(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), facing);
    }
}
