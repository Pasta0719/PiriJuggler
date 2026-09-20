package jp.pirijuggler.fabric.ui;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.reel.GodReelStrip;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Piri GOD cabinet screen with a resource-pack-driven LCD.
 * Gameplay state remains server authoritative; this class only presents it.
 */
public final class GodScreen extends Screen {
    private static final int LOGICAL_W=1920,LOGICAL_H=1080;
    private static final int LCD_X=330,LCD_Y=105,LCD_W=1260,LCD_H=650;
    private final SlotViewState view;
    private final SlotInput input;
    private String pressed="";

    public GodScreen(SlotViewState view,SlotInput input){
        super(Text.literal("Piri GOD"));this.view=view;this.input=input;
    }
    @Override public boolean shouldPause(){return false;}
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override protected void init(){client.mouse.unlockCursor();}
    @Override public void close(){input.send(PacketType.CLOSE_REQUEST);}

    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_T){input.releaseAll();client.setScreen(new SlotChatScreen(null));return true;}
        PacketType action=SlotKeys.action(key,scan);
        if(action!=null){input.key(key,action);pressed=action==PacketType.SPACE_ACTION?"PLAY":action.name();return true;}
        return false;
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers){input.release(key);pressed="";return true;}

    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return false;
        Viewport v=viewport();double lx=v.logicalX(x),ly=v.logicalY(y);
        if(in(lx,ly,285,900,230,85)){pressed="PLAY";return input.mouse("GOD_PLAY",button,PacketType.SPACE_ACTION);}
        if(in(lx,ly,610,900,190,85)){pressed="STOP_LEFT";return input.mouse("GOD_LEFT",button,PacketType.STOP_LEFT);}
        if(in(lx,ly,865,900,190,85)){pressed="STOP_CENTER";return input.mouse("GOD_CENTER",button,PacketType.STOP_CENTER);}
        if(in(lx,ly,1120,900,190,85)){pressed="STOP_RIGHT";return input.mouse("GOD_RIGHT",button,PacketType.STOP_RIGHT);}
        if(in(lx,ly,1380,900,210,85)){pressed="INSERT";return input.mouse("GOD_INSERT",button,PacketType.INSERT_MEDALS);}
        if(in(lx,ly,1610,900,210,85)){pressed="CASH";return input.mouse("GOD_CASHOUT",button,PacketType.CASH_OUT);}
        return false;
    }
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0)pressed="";return button==0;}
    @Override public void renderBackground(DrawContext c,int mouseX,int mouseY,float delta){}

    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        c.fill(0,0,width,height,0xff000000);
        Viewport v=viewport();
        c.getMatrices().push();c.getMatrices().translate(v.x,v.y,0);c.getMatrices().scale((float)v.scale,(float)v.scale,1);

        // Cabinet body.
        c.fill(245,35,1675,1045,0xff120b06);
        c.fill(265,55,1655,1025,0xff5b3408);
        c.fill(285,75,1635,1005,0xff17100a);
        c.fill(LCD_X-15,LCD_Y-15,LCD_X+LCD_W+15,LCD_Y+LCD_H+15,0xffd1a642);
        drawLcd(c);

        drawReels(c);
        // Lower status / controls.
        c.fill(330,785,1590,875,0xff080808);
        drawStatus(c);
        button(c,285,900,230,85,"LEVER / SPACE","PLAY".equals(pressed));
        button(c,610,900,190,85,"LEFT","STOP_LEFT".equals(pressed));
        button(c,865,900,190,85,"CENTER","STOP_CENTER".equals(pressed));
        button(c,1120,900,190,85,"RIGHT","STOP_RIGHT".equals(pressed));
        button(c,1380,900,210,85,"INSERT","INSERT".equals(pressed));
        button(c,1610,900,210,85,"CASH OUT","CASH".equals(pressed));

        String err=view.error();
        if(!err.isEmpty())center(c,err,960,1015,0xffff6666,1.5f);
        c.getMatrices().pop();
        super.render(c,mouseX,mouseY,delta);
    }

    private void drawLcd(DrawContext c){
        String phase=view.value("godPhase"),event=view.value("godLastEvent");
        var scene=GodLcdTheme.scene(event,phase);
        c.fill(LCD_X,LCD_Y,LCD_X+LCD_W,LCD_Y+LCD_H,scene.background());

        // Built-in visual remains useful even with zero external assets.
        long pulse=(System.currentTimeMillis()/350)%2;
        int accent=scene.accent();
        c.fill(LCD_X+28,LCD_Y+28,LCD_X+34,LCD_Y+LCD_H-28,accent);
        c.fill(LCD_X+LCD_W-34,LCD_Y+28,LCD_X+LCD_W-28,LCD_Y+LCD_H-28,accent);
        if(pulse==0)c.fill(LCD_X+55,LCD_Y+90,LCD_X+LCD_W-55,LCD_Y+94,withAlpha(accent,0x88));

        GodLcdTheme.renderLayers(c,scene,LCD_X,LCD_Y);

        center(c,scene.title(),960,LCD_Y+92,accent,titleScale(scene.title()));
        if(!scene.subtitle().isEmpty())center(c,scene.subtitle(),960,LCD_Y+190,0xfff5f0df,2.0f);

        drawCentralDisplay(c,phase,event,accent);
        String role=view.value("godLastRole");
        center(c,"LAST  "+event+"  /  "+role,960,LCD_Y+575,0xffe7e1d1,1.35f);
    }

    private void drawCentralDisplay(DrawContext c,String phase,String event,int accent){
        String main;
        String sub="";
        if("GOD".equals(event)||"GOD_IN_GG".equals(event)){main="7   7   7";sub="PREMIUM";}
        else if("RED7_SGG".equals(event)){main="7   7   7";sub="RED 7";}
        else if("CEILING_Z".equals(event)){main="0   0   0";sub="CEILING";}
        else main=switch(phase){
            case "GG" -> "GG";
            case "G_ZONE" -> "G - ZONE";
            case "SGG" -> "S G G";
            case "SGG_COMEBACK" -> "3 G";
            case "Z_ZONE" -> "Z - ZONE";
            case "Z_GAME" -> "Z - GAME";
            default -> "1   3   5";
        };
        center(c,main,960,LCD_Y+300,accent,main.length()>8?4.5f:6.0f);
        if(!sub.isEmpty())center(c,sub,960,LCD_Y+420,0xffffffff,1.8f);

        if("GG".equals(phase))center(c,"REMAIN "+view.value("godGgRemaining")+"G     STOCK "+view.value("godStocks"),960,LCD_Y+480,0xffffffff,1.8f);
        else if("G_ZONE".equals(phase))center(c,"REMAIN "+view.value("godGZoneRemaining")+"G",960,LCD_Y+480,0xffffffff,1.8f);
        else if("SGG".equals(phase)||"SGG_COMEBACK".equals(phase))center(c,"REMAIN "+view.value("godSggRemaining")+"G",960,LCD_Y+480,0xffffffff,1.8f);
        else if("Z_ZONE".equals(phase))center(c,"CHANCE "+view.value("godZZoneRemaining"),960,LCD_Y+480,0xffffffff,1.8f);
        else if("Z_GAME".equals(phase))center(c,"STOCK "+view.value("godStocks"),960,LCD_Y+480,0xffffffff,1.8f);
    }

    private void drawReels(DrawContext c){
        int top=470,left=560,reelW=245,reelH=235,gap=35;
        for(int reel=0;reel<3;reel++){
            int x=left+reel*(reelW+gap);
            c.fill(x-4,top-4,x+reelW+4,top+reelH+4,0xffd2aa4a);
            c.fill(x,top,x+reelW,top+reelH,0xfff4ead2);
            double phase=view.phase(reel);int middle=(int)Math.floor(phase);double frac=phase-middle;
            for(int row=-1;row<=1;row++){
                int index=middle+row;
                double y=top+reelH/2.0+(row-frac)*72-18;
                drawSymbol(c,GodReelStrip.symbol(reel,index),x+reelW/2,(int)y);
            }
            c.fill(x,top+reelH/2-1,x+reelW,top+reelH/2+1,0x88705010);
        }
    }

    private void drawSymbol(DrawContext c,GodReelStrip.Symbol symbol,int cx,int cy){
        String text=switch(symbol){case GOD->"GOD";case RED7->"7";case BLUE7->"7";case YELLOW7->"7";case BELL->"BELL";case BLANK->"·";};
        int color=switch(symbol){case GOD->0xffffc52e;case RED7->0xffff3535;case BLUE7->0xff4386ff;case YELLOW7->0xffffd83f;case BELL->0xffb8862e;case BLANK->0xff777777;};
        center(c,text,cx,cy,color,symbol==GodReelStrip.Symbol.GOD?2.8f:symbol==GodReelStrip.Symbol.BELL?2.0f:3.5f);
    }

    private void drawStatus(DrawContext c){
        JsonObject state=view.publicState();
        String credit=value(state,"credit"),held=value(state,"heldMedals"),pay=value(state,"pay");
        labelValue(c,"CREDIT",credit,465,810);
        labelValue(c,"PAY",pay,790,810);
        labelValue(c,"MEDALS",held,1115,810);
        labelValue(c,"STATE",view.value("godPhase"),1440,810);
    }

    private void labelValue(DrawContext c,String label,String value,int x,int y){
        center(c,label,x,y,0xffffd36a,1.25f);
        center(c,value,x,y+31,0xffffffff,1.65f);
    }
    private void button(DrawContext c,int x,int y,int w,int h,String label,boolean down){
        c.fill(x,y,x+w,y+h,0xffc49b3f);
        c.fill(x+5,y+5,x+w-5,y+h-5,down?0xff683a08:0xff241608);
        center(c,label,x+w/2,y+h/2-8,0xffffffff,label.length()>10?1.35f:1.7f);
    }

    private static String value(JsonObject o,String key){return o!=null&&o.has(key)?o.get(key).getAsString():"—";}
    private static boolean in(double x,double y,int rx,int ry,int rw,int rh){return x>=rx&&x<=rx+rw&&y>=ry&&y<=ry+rh;}
    private static int withAlpha(int color,int alpha){return (alpha<<24)|(color&0x00ffffff);}
    private static float titleScale(String value){return value.length()>14?2.8f:value.length()>8?3.5f:4.5f;}
    private void center(DrawContext c,String value,int x,int y,int color,float scale){
        c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(scale,scale,1);
        c.drawText(textRenderer,value,-textRenderer.getWidth(value)/2,0,color,true);c.getMatrices().pop();
    }

    private Viewport viewport(){return Viewport.fit(width,height);}
    private record Viewport(double x,double y,double scale){
        static Viewport fit(int width,int height){
            double scale=Math.min(width/(double)LOGICAL_W,height/(double)LOGICAL_H);
            return new Viewport((width-LOGICAL_W*scale)/2.0,(height-LOGICAL_H*scale)/2.0,scale);
        }
        double logicalX(double screen){return (screen-x)/scale;}
        double logicalY(double screen){return (screen-y)/scale;}
    }
}
