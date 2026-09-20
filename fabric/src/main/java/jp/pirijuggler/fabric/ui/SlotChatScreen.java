package jp.pirijuggler.fabric.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;

/** Chat overlay that can return to any Piri machine screen. */
public final class SlotChatScreen extends ChatScreen {
    private final Screen parent;
    public SlotChatScreen(Screen parent){super("");this.parent=parent;}
    @Override public void renderBackground(DrawContext c,int x,int y,float delta){}
    @Override public void render(DrawContext c,int x,int y,float delta){parent.render(c,x,y,delta);client.inGameHud.getChatHud().render(c,client.inGameHud.getTicks(),x,y,true);super.render(c,x,y,delta);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){boolean result=super.keyPressed(key,scan,modifiers);if(client.currentScreen==null)client.setScreen(parent);return result;}
    @Override public void close(){client.setScreen(parent);}
    @Override public void resize(net.minecraft.client.MinecraftClient client,int width,int height){parent.resize(client,width,height);super.resize(client,width,height);}
}
