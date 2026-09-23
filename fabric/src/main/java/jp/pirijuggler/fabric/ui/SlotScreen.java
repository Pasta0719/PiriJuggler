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
    public SlotScreen(SlotViewState view,SlotInput input){super(Text.literal("Piri Slot"));this.view=view;this.input=input;}
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
        long godMs=view.godFreezeElapsedMillis();
        c.getMatrices().push();c.getMatrices().translate(v.x(),v.y(),0);c.getMatrices().scale((float)v.scale(),(float)v.scale(),1);

        panel(c,SlotLayout.CABINET,color("CABINET_BG"));
        panel(c,SlotLayout.DATA,color("DISPLAY_BG"));
        panel(c,SlotLayout.DATA_LEFT,color("DISPLAY_BG"));
        panel(c,SlotLayout.DATA_RIGHT,color("DISPLAY_BG"));
        data(c,v.logicalX(mouseX),v.logicalY(mouseY));
        c.fill(670,300,1570,690,color("REEL_SEPARATOR"));
        for(int reel=0;reel<3;reel++){
            int x=670+315*reel;
            c.fill(x,300,x+270,690,color("REEL_BG"));
            var clip=v.clip(new SlotLayout.Rect(x,300,270,390));c.enableScissor(clip.x(),clip.y(),clip.x()+clip.w(),clip.y()+clip.h());
            double phase=view.phase(reel);int middle=(int)Math.floor(phase);double fraction=phase-middle;
            for(int row=-2;row<=2;row++){
                String symbol=UiConstants.symbol(reel,middle+row);
                boolean wide=symbol.equals("seven")||symbol.equals("bar");
                int textureWidth=wide?320:256,textureHeight=256;
                int symbolWidth=wide?230:130;
                int symbolHeight=symbol.equals("bar")?150:130;
                double symbolY=430+(row-fraction)*130-(symbolHeight-130)/2.0;
                texture(c,"symbols/"+symbol+".png",x+(270-symbolWidth)/2.0,symbolY,symbolWidth,symbolHeight,textureWidth,textureHeight,1);
            }
            c.disableScissor();
            if(view.godFreeze()&&view.godRevealed(reel)){
                long age=view.godRevealAgeMillis(reel);
                if(age>=0&&age<90)c.fill(x,300,x+270,690,0x2affffff);
            }
        }
        var lamp=SlotLayout.LAMP;String name="lamp/piri_chance_"+(view.lampOn()?"on":"off")+".png";
        if(view.lampOn())for(int[] offset:new int[][]{{-4,0},{4,0},{0,4}})texture(c,name,lamp.x()+offset[0],lamp.y()+offset[1],lamp.w(),lamp.h(),512,256,.18f);
        texture(c,name,lamp.x(),lamp.y(),lamp.w(),lamp.h(),512,256,1);
        if("JUGGLER_GOD".equals(view.machineType())){
            int stockOn=view.stockLampOn()?color("DISPLAY_GREEN"):color("BUTTON_METAL_DARK");
            rounded(c,1390,835,150,58,14,stockOn);
            rounded(c,1396,841,138,46,10,color("DISPLAY_BG"));
            text(c,"STOCK",1465,853,1.8f,true,view.stockLampOn()?color("DISPLAY_GREEN"):color("TEXT_SHADOW"));
        }
        panel(c,SlotLayout.STATUS,color("DISPLAY_BG"));
        String[] labels={"CREDIT","BET","PAY","MEDALS"},fields={"credit","bet","pay","heldMedals"};
        for(int n=0;n<4;n++){int x=690+n*198;text(c,labels[n],x,725,2,false);text(c,view.value(fields[n]),x,758,3,false);}
        String state=view.value("gameState");if(state.equals("REPLAY_READY"))text(c,"REPLAY",1050,816,2,true);
        if(state.startsWith("BIG_")||state.startsWith("REG_"))text(c,"COUNT "+view.value("bonusCount"),1090,816,2,true);
        for(var control:SlotLayout.CONTROLS)drawControl(c,control,v.logicalX(mouseX),v.logicalY(mouseY));
        String message=input.closing()?"離席処理中…":errorText();if(!message.isEmpty())text(c,message,1040,990,2,true);
        if(view.godFreeze())drawGodBlackout(c,godMs);
        c.getMatrices().pop();
    }
    private static void drawGodBlackout(DrawContext c,long ms){
        if(ms<35)return;

        // The GOD blackout is an event, not a black rectangle or a fade:
        // the picture tears into horizontal bands, collapses toward the centre,
        // leaves a short bright scan-line, then the signal is completely gone.
        if(ms<70){
            int p=(int)(ms-35);
            for(int y=0;y<1080;y+=54){
                int band=Math.min(1920,p*(32+(y/54%4)*7));
                if(((y/54)&1)==0)c.fill(0,y,band,Math.min(1080,y+30),0xff000000);
                else c.fill(1920-band,y,1920,Math.min(1080,y+30),0xff000000);
            }
            return;
        }

        if(ms<120){
            float p=(ms-70)/50.0f;
            p=p*p;
            int closed=Math.min(540,(int)(540*p));
            c.fill(0,0,1920,closed,0xff000000);
            c.fill(0,1080-closed,1920,1080,0xff000000);

            int centreTop=closed,centreBottom=1080-closed;
            for(int y=centreTop;y<centreBottom;y+=34){
                if(((y/34)&1)==0)c.fill(0,y,1920,Math.min(centreBottom,y+5),0x9a000000);
            }
            return;
        }

        if(ms<165){
            c.fill(0,0,1920,1080,0xff000000);
            float p=(ms-120)/45.0f;
            int half=(int)(960*(1.0f-p));
            int thickness=Math.max(2,(int)(10*(1.0f-p)));
            c.fill(960-half,540-thickness,960+half,540+thickness,0xffffffff);
            return;
        }

        if(ms<900){
            c.fill(0,0,1920,1080,0xff000000);
            return;
        }

        // A very short signal-kick before the picture is handed back to the normal renderer.
        if(ms<940){
            c.fill(0,0,1920,1080,0xff000000);
            int half=(int)(960*((ms-900)/40.0f));
            c.fill(960-half,538,960+half,542,0xd8ffffff);
        }
    }

    private String errorText(){if(view.error().isEmpty())return "";try{return ErrorMessages.japanese(ErrorCode.valueOf(view.error()));}catch(IllegalArgumentException e){return view.error();}}
    private void data(DrawContext c,double mx,double my){
        JsonObject data=view.dataLamp();
        long total=data!=null&&data.has("totalGames")?data.get("totalGames").getAsLong():0;
        long big=data!=null&&data.has("bigCount")?data.get("bigCount").getAsLong():0;
        long reg=data!=null&&data.has("regCount")?data.get("regCount").getAsLong():0;
        long current=data!=null&&data.has("currentGames")?data.get("currentGames").getAsLong():0;
        long diff=data!=null&&data.has("todayDifference")?data.get("todayDifference").getAsLong():0;
        long maxDiff=data!=null&&data.has("todayMaxDifference")?data.get("todayMaxDifference").getAsLong():0;

        text(c,"MACHINE "+view.machineId(),38,27,1.35f,false,color("DISPLAY_WHITE"));

        text(c,"DIFF GRAPH",55,55,1.75f,false,color("DISPLAY_WHITE"));
        JsonArray graph=data!=null&&data.has("graph")?data.getAsJsonArray("graph"):new JsonArray();
        drawGraph(c,graph,55,88,825,146,total);
        text(c,"1G",55,241,1.10f,false,color("DISPLAY_WHITE"));
        text(c,total>0?total+"G":"-",880,241,1.10f,true,color("DISPLAY_WHITE"));

        drawMetric(c,"CURRENT G",Long.toString(current),1060,43,205,color("DISPLAY_WHITE"));
        drawMetric(c,"TOTAL G",Long.toString(total),1370,43,205,color("DISPLAY_WHITE"));
        drawMetric(c,"MAX DIFF",signed(maxDiff),1680,43,205,color("DISPLAY_WHITE"));
        drawMetric(c,"BIG",Long.toString(big),1210,145,205,color("DISPLAY_BIG"));
        drawMetric(c,"REG",Long.toString(reg),1540,145,205,color("DISPLAY_REG"));

        text(c,"DIFF",42,345,1.65f,false,color("DISPLAY_WHITE"));
        digitsFitCentered(c,signed(diff),156,382,188,.78f,diff>=0?color("DISPLAY_GREEN"):color("DISPLAY_WHITE"));
        c.fill(38,448,274,451,color("BUTTON_METAL_DARK"));
        text(c,"BONUS HISTORY",42,465,1.55f,false,color("DISPLAY_WHITE"));
        text(c,"NEWEST",42,492,1.05f,false,color("DISPLAY_WHITE"));
        JsonArray history=data!=null&&data.has("history")?data.getAsJsonArray("history"):new JsonArray();
        String hoverTime=null;
        for(int i=0;i<Math.min(10,history.size());i++){
            JsonObject item=history.get(i).getAsJsonObject();String type=item.get("type").getAsString();int y=521+i*22;
            int tint="GOD".equals(type)?color("DISPLAY_GREEN"):"BIG".equals(type)?color("DISPLAY_BIG"):color("DISPLAY_REG");
            text(c,type,44,y,1.22f,false,tint);
            text(c,item.get("games").getAsString()+"G",139,y,1.22f,false,color("DISPLAY_WHITE"));
            if(mx>=34&&mx<278&&my>=y-3&&my<y+19&&item.has("occurredAt"))hoverTime=HISTORY_TIME.format(Instant.ofEpochMilli(item.get("occurredAt").getAsLong()));
        }
        if(hoverTime!=null)text(c,hoverTime,156,746,.90f,true,color("DISPLAY_WHITE"));

        text(c,"ODDS",1672,345,1.65f,false,color("DISPLAY_WHITE"));
        drawProbability(c,"BIG ODDS",probability(total,big),1672,385,color("DISPLAY_BIG"));
        drawProbability(c,"REG ODDS",probability(total,reg),1672,455,color("DISPLAY_REG"));
        drawProbability(c,"COMBINED",probability(total,big+reg),1672,525,color("DISPLAY_WHITE"));
        c.fill(1666,590,1882,593,color("BUTTON_METAL_DARK"));
        if(data!=null&&data.has("piriChain")&&data.get("piriChain").getAsBoolean()){
            int chain=data.has("piriChainCount")?data.get("piriChainCount").getAsInt():1;
            rounded(c,1664,615,220,102,12,color("DISPLAY_GREEN"));
            rounded(c,1669,620,210,92,9,color("DISPLAY_BG"));
            text(c,"PIRI CHAIN",1774,633,1.45f,true,color("DISPLAY_WHITE"));
            text(c,"CHAIN x"+chain,1774,670,1.80f,true,color("DISPLAY_GREEN"));
        }
    }
    private void drawMetric(DrawContext c,String label,String value,float center,float y,float maxWidth,int tint){
        text(c,label,center,y,1.55f,true,tint);
        digitsFitCentered(c,value,center,y+31,maxWidth,.72f,tint);
    }
    private void drawProbability(DrawContext c,String label,String value,float x,float y,int tint){
        text(c,label,x,y,1.30f,false,tint);
        text(c,value,1776,y+25,1.60f,true,tint);
    }
    private static String signed(long value){return value>0?"+"+value:Long.toString(value);}
    private static String probability(long games,long hits){
        if(games<=0||hits<=0)return "---";
        return "1/"+Math.max(1,Math.round(games/(double)hits));
    }
    private void drawGraph(DrawContext c,JsonArray graph,float x,float y,float w,float h,long totalGames){
        if(graph.isEmpty()||totalGames<1)return;
        long min=Long.MAX_VALUE,max=Long.MIN_VALUE;
        for(JsonElement element:graph){JsonObject p=element.getAsJsonObject();long px=p.get("game").getAsLong();if(px<1||px>totalGames)continue;long py=p.get("difference").getAsLong();min=Math.min(min,py);max=Math.max(max,py);}
        if(min==Long.MAX_VALUE)return;
        double low,high;if(min==max){low=min-200.0;high=max+200.0;}else if(max-min<400){double mid=(min+max)/2.0;low=mid-200;high=mid+200;}else{double pad=(max-min)*.05;low=min-pad;high=max+pad;}
        if(low<=0&&high>=0){float zy=(float)(y+h-(0-low)/(high-low)*h);c.fill((int)x,(int)zy,(int)(x+w),(int)Math.ceil(zy+1),color("GRAPH_ZERO"));}
        float lastX=0,lastY=0;boolean first=true;
        for(JsonElement element:graph){JsonObject p=element.getAsJsonObject();long px=p.get("game").getAsLong();if(px<1||px>totalGames)continue;long py=p.get("difference").getAsLong();
            float sx=totalGames<=1?x+w:(float)(x+(px-1)/(double)(totalGames-1)*w);float sy=(float)(y+h-(py-low)/(high-low)*h);
            if(first){c.fill((int)sx-2,(int)sy-2,(int)sx+3,(int)sy+3,color("GRAPH_LINE"));first=false;}else line(c,lastX,lastY,sx,sy,2.0f,color("GRAPH_LINE"));lastX=sx;lastY=sy;}
    }
    private void drawControl(DrawContext c,SlotLayout.Control control,double mx,double my){
        var r=control.rect();boolean hover=r.contains(mx,my),down=pressed.equals(control.name());
        int fill=color(down?"BUTTON_RED_PRESSED":hover?"BUTTON_RED_HOVER":"BUTTON_RED");
        if(control.name().equals("LEVER")){
            long elapsed=System.nanoTime()-leverAt;double e=leverAt==Long.MIN_VALUE?1:elapsed/1e6;
            double offset=e>=180?0:e<=90?18*e/90:18*(180-e)/90;
            c.fill(r.x()+(r.w()-22)/2,r.y()+35,r.x()+(r.w()+22)/2,r.y()+r.h()-35,color("BUTTON_METAL_DARK"));
            rounded(c,r.x()+(r.w()-70)/2f,(float)(r.y()+27+offset),70,70,35,down?fill:hover?UiConstants.brighter(color("BUTTON_RED")):color("BUTTON_RED"));
            text(c,"LEVER",r.x()+r.w()/2,r.y()+r.h()-25,2,true);
        }else if(control.name().equals("LEFT")||control.name().equals("CENTER")||control.name().equals("RIGHT")){
            float x=r.x()+(r.w()-88)/2f,y=r.y()+(r.h()-88)/2f;
            boolean freezeLocked=view.godFreezeInputLocked();
            rounded(c,x,y,88,88,44,freezeLocked?color("BUTTON_METAL_DARK"):hover?UiConstants.brighter(color("BUTTON_METAL")):color("BUTTON_METAL"));
            rounded(c,x+6,y+6,76,76,38,freezeLocked?color("BUTTON_METAL_DARK"):fill);text(c,control.name(),r.x()+r.w()/2,r.y()+r.h()+12,2,true);
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
        for(int i=0;i<value.length();i++)for(int s=0;s<7;s++)if(SevenSegment.active(value.charAt(i),s)){int[] r=SevenSegment.RECTANGLES[s];c.fill(i*48+r[0],r[1],i*48+r[0]+r[2],r[1]+r[3],active);}c.getMatrices().pop();
    }
    private void texture(DrawContext c,String path,double x,double y,int w,int h,int tw,int th,float alpha){
        Identifier id=JugglerGodAssets.texture(view.machineType(),path);client.getTextureManager().bindTexture(id);client.getTextureManager().getTexture(id).setFilter(true,false);
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