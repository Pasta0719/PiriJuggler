package jp.pirijuggler.fabric.ui;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.fabric.ErrorMessages;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.time.*;
import java.time.format.DateTimeFormatter;
import static jp.pirijuggler.fabric.ui.UiConstants.color;

public final class SlotScreen extends Screen {
    private static final DateTimeFormatter HISTORY_TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Tokyo"));
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
        panel(c,SlotLayout.DATA,color("DISPLAY_BG"));data(c,v.logicalX(mouseX),v.logicalY(mouseY));
        c.fill(670,300,1570,690,color("REEL_SEPARATOR"));
        for(int reel=0;reel<3;reel++){
            int x=670+315*reel;c.fill(x,300,x+270,690,color("REEL_BG"));
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
    private void data(DrawContext c,double mx,double my){
        JsonObject data=view.dataLamp();
        String[] labels={"台 番","現在ゲーム","BIG 回数","REG 回数","トータルG","差 枚","MAX差枚"};
        String[] fields={null,"currentGames","bigCount","regCount","totalGames","todayDifference","todayMaxDifference"};
        String[] units={"","G","回","回","G","枚","枚"};
        int start=350,cell=174,top=27,bottom=101;
        c.fill(342,bottom,1578,bottom+2,color("CABINET_EDGE"));
        for(int i=0;i<labels.length;i++){
            int left=start+i*cell,right=left+156,center=left+78;
            if(i>0)c.fill(left-9,top,left-7,bottom-7,color("BUTTON_METAL_DARK"));
            int tint=i==2?color("DISPLAY_BIG"):i==3?color("DISPLAY_REG"):color("DISPLAY_WHITE");
            text(c,labels[i],center,31,1.45f,true,tint);
            String number=i==0?Integer.toString(view.machineId()):data==null||!data.has(fields[i])?null:data.get(fields[i]).getAsString();
            if(number==null)text(c,"-",center,58,2.35f,true,tint);else digitsFitCentered(c,number,center,57,145,.62f,tint);
            if(!units[i].isEmpty())text(c,units[i],center,88,.82f,true,color("DISPLAY_WHITE"));
        }
        if(data==null)return;

        text(c,"差枚グラフ",350,108,1.05f,false);drawGraph(c,data.has("graph")?data.getAsJsonArray("graph"):new JsonArray(),350,124,400,60);
        text(c,"ボーナス履歴  新しい → 古い",775,108,1.05f,false);
        JsonArray history=data.has("history")?data.getAsJsonArray("history"):new JsonArray();
        String hoverTime=null;
        for(int i=0;i<Math.min(10,history.size());i++){
            JsonObject item=history.get(i).getAsJsonObject();int x=775+i*54;String type=item.get("type").getAsString();
            int tint="BIG".equals(type)?color("DISPLAY_BIG"):color("DISPLAY_REG");text(c,"BIG".equals(type)?"B":"R",x+24,124,1.15f,true,tint);
            digitsFitCentered(c,item.get("games").getAsString(),x+25,148,48,.30f,tint);
            text(c,"G",x+25,174,.70f,true,color("DISPLAY_WHITE"));
            if(mx>=x&&mx<x+52&&my>=120&&my<185&&item.has("occurredAt"))hoverTime=HISTORY_TIME.format(Instant.ofEpochMilli(item.get("occurredAt").getAsLong()));
        }
        if(hoverTime!=null)text(c,hoverTime,1045,188,.9f,true);
        if(data.has("piriChain")&&data.get("piriChain").getAsBoolean()){
            int chain=data.has("piriChainCount")?data.get("piriChainCount").getAsInt():1;
            text(c,"ピリ連チャレンジ中  "+chain+"連目",1460,149,1.1f,true,color("DISPLAY_GREEN"));
        }
    }
    private void drawGraph(DrawContext c,JsonArray graph,float x,float y,float w,float h){
        if(graph.isEmpty())return;
        long min=Long.MAX_VALUE,max=Long.MIN_VALUE,minX=Long.MAX_VALUE,maxX=Long.MIN_VALUE;
        for(JsonElement element:graph){JsonObject p=element.getAsJsonObject();long px=p.get("game").getAsLong(),py=p.get("difference").getAsLong();min=Math.min(min,py);max=Math.max(max,py);minX=Math.min(minX,px);maxX=Math.max(maxX,px);}
        double low,high;if(min==max){low=min-200.0;high=max+200.0;}else if(max-min<400){double mid=(min+max)/2.0;low=mid-200;high=mid+200;}else{double pad=(max-min)*.05;low=min-pad;high=max+pad;}
        if(low<=0&&high>=0){float zy=(float)(y+h-(0-low)/(high-low)*h);c.fill((int)x,(int)zy,(int)(x+w),(int)Math.ceil(zy+1),color("GRAPH_ZERO"));}
        float lastX=0,lastY=0;boolean first=true;
        for(JsonElement element:graph){JsonObject p=element.getAsJsonObject();long px=p.get("game").getAsLong(),py=p.get("difference").getAsLong();float sx=maxX==minX?x:(float)(x+(px-minX)/(double)(maxX-minX)*w);float sy=(float)(y+h-(py-low)/(high-low)*h);
            if(first){c.fill((int)sx-1,(int)sy-1,(int)sx+2,(int)sy+2,color("GRAPH_LINE"));first=false;}else line(c,lastX,lastY,sx,sy,1.5f,color("GRAPH_LINE"));lastX=sx;lastY=sy;}
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
    private void text(DrawContext c,String value,float x,float y,float scale,boolean centered){text(c,value,x,y,scale,centered,color("TEXT_MAIN"));}
    private void text(DrawContext c,String value,float x,float y,float scale,boolean centered,int tint){
        c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(scale,scale,1);int left=centered?-textRenderer.getWidth(value)/2:0;
        c.drawText(textRenderer,value,left+1,1,color("TEXT_SHADOW"),false);c.drawText(textRenderer,value,left,0,tint,false);c.getMatrices().pop();
    }
    private static void digitsFitCentered(DrawContext c,String value,float center,float y,float maxWidth,float baseScale,int active){
        float logical=value.isEmpty()?0:(value.length()-1)*48+40;float scale=baseScale;if(logical>0){float fit=maxWidth/logical;if(fit<scale)scale=Math.max(.45f,fit);if(logical*scale>maxWidth)scale=fit;}digits(c,value,center-logical*scale/2,y,scale,active);
    }
    private static void digitsFit(DrawContext c,String value,float right,float y,float maxWidth,float baseScale,int active){
        float logical=value.isEmpty()?0:(value.length()-1)*48+40;float scale=baseScale;if(logical>0){float fit=maxWidth/logical;if(fit<scale)scale=Math.max(.45f,fit);if(logical*scale>maxWidth)scale=fit;}float x=right-logical*scale;digits(c,value,x,y,scale,active);
    }
    private static void digits(DrawContext c,String value,float x,float y,float scale,int active){
        c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(scale,scale,1);
        for(int i=0;i<value.length();i++)for(int s=0;s<7;s++){int[] r=SevenSegment.RECTANGLES[s];int tint=SevenSegment.active(value.charAt(i),s)?active:active&0xffffff|Math.round(255*.08f)<<24;c.fill(i*48+r[0],r[1],i*48+r[0]+r[2],r[1]+r[3],tint);}c.getMatrices().pop();
    }
    private void texture(DrawContext c,String path,double x,double y,int w,int h,int tw,int th,float alpha){
        Identifier id=Identifier.of("piri","textures/"+path);client.getTextureManager().bindTexture(id);client.getTextureManager().getTexture(id).setFilter(true,false);
        c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(w/(float)tw,h/(float)th,1);c.setShaderColor(1,1,1,alpha);c.drawTexture(id,0,0,0,0,tw,th,tw,th);c.setShaderColor(1,1,1,1);c.getMatrices().pop();
    }
    private static void line(DrawContext c,float x1,float y1,float x2,float y2,float width,int tint){
        float dx=x2-x1,dy=y2-y1,len=(float)Math.sqrt(dx*dx+dy*dy);if(len==0){c.fill((int)x1-1,(int)y1-1,(int)x1+2,(int)y1+2,tint);return;}float nx=-dy/len*width/2,ny=dx/len*width/2;
        c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorProgram);var matrix=c.getMatrices().peek().getPositionMatrix();
        var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP,VertexFormats.POSITION_COLOR);b.vertex(matrix,x1+nx,y1+ny,0).color(tint);b.vertex(matrix,x1-nx,y1-ny,0).color(tint);b.vertex(matrix,x2+nx,y2+ny,0).color(tint);b.vertex(matrix,x2-nx,y2-ny,0).color(tint);BufferRenderer.drawWithGlobalProgram(b.end());RenderSystem.enableCull();RenderSystem.disableBlend();
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
