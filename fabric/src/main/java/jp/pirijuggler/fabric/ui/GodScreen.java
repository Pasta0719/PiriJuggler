package jp.pirijuggler.fabric.ui;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.PacketType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** First playable full-screen Piri GOD cabinet UI. */
public final class GodScreen extends Screen {
    private final SlotViewState view;
    private final SlotInput input;

    public GodScreen(SlotViewState view,SlotInput input){
        super(Text.literal("Piri GOD"));this.view=view;this.input=input;
    }
    @Override public boolean shouldPause(){return false;}
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override protected void init(){client.mouse.unlockCursor();}
    @Override public void close(){input.send(PacketType.CLOSE_REQUEST);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_SPACE){input.key(key,PacketType.SPACE_ACTION);return true;}
        return false;
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers){input.release(key);return true;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return false;
        int cx=width/2,cy=height-70;
        if(x>=cx-100&&x<=cx+100&&y>=cy-24&&y<=cy+24){input.mouse("GOD_PLAY",button,PacketType.SPACE_ACTION);return true;}
        if(x>=30&&x<=150&&y>=height-70&&y<=height-35){input.mouse("GOD_INSERT",button,PacketType.INSERT_MEDALS);return true;}
        if(x>=160&&x<=280&&y>=height-70&&y<=height-35){input.mouse("GOD_CASHOUT",button,PacketType.CASH_OUT);return true;}
        return false;
    }
    @Override public void renderBackground(DrawContext c,int mouseX,int mouseY,float delta){c.fill(0,0,width,height,0xff090701);}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        renderBackground(c,mouseX,mouseY,delta);
        int cx=width/2;
        c.drawCenteredTextWithShadow(textRenderer,"PIRI GOD",cx,24,0xffffd56a);
        c.drawCenteredTextWithShadow(textRenderer,phaseTitle(),cx,55,0xffffffff);
        c.drawCenteredTextWithShadow(textRenderer,"LAST  "+view.value("godLastEvent")+"  /  "+view.value("godLastRole"),cx,78,0xffffdf8a);

        JsonObject state=view.publicState();
        int credit=num(state,"credit"),held=num(state,"heldMedals"),pay=num(state,"pay");
        c.drawCenteredTextWithShadow(textRenderer,"CREDIT "+credit+"    MEDALS "+held+"    PAY "+pay,cx,110,0xffe8e8e8);

        String phase=view.value("godPhase");
        if("GG".equals(phase)){
            c.drawCenteredTextWithShadow(textRenderer,"GG  "+view.value("godGgRemaining")+"G   STOCK "+view.value("godStocks"),cx,155,0xffffca3a);
        }else if("G_ZONE".equals(phase)){
            c.drawCenteredTextWithShadow(textRenderer,"G-ZONE  "+view.value("godGZoneRemaining")+"G",cx,155,0xffc8d8ff);
        }else if("SGG".equals(phase)){
            c.drawCenteredTextWithShadow(textRenderer,"SUPER GOD GAME  "+view.value("godSggRemaining")+"G",cx,155,0xffff5b5b);
        }else if("SGG_COMEBACK".equals(phase)){
            c.drawCenteredTextWithShadow(textRenderer,"SGG COMEBACK  "+view.value("godSggRemaining")+"G",cx,155,0xffff8c6b);
        }else if("Z_ZONE".equals(phase)){
            c.drawCenteredTextWithShadow(textRenderer,"Z-ZONE  CHANCE "+view.value("godZZoneRemaining"),cx,155,0xff74eaff);
        }else if("Z_GAME".equals(phase)){
            c.drawCenteredTextWithShadow(textRenderer,"Z-GAME  STOCK "+view.value("godStocks"),cx,155,0xff74eaff);
        }else{
            c.drawCenteredTextWithShadow(textRenderer,"NORMAL",cx,155,0xffdddddd);
        }

        c.fill(cx-100,height-94,cx+100,height-46,0xff6b4700);
        c.drawCenteredTextWithShadow(textRenderer,"PLAY / LEVER  [SPACE]",cx,height-78,0xffffffff);
        c.fill(30,height-70,150,height-35,0xff303030);c.drawCenteredTextWithShadow(textRenderer,"INSERT",90,height-58,0xffffffff);
        c.fill(160,height-70,280,height-35,0xff303030);c.drawCenteredTextWithShadow(textRenderer,"CASH OUT",220,height-58,0xffffffff);
        if(!view.error().isEmpty())c.drawCenteredTextWithShadow(textRenderer,view.error(),cx,height-120,0xffff6666);
        super.render(c,mouseX,mouseY,delta);
    }
    private String phaseTitle(){
        String e=view.value("godLastEvent");
        return switch(e){case "GOD","GOD_IN_GG"->"PREMIUM GOD GAME";case "RED7_SGG"->"RED 7";case "CEILING_Z"->"0 0 0";default->"GOD GAME";};
    }
    private static int num(JsonObject o,String key){return o!=null&&o.has(key)?o.get(key).getAsInt():0;}
}
