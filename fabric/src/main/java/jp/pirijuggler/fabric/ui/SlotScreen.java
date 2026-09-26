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
    private final SlotViewState view;private final SlotInput input;private String pressed="";private long leverAt=Long.MIN_VALUE;private int historyOffset=0;
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
    @Override public boolean mouseScrolled(double x,double y,double horizontalAmount,double verticalAmount){
        var v=SlotLayout.Viewport.fit(width,height);
        double lx=v.logicalX(x),ly=v.logicalY(y);
        if(lx<34||lx>=278||ly<455||ly>=746)return false;
        JsonObject data=view.dataLampView();
        JsonArray history=data!=null&&data.has("history")?data.getAsJsonArray("history"):new JsonArray();
        int maxOffset=Math.max(0,history.size()-10);
        if(verticalAmount<0)historyOffset=Math.min(maxOffset,historyOffset+10);
        else if(verticalAmount>0)historyOffset=Math.max(0,historyOffset-10);
        return verticalAmount!=0;
    }
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta){}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        c.fill(0,0,width,height,color("SCREEN_OUTSIDE"));var v=SlotLayout.Viewport.fit(width,height);
        long godMs=view.godFreezeElapsedMillis();
        c.getMatrices().push();c.getMatrices().translate(v.x(),v.y(),0);c.getMatrices().scale((float)v.scale(),(float)v.scale(),1);

        if(isJugglerGod(view.machineType()))godCabinetPanel(c,SlotLayout.CABINET);
        else panel(c,SlotLayout.CABINET,color("CABINET_BG"));
        panel(c,SlotLayout.DATA,color("DISPLAY_BG"));
        panel(c,SlotLayout.DATA_LEFT,color("DISPLAY_BG"));
        panel(c,SlotLayout.DATA_RIGHT,color("DISPLAY_BG"));
        data(c,v.logicalX(mouseX),v.logicalY(mouseY));
        c.fill(670,300,1570,690,view.godFreeze()&&godMs>=35?0xff111111:color("REEL_SEPARATOR"));
        for(int reel=0;reel<3;reel++){
            int x=670+315*reel;
            var clip=v.clip(new SlotLayout.Rect(x,300,270,390));c.enableScissor(clip.x(),clip.y(),clip.x()+clip.w(),clip.y()+clip.h());
            if(view.godFreeze()&&godMs>=35)drawGodReelWindow(c,reel,x,godMs);
            else{
                int reelBg=isJugglerGod(view.machineType())?color("JUGGLER_GOD_REEL_BG"):color("REEL_BG");
                c.fill(x,300,x+270,690,reelBg);
                drawReelSymbols(c,reel,x);
            }
            c.disableScissor();
        }
        var lamp=SlotLayout.LAMP;String name="lamp/piri_chance_"+(view.lampOn()?"on":"off")+".png";
        boolean godMachine=isJugglerGod(view.machineType());
        if(view.lampOn())for(int[] offset:new int[][]{{-4,0},{4,0},{0,4}}){
            if(godMachine)textureFitActual(c,name,lamp.x()+offset[0],lamp.y()+offset[1],lamp.w(),lamp.h(),.18f,1);
            else texture(c,name,lamp.x()+offset[0],lamp.y()+offset[1],lamp.w(),lamp.h(),512,256,.18f);
        }
        if(godMachine)textureFitActual(c,name,lamp.x(),lamp.y(),lamp.w(),lamp.h(),1,1);
        else texture(c,name,lamp.x(),lamp.y(),lamp.w(),lamp.h(),512,256,1);
        if(isJugglerGod(view.machineType())){
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
        c.getMatrices().pop();
    }
    private void drawReelSymbols(DrawContext c,int reel,int x){
        double phase=view.phase(reel);int middle=(int)Math.floor(phase);double fraction=phase-middle;
        for(int row=-2;row<=2;row++){
            String symbol=UiConstants.symbol(reel,middle+row);
            boolean wide=symbol.equals("seven")||symbol.equals("bar");
            int textureWidth=wide?320:256,textureHeight=256;
            int ordinarySymbolWidth=wide?230:130;
            int ordinarySymbolHeight=symbol.equals("bar")?150:130;
            boolean godLarge=symbol.equals("seven")||symbol.equals("grape")||symbol.equals("replay");
            int godSymbolWidth=symbol.equals("bar")?230:godLarge?230:130;
            int godSymbolHeight=symbol.equals("bar")?150:godLarge?150:130;
            int symbolWidth=isJugglerGod(view.machineType())?godSymbolWidth:ordinarySymbolWidth;
            int symbolHeight=isJugglerGod(view.machineType())?godSymbolHeight:ordinarySymbolHeight;
            double symbolY=430+(row-fraction)*130-(symbolHeight-130)/2.0;
            if(isJugglerGod(view.machineType()))
                textureFitActual(c,"symbols/"+symbol+".png",x+(270-symbolWidth)/2.0,symbolY,symbolWidth,symbolHeight,1,1);
            else
                texture(c,"symbols/"+symbol+".png",x+(270-symbolWidth)/2.0,symbolY,symbolWidth,symbolHeight,textureWidth,textureHeight,1);
        }
    }

    private void drawGodReelWindow(DrawContext c,int reel,int x,long ms){
        c.fill(x,300,x+270,690,0xff141414);

        // Keep the freeze visible: during the entry, render ONLY the collapsing reel image.
        // Do not leave a full-size dim reel behind it, otherwise the collapse visually disappears.
        if(ms<120){
            float p=Math.min(1f,(ms-35)/85.0f);
            float scaleY=Math.max(.035f,1f-p*p);
            c.getMatrices().push();
            c.getMatrices().translate(0,495,0);
            c.getMatrices().scale(1,scaleY,1);
            c.getMatrices().translate(0,-495,0);
            drawReelSymbolsDark(c,reel,x,.34f);
            c.getMatrices().pop();
            return;
        }

        if(ms<165){
            float p=(ms-120)/45.0f;
            int half=(int)(135*(1.0f-p));
            int thickness=Math.max(1,(int)(5*(1.0f-p)));
            c.fill(x+135-half,495-thickness,x+135+half,495+thickness,0xffd8d8d8);
            return;
        }

        // After the freeze has completed, the reel remains present but unlit.
        drawReelSymbolsDark(c,reel,x,.10f);

        // BAR appears only after that reel has visually completed its authoritative stop.
        if(view.godRevealed(reel)){
            long age=view.godRevealAgeMillis(reel);
            if(age>=0&&age<120){
                float glow=(float)Math.max(0,.24*(1.0-age/120.0));
                textureFitActual(c,"symbols/bar.png",x+14,414,242,162,glow,1);
                textureFitActual(c,"symbols/bar.png",x+18,418,234,154,glow,1);
            }
            textureFitActual(c,"symbols/bar.png",x+20,420,230,150,1,1);
        }
    }

    private void drawReelSymbolsDark(DrawContext c,int reel,int x,float brightness){
        double phase=view.phase(reel);int middle=(int)Math.floor(phase);double fraction=phase-middle;
        for(int row=-2;row<=2;row++){
            String symbol=UiConstants.symbol(reel,middle+row);
            boolean wide=symbol.equals("seven")||symbol.equals("bar");
            int textureWidth=wide?320:256,textureHeight=256;
            boolean godLarge=symbol.equals("seven")||symbol.equals("grape")||symbol.equals("replay");
            int symbolWidth=symbol.equals("bar")?230:godLarge?230:130;
            int symbolHeight=symbol.equals("bar")?150:godLarge?150:130;
            double symbolY=430+(row-fraction)*130-(symbolHeight-130)/2.0;
            textureFitActual(c,"symbols/"+symbol+".png",x+(270-symbolWidth)/2.0,symbolY,symbolWidth,symbolHeight,1,brightness);
        }
    }

    private static boolean isJugglerGod(String type){return "JUGGLER_GOD".equals(type)||"JUGGLER_GOD_EXTREME".equals(type);}

    private String errorText(){
        if(view.error().isEmpty())return "";
        try{
            ErrorCode code=ErrorCode.valueOf(view.error());
            String machine="台"+view.machineId();
            return switch(code){
                case MACHINE_OCCUPIED -> machine+"はほかのプレイヤーが遊技中です。空くまでお待ちください。";
                case SESSION_MISMATCH -> machine+"との接続状態が変わりました。画面を閉じて、この台をもう一度開いてください。";
                default -> ErrorMessages.japanese(code);
            };
        }catch(IllegalArgumentException e){return view.error();}
    }
    private void data(DrawContext c,double mx,double my){
        JsonObject data=view.dataLampView();
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
        JsonArray history=data!=null&&data.has("history")?data.getAsJsonArray("history"):new JsonArray();
        int maxHistoryOffset=Math.max(0,history.size()-10);
        historyOffset=Math.max(0,Math.min(historyOffset,maxHistoryOffset));
        int shownFrom=history.isEmpty()?0:historyOffset+1;
        int shownTo=Math.min(history.size(),historyOffset+10);
        text(c,historyOffset==0?"NEWEST":"OLDER",42,492,1.05f,false,color("DISPLAY_WHITE"));
        if(!history.isEmpty())text(c,shownFrom+"-"+shownTo+" / "+history.size(),252,492,.90f,true,color("DISPLAY_WHITE"));
        String hoverTime=null;
        for(int row=0;row<10&&historyOffset+row<history.size();row++){
            JsonObject item=history.get(historyOffset+row).getAsJsonObject();String type=item.get("type").getAsString();int y=521+row*22;
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
        textureTint(c,path,x,y,w,h,tw,th,alpha,1);
    }
    private void textureFitActual(DrawContext c,String path,double x,double y,int boxW,int boxH,float alpha,float brightness){
        var size=JugglerGodAssets.textureSize(view.machineType(),path);
        double scale=Math.min(boxW/(double)size.width(),boxH/(double)size.height());
        double w=size.width()*scale,h=size.height()*scale;
        double dx=x+(boxW-w)/2.0,dy=y+(boxH-h)/2.0;
        textureTintExact(c,path,dx,dy,w,h,size.width(),size.height(),alpha,brightness);
    }
    private void textureTintExact(DrawContext c,String path,double x,double y,double w,double h,int tw,int th,float alpha,float brightness){
        Identifier id=JugglerGodAssets.texture(view.machineType(),path);client.getTextureManager().bindTexture(id);client.getTextureManager().getTexture(id).setFilter(true,false);
        c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale((float)(w/tw),(float)(h/th),1);c.setShaderColor(brightness,brightness,brightness,alpha);c.drawTexture(id,0,0,0,0,tw,th,tw,th);c.setShaderColor(1,1,1,1);c.getMatrices().pop();
    }
    private void textureTint(DrawContext c,String path,double x,double y,int w,int h,int tw,int th,float alpha,float brightness){
        Identifier id=JugglerGodAssets.texture(view.machineType(),path);client.getTextureManager().bindTexture(id);client.getTextureManager().getTexture(id).setFilter(true,false);
        c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(w/(float)tw,h/(float)th,1);c.setShaderColor(brightness,brightness,brightness,alpha);c.drawTexture(id,0,0,0,0,tw,th,tw,th);c.setShaderColor(1,1,1,1);c.getMatrices().pop();
    }
    private static void line(DrawContext c,float x1,float y1,float x2,float y2,float width,int tint){
        float dx=x2-x1,dy=y2-y1,len=(float)Math.sqrt(dx*dx+dy*dy);if(len==0){c.fill((int)x1-1,(int)y1-1,(int)x1+2,(int)y1+2,tint);return;}float nx=-dy/len*width/2,ny=dx/len*width/2;
        c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorProgram);var matrix=c.getMatrices().peek().getPositionMatrix();
        var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP,VertexFormats.POSITION_COLOR);b.vertex(matrix,x1+nx,y1+ny,0).color(tint);b.vertex(matrix,x1-nx,y1-ny,0).color(tint);b.vertex(matrix,x2+nx,y2+ny,0).color(tint);b.vertex(matrix,x2-nx,y2-ny,0).color(tint);BufferRenderer.drawWithGlobalProgram(b.end());RenderSystem.enableCull();RenderSystem.disableBlend();
    }
    private static void panel(DrawContext c,SlotLayout.Rect r,int fill){rounded(c,r.x(),r.y(),r.w(),r.h(),18,color("CABINET_EDGE"));rounded(c,r.x()+4,r.y()+4,r.w()-8,r.h()-8,12,fill);}
    private static void godCabinetPanel(DrawContext c,SlotLayout.Rect r){
        rounded(c,r.x(),r.y(),r.w(),r.h(),18,color("CABINET_EDGE_LIGHT"));
        roundedVerticalGradient(c,r.x()+4,r.y()+4,r.w()-8,r.h()-8,12,
                color("JUGGLER_GOD_GOLD_TOP"),color("JUGGLER_GOD_GOLD_BOTTOM"));
        // Thin soft highlight bands break the flat-fill look and give the face a metallic sheen.
        c.fill(r.x()+18,r.y()+24,r.x()+r.w()-18,r.y()+34,color("JUGGLER_GOD_GOLD_HIGHLIGHT"));
        c.fill(r.x()+18,r.y()+38,r.x()+r.w()-18,r.y()+42,color("JUGGLER_GOD_GOLD_SHINE"));
        c.fill(r.x()+18,r.y()+r.h()-34,r.x()+r.w()-18,r.y()+r.h()-24,color("JUGGLER_GOD_GOLD_SHADOW"));
    }
    private static void roundedVerticalGradient(DrawContext c,float x,float y,float w,float h,float radius,int top,int bottom){
        c.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var b=Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN,VertexFormats.POSITION_COLOR);var matrix=c.getMatrices().peek().getPositionMatrix();
        b.vertex(matrix,x+w/2,y+h/2,0).color(mixColor(top,bottom,.5f));
        for(int corner=0;corner<4;corner++)for(int step=0;step<=16;step++){
            double angle=Math.toRadians(corner*90+step*90/16.0);float cx=corner==0||corner==3?x+w-radius:x+radius,cy=corner<2?y+h-radius:y+radius;
            float py=cy+(float)Math.sin(angle)*radius;
            float t=Math.max(0,Math.min(1,(py-y)/h));
            b.vertex(matrix,cx+(float)Math.cos(angle)*radius,py,0).color(mixColor(top,bottom,t));
        }
        b.vertex(matrix,x+w,y+h-radius,0).color(mixColor(top,bottom,Math.max(0,Math.min(1,(h-radius)/h))));
        BufferRenderer.drawWithGlobalProgram(b.end());RenderSystem.enableCull();RenderSystem.disableBlend();
    }
    private static int mixColor(int a,int b,float t){
        int aa=a>>>24&255,ar=a>>>16&255,ag=a>>>8&255,ab=a&255;
        int ba=b>>>24&255,br=b>>>16&255,bg=b>>>8&255,bb=b&255;
        return ((int)(aa+(ba-aa)*t)<<24)|((int)(ar+(br-ar)*t)<<16)|((int)(ag+(bg-ag)*t)<<8)|(int)(ab+(bb-ab)*t);
    }
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