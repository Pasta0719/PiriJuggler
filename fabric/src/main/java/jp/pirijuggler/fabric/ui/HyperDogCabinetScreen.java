package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.fabric.PiriJugglerClient;
import jp.pirijuggler.fabric.network.PiriPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/**
 * Production Phase05 full HYPER_DOG cabinet. The Phase02 preview remains a
 * compatibility base for real Paper/Fabric acceptance input probes; only this
 * subclass is routed for newly opened HYPER_DOG sessions.
 *
 * Layout, physical hitboxes, loans/medal controls and datalamp follow the
 * existing 1920x1080 SlotLayout. Lottery/stocks never run in Fabric.
 */
public final class HyperDogCabinetScreen extends HyperDogReelPreviewScreen {
    private static final int OUTSIDE=0xff12121a,EDGE=0xff817a95,CABINET=0xff302f41;
    private static final int REEL_BG=0xfff4f1e8,PANEL=0xff101824,OFF=0xff25242c;
    private static final int GOLD=0xffd9b65c,PURPLE=0xffb78cff,CYAN=0xff5ad8f0;
    private static final int WHITE=0xffeaeffa,STEEL=0xff404454,RED=0xffb8495a;
    private static final int NORMAL_SYMBOL_SIZE=124;
    private static final int SPECIAL_SYMBOL_SIZE=166; // Like other cabinets, 7/BAR are bigger than ordinary symbols.
    static int reelBackgroundColor(){return REEL_BG;}
    static int symbolRenderSize(String path){
        return path.endsWith("/red7.png")||path.endsWith("/rainbow7.png")||path.endsWith("/bar.png")
                ? SPECIAL_SYMBOL_SIZE : NORMAL_SYMBOL_SIZE;
    }
    private static final String[] COUNTERS={"CREDIT","BET","PAY","MEDALS"};
    private static final String[] COUNTER_KEYS={"credit","bet","pay","heldMedals"};
    private static final String[] COLORS={"WHITE","BLUE","GREEN","YELLOW","PURPLE","RED","RAINBOW"};
    private static final int[] COLORS_ARGB={WHITE,0xff548efa,0xff45d48c,0xffefd757,PURPLE,0xfff14954,0xffe0afff};
    private final HyperDogClientReels reels;
    private final HyperDogCabinetState hud;
    private final Set<Integer> pressedKeys=new HashSet<>();
    private String pressedControl="";
    private boolean pressedMouse;
    private int historyOffset;
    private long leverPressedMs;

    public HyperDogCabinetScreen(HyperDogClientReels reels,HyperDogCabinetState hud) {
        super(reels);
        this.reels=Objects.requireNonNull(reels);
        this.hud=Objects.requireNonNull(hud);
    }
    public HyperDogCabinetState cabinetState(){return hud;}
    @Override protected void init(){client.mouse.unlockCursor();}
    @Override public boolean shouldPause(){return false;}
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override public boolean requestPlay(){
        if(hud.displayLocked()||hud.closeRequested())return false;
        return super.requestPlay();
    }
    @Override public boolean requestStop(PacketType type){
        if(hud.closeRequested()||hud.displayLocked())return false;
        return super.requestStop(type);
    }
    public boolean requestControl(String name){
        for(var control:SlotLayout.CONTROLS)if(control.name().equals(name)){
            if(name.equals("BET")||name.equals("LEVER"))return requestPlay();
            return switch(control.action()){
                case STOP_LEFT,STOP_CENTER,STOP_RIGHT -> requestStop(control.action());
                default -> sendAction(control.action());
            };
        }
        return false;
    }
    private boolean sendAction(PacketType action){
        if(hud.closeRequested())return false;
        if(action!=PacketType.CLOSE_REQUEST && hud.displayLocked())return false;
        var session=PiriJugglerClient.session();
        if(session.sessionId()==null||session.machineId()!=reels.machineId()||
                !Objects.equals(session.sessionId(),hud.sessionId()))return false;
        com.google.gson.JsonObject j=new com.google.gson.JsonObject();
        j.addProperty("sessionId",session.sessionId().toString());
        j.addProperty("machineId",session.machineId());
        j.addProperty("clientSequence",session.takeSequence());
        ClientPlayNetworking.send(new PiriPayload(EnvelopeCodec.encode(Envelope.current(action,j))));
        if(action==PacketType.CLOSE_REQUEST)hud.markCloseRequested();
        return true;
    }
    @Override public void close(){sendAction(PacketType.CLOSE_REQUEST);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_T){
            client.setScreen(new SlotChatScreen(this));return true;
        }
        PacketType action=SlotKeys.action(key,scan);
        if(action==null)return super.keyPressed(key,scan,modifiers);
        if(!pressedKeys.add(key))return true;
        if(action==PacketType.CLOSE_REQUEST){close();return true;}
        if(action==PacketType.SPACE_ACTION){
            if(reels.spinning()&&!reels.complete())requestStop(action);
            else requestPlay();
        }else if(action==PacketType.STOP_LEFT||action==PacketType.STOP_CENTER||action==PacketType.STOP_RIGHT)
            requestStop(action);
        else sendAction(action);
        return true;
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers){
        pressedKeys.remove(key);return true;
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return false;
        var v=SlotLayout.Viewport.fit(width,height);
        double x=v.logicalX(mx),y=v.logicalY(my);
        for(var control:SlotLayout.CONTROLS)if(control.rect().contains(x,y)){
            if(requestControl(control.name())){
                pressedControl=control.name();pressedMouse=true;
                if(control.name().equals("LEVER"))leverPressedMs=System.currentTimeMillis();
            }
            return true;
        }
        return false;
    }
    @Override public boolean mouseReleased(double mx,double my,int button){
        if(button!=0)return false;
        pressedControl="";pressedMouse=false;return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        var v=SlotLayout.Viewport.fit(width,height);
        double x=v.logicalX(mx),y=v.logicalY(my);
        if(x<34||x>=278||y<455||y>=746)return false;
        var data=hud.dataLamp();
        var history=data!=null&&data.has("history")&&data.get("history").isJsonArray()
                ?data.getAsJsonArray("history"):new com.google.gson.JsonArray();
        int max=Math.max(0,history.size()-10);
        if(vertical<0)historyOffset=Math.min(max,historyOffset+10);
        else if(vertical>0)historyOffset=Math.max(0,historyOffset-10);
        return vertical!=0;
    }
    @Override public void renderBackground(DrawContext c,int mouseX,int mouseY,float delta){}

    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        c.fill(0,0,width,height,OUTSIDE);
        var vp=SlotLayout.Viewport.fit(width,height);
        c.getMatrices().push();
        c.getMatrices().translate(vp.x(),vp.y(),0);
        c.getMatrices().scale((float)vp.scale(),(float)vp.scale(),1f);
        drawPanel(c,HyperDogCabinetState.cabinetRect(),CABINET,EDGE);
        drawPanel(c,SlotLayout.DATA,PANEL,EDGE);
        drawPanel(c,SlotLayout.DATA_LEFT,PANEL,STEEL);
        drawPanel(c,HyperDogCabinetState.rightDataRect(),PANEL,STEEL);
        drawDataLamp(c);
        drawReels(c,vp);
        drawFlash(c);
        drawAnnouncement(c);
        drawStatusIndicators(c);
        drawAddScreen(c);
        drawCounters(c);
        double lx=vp.logicalX(mouseX),ly=vp.logicalY(mouseY);
        for(var control:SlotLayout.CONTROLS)drawControl(c,control,lx,ly);
        if(hud.closeRequested())fitText(c,"離席処理中…",1040,998,2.2f,780,WHITE,true);
        else if(!hud.error().isEmpty())fitText(c,hud.error(),1040,998,1.8f,820,WHITE,true);
        c.getMatrices().pop();
        countRendered(9);
    }
    private void drawReels(DrawContext c,SlotLayout.Viewport vp){
        c.fill(668,297,1572,693,EDGE);
        for(Reel reel:Reel.values()){
            int col=reel.ordinal();
            int x=670+315*col;
            c.fill(x,300,x+270,690,REEL_BG);
            var clip=vp.clip(new SlotLayout.Rect(x,300,270,390));
            c.enableScissor(clip.x(),clip.y(),clip.x()+clip.w(),clip.y()+clip.h());
            for(int row=-1;row<=1;row++){
                String symbol=reels.spinning()
                        ?reels.texturePath(reel,row)
                        :HyperDogReelImageMapping.texturePath(reel,0,row);
                Identifier id=Identifier.of("piri","textures/"+symbol);
                int size=symbolRenderSize(symbol);
                int drawX=x+(270-size)/2,drawY=300+(row+1)*130+(130-size)/2;
                c.getMatrices().push();
                c.getMatrices().translate(drawX,drawY,0);
                int texturePixels=HyperDogTextureDimensions.dimension(symbol);
                c.getMatrices().scale(size/(float)texturePixels,size/(float)texturePixels,1f);
                c.drawTexture(id,0,0,0,0,texturePixels,texturePixels,texturePixels,texturePixels);
                c.getMatrices().pop();
            }
            c.disableScissor();
            c.drawBorder(x,300,270,390,STEEL);
            if(reels.spinning()&&reels.stopIndex(reel)>=0)
                c.fill(x,681,x+270,689,0xff54c8ad);
        }
    }
    private void drawFlash(DrawContext c){
        String tintName=hud.flashColor();
        int alpha=hud.flashAlphaPercent();
        if(tintName==null||alpha==0)return;
        int opacity=Math.round(alpha*255f/100f);
        if("RAINBOW".equals(tintName)){
            // Moving translucent bands across all three reels; the artwork remains legible.
            int[] spectrum={0xfff14954,0xffffa752,0xffefd757,0xff45d48c,0xff548efa,0xffb78cff,0xfff14954};
            int offset=(int)((hud.flashElapsedMs()/16)%spectrum.length);
            for(int i=0;i<14;i++){
                int x=670+i*900/14, end=670+(i+1)*900/14;
                c.fill(x,300,end,690,(opacity<<24)|(spectrum[(i+offset)%spectrum.length]&0x00ffffff));
            }
        }else{
            int index=Arrays.asList(COLORS).indexOf(tintName);
            if(index>=0)c.fill(670,300,1570,690,(opacity<<24)|(COLORS_ARGB[index]&0x00ffffff));
        }
    }
    private void drawAnnouncement(DrawContext c){
        var lamp=HyperDogLampGeometry.announcement(0);
        var frame=lamp.frame();
        boolean lit=hud.announcement();
        // Beveled eight-sided emblem, not a square light or flower motif.
        drawChanceBand(c,lamp.frame(),14,0xffaaa6b7);
        drawChanceBand(c,lamp.gasket(),11,0xff14131f);
        drawChanceBand(c,lamp.lens(),8,lit?0xfff7ca80:0xff60566f);
        drawChanceBand(c,lamp.emitter(),4,lit?0xffca7936:0xff251f32);
        int edge=lit?0xffffdc99:0xff756c88;
        c.fill(frame.x()+25,frame.y()+10,frame.x()+frame.w()-25,frame.y()+11,edge);
        c.fill(frame.x()+25,frame.y()+frame.h()-11,frame.x()+frame.w()-25,frame.y()+frame.h()-10,edge);
        fitText(c,"CHANCE",frame.x()+frame.w()/2f,frame.y()+16,
                3.25f,frame.w()-42,lit?0xfffff5e5:0xffa9a0ba,true);
    }

    /** Paint a chamfered polygon as a middle fill and short clipped edge rows. */
    private static void drawChanceBand(DrawContext c,SlotLayout.Rect r,int cut,int color){
        int x=r.x(),y=r.y(),w=r.w(),h=r.h();
        int chamfer=Math.min(cut,Math.min(w/2-1,h/2-1));
        c.fill(x,y+chamfer,x+w,y+h-chamfer,color);
        for(int row=0;row<chamfer;row++){
            int inset=chamfer-row;
            c.fill(x+inset,y+row,x+w-inset,y+row+1,color);
            c.fill(x+inset,y+h-row-1,x+w-inset,y+h-row,color);
        }
    }

    private void drawStatusIndicators(DrawContext c){
        var modes=HyperDogCabinetState.Lamp.values();
        int[] colors={GOLD,PURPLE,CYAN};
        String[] labels={"RUSH","HYPER","CZ"};
        for(int i=0;i<3;i++){
            var lamp=HyperDogLampGeometry.left(i);
            boolean lit=hud.active(modes[i]);
            drawLamp(c,lamp,lit,colors[i]);
            var r=lamp.frame();
            fitText(c,labels[i],r.x()+r.w()/2f,r.y()+9,1.45f,r.w()-18,
                    lit?0xfff9f1dc:0xffa29eb1,true);
        }
        int count=hud.rightCount();
        int tint=OFF;
        String color=hud.color();
        if(color!=null){
            int i=Arrays.asList(COLORS).indexOf(color);
            if(i>=0)tint=COLORS_ARGB[i];
        }
        for(int i=0;i<3;i++)
            drawLamp(c,HyperDogLampGeometry.right(i),i<count,tint);
    }

    private static void drawLamp(DrawContext c,HyperDogLampGeometry.Layers lamp,boolean lit,int color){
        // Metal outer bezel > black inset > dark lens > smaller illuminated
        // emitter. Neither the inactive nor active state paints to the frame.
        lampLayer(c,lamp.frame(),STEEL);
        lampLayer(c,lamp.gasket(),0xff0b0e18);
        lampLayer(c,lamp.lens(),0xff34303b);
        var e=lamp.emitter();
        lampLayer(c,e,lit?color:0xff29242c);
        if(lit){
            // Small highlight on the glass, fully within the emitter inset.
            int left=e.x()+Math.max(2,e.w()/10);
            int right=e.x()+e.w()-Math.max(2,e.w()/10);
            int top=e.y()+Math.max(1,e.h()/8);
            int bottom=Math.min(e.y()+e.h()-1,top+Math.max(1,e.h()/7));
            if(right>left&&bottom>top)
                c.fill(left,top,right,bottom,0x55ffffff);
        }
    }

    private static void lampLayer(DrawContext c,SlotLayout.Rect r,int color){
        float radius=Math.min(9f,Math.min(r.w(),r.h())/3f);
        SlotScreen.rounded(c,r.x(),r.y(),r.w(),r.h(),radius,color);
    }
    private void drawAddScreen(DrawContext c){
        var r=HyperDogCabinetState.lcdRect();
        drawPanel(c,r,0xff0a0c16,STEEL);
        String value=hud.displayText();
        if(!value.isEmpty()){
            int color=value.startsWith("+")?0xff81f2c3:value.startsWith("TOTAL")?GOLD:WHITE;
            fitText(c,value,r.x()+r.w()/2f,r.y()+47,3.2f,r.w()-22,color,true);
        }
    }
    private void drawCounters(DrawContext c){
        drawPanel(c,SlotLayout.STATUS,PANEL,STEEL);
        for(int i=0;i<4;i++){
            float x=690+i*198;
            fitText(c,COUNTERS[i],x,722,1.65f,182,GOLD,false);
            fitText(c,hud.value(COUNTER_KEYS[i]),x,755,2.85f,182,WHITE,false);
        }
    }
    private void drawDataLamp(DrawContext c){
        var data=hud.dataLamp();
        long total=value(data,"totalGames"),current=value(data,"currentGames");
        long diff=value(data,"todayDifference"),max=value(data,"todayMaxDifference");
        long rush=value(data,"rushCount"),hyper=value(data,"hyperCount");
        fitText(c,"MACHINE "+hud.machineId(),38,28,1.45f,310,WHITE,false);
        fitText(c,"DIFF GRAPH",55,57,1.75f,500,WHITE,false);
        var graph=data!=null&&data.has("graph")?data.getAsJsonArray("graph"):
                new com.google.gson.JsonArray();
        // Reuse the existing slot UI's graph presentation without drawing a second graph.
        SlotScreen.drawGraph(c,graph,55,88,825,146,total);
        fitText(c,"1G",55,241,1.10f,160,WHITE,false);
        fitText(c,total>0?total+"G":"-",880,241,1.10f,160,WHITE,true);
        fitText(c,"CURRENT G",1060,43,1.55f,220,WHITE,true);
        fitText(c,Long.toString(current),1060,76,2.3f,230,WHITE,true);
        fitText(c,"TOTAL G",1370,43,1.55f,220,WHITE,true);
        fitText(c,Long.toString(total),1370,76,2.3f,230,WHITE,true);
        fitText(c,"MAX DIFF",1680,43,1.55f,220,WHITE,true);
        fitText(c,signed(max),1680,76,2.3f,230,WHITE,true);
        // Match the two-counter layout used by existing BIG/REG slot data lamps.
        fitText(c,"RUSH",1210,145,1.55f,205,GOLD,true);
        fitText(c,Long.toString(rush),1210,180,2.35f,205,WHITE,true);
        fitText(c,"HYPER",1540,145,1.55f,205,GOLD,true);
        fitText(c,Long.toString(hyper),1540,180,2.35f,205,WHITE,true);
        fitText(c,"DIFF",42,345,1.7f,240,WHITE,false);
        fitText(c,signed(diff),156,391,2.4f,205,diff>=0?0xff60e6b5:WHITE,true);
        fitText(c,"HISTORY",44,467,1.55f,230,WHITE,false);
        if(data!=null&&data.has("history")&&data.get("history").isJsonArray()){
            var history=data.getAsJsonArray("history");
            historyOffset=Math.max(0,Math.min(historyOffset,Math.max(0,history.size()-10)));
            for(int i=0;i<10&&historyOffset+i<history.size();i++){
                var item=history.get(historyOffset+i).getAsJsonObject();
                String type=item.has("type")?item.get("type").getAsString():"";
                String games=item.has("games")?item.get("games").getAsString()+"G":"";
                String medals=item.has("medals")?signed(item.get("medals").getAsLong()):"";
                String detail=games.isEmpty()?medals:medals.isEmpty()?games:games+" "+medals;
                fitText(c,type,45,523+i*22,1.15f,100,GOLD,false);
                fitText(c,detail,145,523+i*22,1.10f,130,WHITE,false);
            }
        }
        // Keep the established ODDS panel/CHAIN region, without new overlays.
        fitText(c,"ODDS",1730,345,1.5f,162,WHITE,false);
        fitText(c,"RUSH ODDS",1730,383,1.22f,162,GOLD,false);
        fitText(c,odds(total,rush),1730,412,1.70f,162,WHITE,false);
        fitText(c,"HYPER ODDS",1730,458,1.22f,162,GOLD,false);
        fitText(c,odds(total,hyper),1730,487,1.70f,162,WHITE,false);
        fitText(c,"COMBINED",1730,534,1.22f,162,GOLD,false);
        fitText(c,odds(total,Math.addExact(rush,hyper)),1730,563,1.70f,162,WHITE,false);
        if(data!=null&&data.has("shotExists")&&data.get("shotExists").getAsBoolean()){
            boolean complete=data.has("shotFinal")&&data.get("shotFinal").getAsBoolean();
            long amount=value(data,"shotNet");
            // Exactly GET n while active and TOTAL n after the last failed comeback.
            String text=(complete?"TOTAL ":"GET ")+String.format(java.util.Locale.ROOT,"%,d",amount);
            fitText(c,text,1730,655,1.65f,165,complete?GOLD:0xff60e6b5,false);
        }
    }
    private static String odds(long games,long entries){
        return games<=0||entries<=0?"---":"1/"+Math.max(1,Math.round(games/(double)entries));
    }
    private static long value(com.google.gson.JsonObject data,String key){
        return data!=null&&data.has(key)&&!data.get(key).isJsonNull()?data.get(key).getAsLong():0;
    }
    private static String signed(long x){return x>0?"+"+x:Long.toString(x);}
    private void drawControl(DrawContext c,SlotLayout.Control ctrl,double mx,double my){
        var r=ctrl.rect();
        boolean hover=r.contains(mx,my),down=pressedMouse&&ctrl.name().equals(pressedControl);
        int fill=down?0xff7a3040:hover?0xffdc7584:RED;
        if(ctrl.name().equals("LEVER")){
            long elapsed=System.currentTimeMillis()-leverPressedMs;
            double shift=down&&elapsed<180?(elapsed<90?18*elapsed/90.0:18*(180-elapsed)/90.0):0;
            c.fill(r.x()+(r.w()-22)/2,r.y()+35,r.x()+(r.w()+22)/2,r.y()+r.h()-35,STEEL);
            SlotScreen.rounded(c,r.x()+(r.w()-70)/2f,(float)(r.y()+27+shift),70,70,35,fill);
            fitText(c,"LEVER",r.x()+r.w()/2f,r.y()+r.h()-25,2f,r.w(),WHITE,true);
        }else if(ctrl.name().equals("LEFT")||ctrl.name().equals("CENTER")||ctrl.name().equals("RIGHT")){
            // The established JUGGLER cabinet's round metal stop buttons,
            // not HYPER_DOG-specific red rectangles.
            float x=r.x()+(r.w()-88)/2f,y=r.y()+(r.h()-88)/2f;
            SlotScreen.rounded(c,x,y,88,88,44,0xff9796a8);
            SlotScreen.rounded(c,x+6,y+6,76,76,38,fill);
            fitText(c,ctrl.name(),r.x()+r.w()/2f,r.y()+r.h()+12,2f,r.w(),WHITE,true);
        }else{
            SlotScreen.rounded(c,r.x(),r.y(),r.w(),r.h(),18,STEEL);
            SlotScreen.rounded(c,r.x()+4,r.y()+4,r.w()-8,r.h()-8,14,fill);
            fitText(c,ctrl.name(),r.x()+r.w()/2f,r.y()+r.h()/2f-8,
                    ctrl.name().length()>7?1.4f:2.0f,r.w()-12,WHITE,true);
        }
    }
    private static void drawPanel(DrawContext c,SlotLayout.Rect r,int fill,int border){
        float radius=Math.min(18f,Math.min(r.w(),r.h())/4f);
        SlotScreen.rounded(c,r.x(),r.y(),r.w(),r.h(),radius,border);
        SlotScreen.rounded(c,r.x()+4,r.y()+4,r.w()-8,r.h()-8,Math.max(2f,radius-4f),fill);
    }
    /** Geometry-only fitting, also used by desktop and 4:3/ultrawide tests. */
    public static float fitScale(int glyphPixelWidth,float base,float allowedWidth){
        if(glyphPixelWidth<=0||base<=0||allowedWidth<=0)throw new IllegalArgumentException("fit");
        return Math.min(base,allowedWidth/glyphPixelWidth);
    }
    private void fitText(DrawContext c,String text,float x,float y,float base,float maxWidth,int tint,boolean center){
        if(text==null||text.isEmpty())return;
        float scale=fitScale(client.textRenderer.getWidth(text),base,maxWidth);
        c.getMatrices().push();
        c.getMatrices().translate(center?x-client.textRenderer.getWidth(text)*scale/2:x,y,0);
        c.getMatrices().scale(scale,scale,1);
        c.drawTextWithShadow(client.textRenderer,text,0,0,tint);
        c.getMatrices().pop();
    }
}
