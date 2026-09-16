package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.fabric.network.ClientSession;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

public final class AdminUi {
    public static void receive(Envelope packet, ClientSession session, Consumer<Envelope> sender) {
        if (packet.packetType() != PacketType.ADMIN_STATE) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof AdminScreen)) client.setScreen(new AdminScreen(session, sender));
    }

    public static boolean isOpen() {
        return MinecraftClient.getInstance().currentScreen instanceof AdminScreen;
    }

    private AdminUi() {}
}
