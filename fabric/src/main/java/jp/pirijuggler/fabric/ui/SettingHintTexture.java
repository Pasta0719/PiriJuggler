package jp.pirijuggler.fabric.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Shared nine-frame atlas for owner GUI and spectator cabinet. */
public final class SettingHintTexture {
    public static final Identifier ATLAS=Identifier.of("piri","textures/juggler_god/lamp/setting_hint_atlas.png");
    public static final int TILE_W=240, TILE_H=100, ATLAS_H=900;
    public static int frame(String color){
        if(color==null)return 0;
        return switch(color) {
            case "WHITE"->1; case "BLUE"->2; case "GREEN"->3; case "YELLOW"->4;
            case "PURPLE"->5; case "RED"->6; case "GOLD"->7; case "RAINBOW"->8;
            default->0;
        };
    }
    public static void draw(DrawContext context,SlotLayout.Rect rect,String color) {
        context.getMatrices().push();
        context.getMatrices().translate(rect.x(),rect.y(),0);
        context.getMatrices().scale(rect.w()/(float)TILE_W,rect.h()/(float)TILE_H,1);
        context.drawTexture(ATLAS,0,0,0,frame(color)*TILE_H,
                TILE_W,TILE_H,TILE_W,ATLAS_H);
        context.getMatrices().pop();
    }
    private SettingHintTexture(){}
}
