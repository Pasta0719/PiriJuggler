package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.common.protocol.PacketType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Temporary non-gameplay screen for machine families whose engine/UI is not implemented yet.
 * It deliberately does not reuse the Juggler cabinet.
 */
final class MachineUnavailableScreen extends Screen {
    private final String machineType;
    private final SlotInput input;
    private boolean closing;

    MachineUnavailableScreen(String machineType, SlotInput input) {
        super(Text.literal("Piri Slot - " + machineType));
        this.machineType = machineType;
        this.input = input;
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, machineType, width / 2, height / 2 - 18, 0xffffff);
        context.drawCenteredTextWithShadow(textRenderer, "Machine UI is not implemented yet", width / 2, height / 2 + 4, 0xbfbfbf);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() {
        if (!closing) {
            closing = true;
            input.send(PacketType.CLOSE_REQUEST);
        }
    }
}
