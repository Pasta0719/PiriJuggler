package jp.pirijuggler.fabric.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.fabric.ErrorMessages;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import static jp.pirijuggler.fabric.ui.UiConstants.color;

public final class SlotScreen extends Screen {
    private final SlotViewState view;private final SlotInput input;private String pressed="";private long leverAt=Long.MIN_VALUE;
    public SlotScreen(SlotViewState view,SlotInput input){super(Text.literal("Piri Juggler"));this.view=view;this.input=input;}
    @Override public boolean shouldPause(){return false;}
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override protected void init(){client.mouse.unlockCursor();}
    @Override public void close(){input.send(PacketType.CLOSE_REQUEST);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_T){input.releaseAll();client.setScreen(new SlotChatScreen(this));return true;}
        PacketType action=SlotKeys.action(key,scan);if(action!=null){input.key(key,action);return true;}return false;
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers){input.release(key);return true;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return false;var v=SlotLayout.Viewport.fit(width,height);
        for(var control:SlotLayout.CONTROLS)if(control.rect().contains(v.logicalX(x),v.logicalY(y))) {
            if(input.mouse(control.name(),button,control.action())){pressed=control.name();if(pressed.equals("LEVER"))leverAt=System.nanoTime();}return true;
        }return false;
    }
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0)pressed="";return button==0;}
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta){}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        c.fill(0,0,width,height,color("SCREEN_OUTSIDE"));var v=SlotLayout.Viewport.fit(width,height);
        c.getMatrices().push();c.getMatrices().translate(v.x(),v.y(),0);c.getMatrices().scale((float)v.scale(),(float)v.scale(),1);
        panel(c,SlotLayout.CABINET,color("CABINET_BG"));
        text(c,"PIRI JUGGLER",960,238,3,true);
        panel(c,SlotLayout.DATA,color("DISPLAY_BG"));data(c);
        c.fill(670,300,1570,690,color("REEL_SEPARATOR"));
        for(int reel=0;reel<3;reel++){
            int x=670+315*reel;c.fill(x,300,x+270,690,color("REEL_BG"));
            // DrawContext's scissor coordinates are GUI coordinates, independent of its matrix.
            var clip=v.clip(new SlotLayout.Rect(x,300,270,390));c.enableScissor(clip.x(),clip.y(),clip.x()+clip.w(),clip.y()+clip.h());
            double phase=view.phase(reel);int middle=(int)Math.floor(phase);double fraction=phase-middle;
            for(int row=-2;row<=2;row++)texture(c,"symbols/"+UiConstants.symbol(reel,middle+row)+".png",x+(270-130)/2.0,430+(row-fraction)*130,130,130,256,256,1);
            c.disableScissor();
        }
        var lamp=SlotLayout.LAMP;String name="lamp/piri_chance_"+(view.lampOn()?"on":"off")+".png";
        if(view.lampOn())for(int[] offset:new int[][]{{-4,0},{4,0},{0,4}})texture(c,name,lamp.x()+offset[0],lamp.y()+offset[1],lamp.w(),lamp.h(),512,256,.18f);
        texture(c,name,lamp.x(),lamp.y(),lamp.w(),lamp.h(),512,256,1);
        panel(c,SlotLayout.STATUS,color("DISPLAY_BG"));
        String[] labels={"CREDIT","BET","PAY","MEDALS"},fields={"credit","bet","pay","heldMedals"};
        for(int n=0;n<4;n++){int x=690+n*198;text(c,labels[n],x,725,2,false);text(c,view.value(fields[n]),x,758,3,false);}
        String state=view.value("gameState");if(state.equals("REPLAY_READY"))text(c,"REPLAY",1050,816,2,true);
        if(state.startsWith("BIG_")||state.startsWith("REG_"))text(c,"COUNT "+view.value("bonusCount"),1090,816,2,true);
        for(var control:SlotLayout.CONTROLS)drawControl(c,control,v.logicalX(mouseX),v.logicalY(mouseY));
        String message=input.closing()?"離席処理中…":errorText();if(!message.isEmpty())text(c,message,1040,990,2,true);
        c.getMatrices().pop();
    }
    private String errorText(){if(view.error().isEmpty())return "";try{return ErrorMessages.japanese(ErrorCode.valueOf(view.error()));}catch(IllegalArgumentException e){return view.error();}}
    private void data(DrawContext c){
        text(c,"No.",356,42,2,false);digits(c,Integer.toString(view.machineId()),356,90,.65f,color("DISPLAY_WHITE"));
        String[] fields={"currentGames","bigCount","regCount","totalGames"},labels={"G","BIG","REG","TOTAL G"};
        var data=view.dataLamp();
        for(int i=0;i<4;i++){int x=555+i*240;text(c,labels[i],x,42,2,false);String number=data==null||!data.has(fields[i])?"-":data.get(fields[i]).getAsString();digits(c,number,x,90,.75f,color(i==1?"DISPLAY_BIG":i==2?"DISPLAY_REG":"DISPLAY_WHITE"));}
    }
    private void drawControl(DrawContext c,SlotLayout.Control control,double mx,double my){
        var r=control.rect();boolean hover=r.contains(mx,my),down=pressed.equals(control.name());
        int fill=color(down?"BUTTON_RED_PRESSED":hover?"BUTTON_RED_HOVER":"BUTTON_RED");
        if(control.name().equals("LEVER")){
            long elapsed=System.nanoTime()-leverAt;double e=leverAt==Long.MIN_VALUE?1:elapsed/1e6;
            double offset=e>=180?0:e<=90?18*e/90:18*(180-e)/90;
            c.fill(r.x()+(r.w()-22)/2,r.y()+35,r.x()+(r.w()+22)/2,r.y()+r.h()-35,color("BUTTON_METAL_DARK"));
            rounded(c,r.x()+(r.w()-70)/2f,(float)(r.y()+35+offset),70,70,35,down?fill:hover?UiConstants.brighter(color("BUTTON_RED")):color("BUTTON_RED"));
            text(c,"LEVER",r.x()+r.w()/2,r.y()+r.h()-25,2,true);
        }else if(control.name().equals("LEFT")||control.name().equals("CENTER")||control.name().equals("RIGHT")){
            float x=r.x()+(r.w()-88)/2f,y=r.y()+(r.h()-88)/2f;
            rounded(c,x,y,88,88,44,hover?UiConstants.brighter(color("BUTTON_METAL")):color("BUTTON_METAL"));
            rounded(c,x+6,y+6,76,76,38,fill);text(c,control.name(),r.x()+r.w()/2,r.y()+r.h()+12,2,true);
        }else{
            rounded(c,r.x(),r.y(),r.w(),r.h(),18,color("BUTTON_METAL_DARK"));rounded(c,r.x()+4,r.y()+4,r.w()-8,r.h()-8,14,fill);
            text(c,control.name(),r.x()+r.w()/2,r.y()+r.h()/2-9,control.name().equals("CASH OUT")?1.5f:2,true);
        }
    }
    private void text(DrawContext c,String value,float x,float y,float scale,boolean centered){
        c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(scale,scale,1);int left=centered?-textRenderer.getWidth(value)/2:0;
        c.drawText(textRenderer,value,left+1,1,color("TEXT_SHADOW"),false);c.drawText(textRenderer,value,left,0,color("TEXT_MAIN"),false);c.getMatrices().pop();
    }
    private static void digits(DrawContext c,String value,float x,float y,float scale,int active){
        c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(scale,scale,1);
        for(int i=0;i<value.length();i++)for(int s=0;s<7;s++){int[] r=SevenSegment.RECTANGLES[s];int tint=SevenSegment.active(value.charAt(i),s)?active:active&0xffffff|Math.round(255*.08f)<<24;c.fill(i*48+r[0],r[1],i*48+r[0]+r[2],r[1]+r[3],tint);}c.getMatrices().pop();
    }
    private void texture(DrawContext c,String path,double x,double y,int w,int h,int tw,int th,float alpha){
        Identifier id=Identifier.of("piri","textures/"+path);client.getTextureManager().bindTexture(id);client.getTextureManager().getTexture(id).setFilter(true,false);
        c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(w/(float)tw,h/(float)th,1);c.setShaderColor(1,1,1,alpha);c.drawTexture(id,0,0,0,0,tw,th,tw,th);c.setShaderColor(1,1,1,1);c.getMatrices().pop();
    }
    private static void panel(DrawContext c,SlotLayout.Rect r,int fill){rounded(c,r.x(),r.y(),r.w(),r.h(),18,color("CABINET_EDGE"));rounded(c,r.x()+4,r.y()+4,r.w()-8,r.h()-8,12,fill);}
    private static void rounded(DrawContext c,float x,float y,float w,float h,float radius,int tint){
        c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN,VertexFormats.POSITION_COLOR);var matrix=c.getMatrices().peek().getPositionMatrix();
        b.vertex(matrix,x+w/2,y+h/2,0).color(tint);
        for(int corner=0;corner<4;corner++)for(int step=0;step<=16;step++){
            double angle=Math.toRadians(corner*90+step*90/16.0);float cx=corner==0||corner==3?x+w-radius:x+radius,cy=corner<2?y+h-radius:y+radius;
            b.vertex(matrix,cx+(float)Math.cos(angle)*radius,cy+(float)Math.sin(angle)*radius,0).color(tint);
        }
        b.vertex(matrix,x+w,y+h-radius,0).color(tint);BufferRenderer.drawWithGlobalProgram(b.end());RenderSystem.enableCull();RenderSystem.disableBlend();
    }
}
