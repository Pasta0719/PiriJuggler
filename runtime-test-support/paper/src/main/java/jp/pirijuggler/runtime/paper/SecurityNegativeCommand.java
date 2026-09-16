package jp.pirijuggler.runtime.paper;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.session.Session;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

final class SecurityNegativeCommand implements CommandExecutor {
    private final RuntimeObserver helper;

    SecurityNegativeCommand(RuntimeObserver helper) { this.helper = helper; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("PLAYER_REQUIRED"); return true; }
        if (!player.isOp()) { sender.sendMessage("NOT_OP"); return true; }

        var plugin = helper.getServer().getPluginManager().getPlugin("PiriJuggler");
        if (!(plugin instanceof PiriJugglerPlugin production) || !production.isEnabled()) {
            sender.sendMessage("PIRISECURITY FAIL production_plugin_missing"); return true;
        }
        if (!production.canUseSlot(player.getUniqueId())) {
            sender.sendMessage("PIRISECURITY FAIL open_a_machine_first"); return true;
        }

        Session before = production.machines().snapshot().session(player.getUniqueId());
        if (before == null || before.lifecycle() != Session.Lifecycle.ACTIVE) {
            sender.sendMessage("PIRISECURITY FAIL active_session_required"); return true;
        }

        var oldBody = before.identity();
        oldBody.addProperty("clientSequence", before.sequence());
        Envelope duplicate = Envelope.current(PacketType.CASH_OUT, oldBody);
        production.machines().receive(player, duplicate);
        production.machines().receive(player, duplicate);

        Session afterDuplicate = production.machines().snapshot().session(player.getUniqueId());
        boolean duplicateSafe = unchanged(before, afterDuplicate);

        JsonObject fakeBody = new JsonObject();
        fakeBody.addProperty("sessionId", UUID.randomUUID().toString());
        fakeBody.addProperty("machineId", before.machine());
        fakeBody.addProperty("clientSequence", before.sequence() + 1);
        production.machines().receive(player, Envelope.current(PacketType.CASH_OUT, fakeBody));

        Session afterFake = production.machines().snapshot().session(player.getUniqueId());
        boolean fakeSafe = unchanged(before, afterFake);
        boolean passed = duplicateSafe && fakeSafe;

        sender.sendMessage("PIRISECURITY " + (passed ? "PASS" : "FAIL")
                + " duplicateNoSideEffect=" + duplicateSafe
                + " fakeSessionNoSideEffect=" + fakeSafe
                + " sequence=" + before.sequence()
                + " credit=" + before.number("credit")
                + " held=" + before.number("held_medals"));
        return true;
    }

    private static boolean unchanged(Session a, Session b) {
        return b != null
                && a.id().equals(b.id())
                && a.machine() == b.machine()
                && a.sequence() == b.sequence()
                && a.state() == b.state()
                && a.lifecycle() == b.lifecycle()
                && a.number("credit") == b.number("credit")
                && a.number("held_medals") == b.number("held_medals")
                && a.number("current_bet") == b.number("current_bet")
                && a.number("pay_display") == b.number("pay_display");
    }
}
