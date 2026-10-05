package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.common.protocol.PacketType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Functional baseline controller for the physical pachinko machine.
 * The authoritative ball/kurun presentation stays in the world renderer; this
 * screen only sends machine-family actions and never resolves an outcome locally.
 */
public final class PachinkoScreen extends Screen {
    private final SlotInput input;
    private boolean closing;

    public PachinkoScreen(SlotInput input) {
        super(Text.literal("Piri Pachinko"));
        this.input = input;
    }

    @Override protected void init() {
        int y=height/2+42;
        addDrawableChild(ButtonWidget.builder(Text.literal("LOAN 250"), button -> input.send(PacketType.LOAN))
                .dimensions(width/2-156,y,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("FIRE"), button -> input.send(PacketType.PACHINKO_FIRE))
                .dimensions(width/2-50,y,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("RESOLVE"), button -> input.send(PacketType.PACHINKO_PRESENTATION))
                .dimensions(width/2+56,y,100,20).build());
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        renderBackground(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,"PACHINKO",width/2,height/2-42,0xffffff);
        context.drawCenteredTextWithShadow(textRenderer,"Ball / kurun motion is rendered on the physical machine",width/2,height/2-20,0xbfbfbf);
        context.drawCenteredTextWithShadow(textRenderer,"SPACE: FIRE   ENTER: RESOLVE   L: LOAN",width/2,height/2+2,0xbfbfbf);
        super.render(context,mouseX,mouseY,delta);
    }

    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
        PacketType action=switch(keyCode) {
            case GLFW.GLFW_KEY_SPACE -> PacketType.PACHINKO_FIRE;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> PacketType.PACHINKO_PRESENTATION;
            case GLFW.GLFW_KEY_L -> PacketType.LOAN;
            default -> null;
        };
        if(action!=null&&input.key(keyCode,action))return true;
        return super.keyPressed(keyCode,scanCode,modifiers);
    }

    @Override public boolean keyReleased(int keyCode,int scanCode,int modifiers) {
        input.release(keyCode);
        return super.keyReleased(keyCode,scanCode,modifiers);
    }

    @Override public void close() {
        if(!closing){
            closing=true;
            input.send(PacketType.CLOSE_REQUEST);
        }
    }
}
