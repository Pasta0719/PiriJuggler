package jp.pirijuggler.fabric.render;

import jp.pirijuggler.fabric.PiriJugglerClient;
import jp.pirijuggler.fabric.network.RemoteMachineViewState;
import jp.pirijuggler.fabric.ui.UiConstants;
import jp.pirijuggler.fabric.ui.JugglerGodAssets;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Phase13 external cabinet renderer.
 * Geometry/layout is mapped directly from SlotLayout.CABINET (290,205,1340,835).
 */
public final class WorldCabinetRenderer {
    private static final double MAX_DISTANCE_SQ = 32.0 * 32.0;
    private static final int FULL_LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final Identifier WHITE = Identifier.of("piri", "textures/world/white.png");
    private static final Identifier LAMP_ON = Identifier.of("piri", "textures/lamp/piri_chance_on.png");
    private static final Identifier LAMP_OFF = Identifier.of("piri", "textures/lamp/piri_chance_off.png");

    private static final double CAB_X=290, CAB_Y=205, CAB_W=1340, CAB_H=835;

    private WorldCabinetRenderer(){}

    public static void register(){ WorldRenderEvents.AFTER_ENTITIES.register(WorldCabinetRenderer::render); }

    private static void render(WorldRenderContext context){
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.world==null||context.world()==null||context.camera()==null||context.consumers()==null)return;
        Vec3d camera=context.camera().getPos();
        String dimension=context.world().getRegistryKey().getValue().toString();
        long now=System.nanoTime();

        for(RemoteMachineViewState state:PiriJugglerClient.remoteMachines().viewSnapshot()){
            if(!MachineWorldRendererRegistry.hasRenderer(state.machineType()))continue;
            if(!state.dimension().isEmpty()&&!dimension.equals(state.dimension()))continue;
            double ax=state.x()+.5,ay=state.y()+.5,az=state.z()+.5;
            double dx=camera.x-ax,dy=camera.y-ay,dz=camera.z-az;
            if(dx*dx+dy*dy+dz*dz>MAX_DISTANCE_SQ)continue;

            var basis=CabinetPlacement.basis(state.x(),state.y(),state.z(),state.facing());
            if(context.frustum()!=null){
                var c=basis.center();
                if(!context.frustum().isVisible(new Box(c.x()-1,c.y()-1,c.z()-1,c.x()+1,c.y()+1,c.z()+1)))continue;
            }
            draw(context,state,basis,camera,now,client);
        }
    }

    private static void draw(WorldRenderContext context,RemoteMachineViewState state,
                             CabinetPlacement.Basis basis,Vec3d camera,long now,MinecraftClient client){
        var consumers=context.consumers();

        // Compact public data lamp above the cabinet.
        double dataY=CabinetPlacement.HEIGHT/2.0+.125;
        quad(consumers,WHITE,basis,camera,0,dataY,CabinetPlacement.WIDTH,.20,.0010,UiConstants.color("CABINET_EDGE"),0,0,1,1);
        quad(consumers,WHITE,basis,camera,0,dataY,CabinetPlacement.WIDTH-.015,.185,.0016,UiConstants.color("DISPLAY_BG"),0,0,1,1);
        quad(consumers,WHITE,basis,camera,.11,dataY,.006,.165,.0020,UiConstants.color("BUTTON_METAL_DARK"),0,0,1,1);

        localText(consumers,client.textRenderer,basis,camera,-.45,dataY+.055,"BIG",.00245f,UiConstants.color("DISPLAY_BIG"),false);
        localText(consumers,client.textRenderer,basis,camera,-.23,dataY+.055,Long.toString(state.bigCount()),.00285f,UiConstants.color("DISPLAY_WHITE"),false);
        localText(consumers,client.textRenderer,basis,camera,-.45,dataY-.015,"REG",.00245f,UiConstants.color("DISPLAY_REG"),false);
        localText(consumers,client.textRenderer,basis,camera,-.23,dataY-.015,Long.toString(state.regCount()),.00285f,UiConstants.color("DISPLAY_WHITE"),false);

        long bonusTotal=state.bigCount()+state.regCount();
        String combined=bonusTotal==0?"1/---":String.format(Locale.ROOT,"1/%.1f",state.totalGames()/(double)bonusTotal);
        localText(consumers,client.textRenderer,basis,camera,.31,dataY+.050,"合算",.00205f,UiConstants.color("DISPLAY_GREEN"),true);
        localText(consumers,client.textRenderer,basis,camera,.31,dataY-.018,combined,.00285f,UiConstants.color("DISPLAY_WHITE"),true);

        // Cabinet face. JUGGLER_GOD uses layered gold bands instead of a flat color.
        boolean jugglerGod="JUGGLER_GOD".equals(state.machineType());
        quad(consumers,WHITE,basis,camera,0,0,CabinetPlacement.WIDTH,CabinetPlacement.HEIGHT,0,
                jugglerGod?UiConstants.color("CABINET_EDGE_LIGHT"):UiConstants.color("CABINET_EDGE"),0,0,1,1);
        if(jugglerGod){
            double innerW=CabinetPlacement.WIDTH-.015,innerH=CabinetPlacement.HEIGHT-.015;
            double bandH=innerH/5.0;
            int[] gold={
                    UiConstants.color("JUGGLER_GOD_GOLD_TOP"),
                    UiConstants.color("JUGGLER_GOD_GOLD_MID"),
                    UiConstants.color("JUGGLER_GOD_GOLD_MID"),
                    UiConstants.color("JUGGLER_GOD_GOLD_LOW"),
                    UiConstants.color("JUGGLER_GOD_GOLD_BOTTOM")
            };
            for(int i=0;i<5;i++){
                double cy=innerH/2.0-bandH/2.0-i*bandH;
                quad(consumers,WHITE,basis,camera,0,cy,innerW,bandH+.001,.0006,gold[i],0,0,1,1);
            }
            quad(consumers,WHITE,basis,camera,0,innerH/2.0-.030,innerW-.030,.015,.0008,
                    UiConstants.color("JUGGLER_GOD_GOLD_HIGHLIGHT"),0,0,1,1);
        }else{
            quad(consumers,WHITE,basis,camera,0,0,CabinetPlacement.WIDTH-.015,CabinetPlacement.HEIGHT-.015,.0006,
                    UiConstants.color("CABINET_BG"),0,0,1,1);
        }

        // SlotScreen reel separator: (670,300)-(1570,690), mapped inside CABINET.
        boolean godBlackout=state.godFreeze();
        rect(consumers,WHITE,basis,camera,670,300,900,390,.0010,
                godBlackout?0xff111111:UiConstants.color("REEL_SEPARATOR"),0,0,1,1);

        for(int reel=0;reel<3;reel++){
            int x=670+315*reel;
            int reelBg="JUGGLER_GOD".equals(state.machineType())
                    ?UiConstants.color("JUGGLER_GOD_REEL_BG")
                    :UiConstants.color("REEL_BG");
            rect(consumers,WHITE,basis,camera,x,300,270,390,.0015,
                    godBlackout?0xff141414:reelBg,0,0,1,1);

            double phase=state.phase(reel,now);
            int middle=(int)Math.floor(phase);
            double fraction=phase-middle;
            for(int row=-2;row<=2;row++){
                String symbol=UiConstants.symbol(reel,middle+row);
                boolean wide=symbol.equals("seven")||symbol.equals("bar");
                double boxW=wide?230:130;
                double boxH=symbol.equals("bar")?150:130;
                double sy=430+(row-fraction)*130-(boxH-130)/2.0;
                double sw=boxW,sh=boxH;
                if("JUGGLER_GOD".equals(state.machineType())){
                    var size=JugglerGodAssets.textureSize(state.machineType(),"symbols/"+symbol+".png");
                    double fit=Math.min(boxW/size.width(),boxH/size.height());
                    sw=size.width()*fit;sh=size.height()*fit;
                    sy+=(boxH-sh)/2.0;
                }
                symbolTint(consumers,basis,camera,state.machineType(),symbol,x+(270-sw)/2.0,sy,sw,sh,300,690,
                        godBlackout?0xff202020:0xffffffff);
            }

            if(godBlackout&&state.godRevealed(reel,now)){
                double boxW=230,boxH=150;
                var size=JugglerGodAssets.textureSize(state.machineType(),"symbols/bar.png");
                double fit=Math.min(boxW/size.width(),boxH/size.height());
                double sw=size.width()*fit,sh=size.height()*fit;
                symbolTint(consumers,basis,camera,state.machineType(),"bar",x+(270-sw)/2.0,420+(boxH-sh)/2.0,sw,sh,300,690,0xffffffff);
            }
        }

        // Exact SlotScreen lamp placement: (350,390,300,170).
        Identifier lamp=state.lampVisible(now)
                ?JugglerGodAssets.texture(state.machineType(),"lamp/piri_chance_on.png")
                :JugglerGodAssets.texture(state.machineType(),"lamp/piri_chance_off.png");
        if("JUGGLER_GOD".equals(state.machineType())){
            double boxW=300,boxH=170;
            var size=JugglerGodAssets.textureSize(state.machineType(),"lamp/piri_chance_"+(state.lampVisible(now)?"on":"off")+".png");
            double fit=Math.min(boxW/size.width(),boxH/size.height());
            double lw=size.width()*fit,lh=size.height()*fit;
            rect(consumers,lamp,basis,camera,350+(boxW-lw)/2.0,390+(boxH-lh)/2.0,lw,lh,.0025,0xffffffff,0,0,1,1);
        }else{
            rect(consumers,lamp,basis,camera,350,390,300,170,.0025,0xffffffff,0,0,1,1);
        }

        // Exact status panel: (670,710,900,95), but only public remote fields.
        rect(consumers,WHITE,basis,camera,670,710,900,95,.0012,UiConstants.color("DISPLAY_BG"),0,0,1,1);
        text(consumers,client.textRenderer,basis,camera,690,725,"CREDIT",2.0f,UiConstants.color("TEXT_MAIN"));
        text(consumers,client.textRenderer,basis,camera,690,758,Long.toString(state.credit()),3.0f,UiConstants.color("TEXT_MAIN"));
        text(consumers,client.textRenderer,basis,camera,1086,725,"PAY",2.0f,UiConstants.color("TEXT_MAIN"));
        text(consumers,client.textRenderer,basis,camera,1086,758,Long.toString(state.pay()),3.0f,UiConstants.color("TEXT_MAIN"));

        if("REPLAY_READY".equals(state.gameState()))
            textCentered(consumers,client.textRenderer,basis,camera,1050,816,"REPLAY",2.0f,UiConstants.color("TEXT_MAIN"));
        if(!"NONE".equals(state.bonusMode()))
            textCentered(consumers,client.textRenderer,basis,camera,1090,816,
                    "COUNT "+state.bonusCount(),2.0f,UiConstants.color("TEXT_MAIN"));

        // Match visible cabinet controls from the real SlotScreen. Decorative only in world view.
        control(consumers,basis,camera,440,860,150,100,"BET",client.textRenderer);
        stopButton(consumers,basis,camera,720,865,180,110,"LEFT",client.textRenderer);
        stopButton(consumers,basis,camera,990,865,180,110,"CENTER",client.textRenderer);
        stopButton(consumers,basis,camera,1260,865,180,110,"RIGHT",client.textRenderer);
    }

    private static void control(VertexConsumerProvider c,CabinetPlacement.Basis b,Vec3d cam,
                                double x,double y,double w,double h,String label,TextRenderer tr){
        rect(c,WHITE,b,cam,x,y,w,h,.0012,UiConstants.color("BUTTON_METAL_DARK"),0,0,1,1);
        insetRect(c,b,cam,x,y,w,h,5,.0020,UiConstants.color("BUTTON_RED"));
        textCentered(c,tr,b,cam,x+w/2,y+h/2-9,label,2.0f,UiConstants.color("TEXT_MAIN"));
    }

    private static void stopButton(VertexConsumerProvider c,CabinetPlacement.Basis b,Vec3d cam,
                                   double x,double y,double w,double h,String label,TextRenderer tr){
        double s=88;
        rect(c,WHITE,b,cam,x+(w-s)/2,y+(h-s)/2,s,s,.0012,UiConstants.color("BUTTON_METAL"),0,0,1,1);
        rect(c,WHITE,b,cam,x+(w-76)/2,y+(h-76)/2,76,76,.0020,UiConstants.color("BUTTON_RED"),0,0,1,1);
        textCentered(c,tr,b,cam,x+w/2,y+h+12,label,2.0f,UiConstants.color("TEXT_MAIN"));
    }

    private static void insetRect(VertexConsumerProvider c,CabinetPlacement.Basis b,Vec3d cam,
                                  double x,double y,double w,double h,double inset,double depth,int color){
        rect(c,WHITE,b,cam,x+inset,y+inset,w-inset*2,h-inset*2,depth,color,0,0,1,1);
    }

    private static void symbol(VertexConsumerProvider c,CabinetPlacement.Basis b,Vec3d cam,String machineType,String symbol,
                               double x,double y,double w,double h,double clipTop,double clipBottom){
        symbolTint(c,b,cam,machineType,symbol,x,y,w,h,clipTop,clipBottom,0xffffffff);
    }

    private static void symbolTint(VertexConsumerProvider c,CabinetPlacement.Basis b,Vec3d cam,String machineType,String symbol,
                                   double x,double y,double w,double h,double clipTop,double clipBottom,int tint){
        double y0=Math.max(y,clipTop), y1=Math.min(y+h,clipBottom);
        if(y1<=y0)return;
        float v0=(float)((y0-y)/h),v1=(float)((y1-y)/h);
        rect(c,JugglerGodAssets.texture(machineType,"symbols/"+symbol+".png"),b,cam,x,y0,w,y1-y0,.0025,tint,0,v0,1,v1);
    }

    private static void rect(VertexConsumerProvider c,Identifier tex,CabinetPlacement.Basis b,Vec3d cam,
                             double sx,double sy,double sw,double sh,double depth,int color,
                             float u0,float v0,float u1,float v1){
        double cx=((sx-CAB_X)+sw/2.0)/CAB_W*CabinetPlacement.WIDTH-CabinetPlacement.WIDTH/2.0;
        double cy=CabinetPlacement.HEIGHT/2.0-((sy-CAB_Y)+sh/2.0)/CAB_H*CabinetPlacement.HEIGHT;
        quad(c,tex,b,cam,cx,cy,sw/CAB_W*CabinetPlacement.WIDTH,sh/CAB_H*CabinetPlacement.HEIGHT,depth,color,u0,v0,u1,v1);
    }

    private static void quad(VertexConsumerProvider consumers,Identifier texture,CabinetPlacement.Basis basis,Vec3d camera,
                             double cx,double cy,double width,double height,double depth,int color,
                             float u0,float v0,float u1,float v1){
        var layer=depth==0?basis:new CabinetPlacement.Basis(
                basis.center().add(basis.front().scale(depth)),basis.front(),basis.right(),basis.up());
        double l=cx-width/2,r=cx+width/2,bt=cy-height/2,t=cy+height/2;
        var p0=relative(CabinetPlacement.point(layer,l,bt),camera);
        var p1=relative(CabinetPlacement.point(layer,r,bt),camera);
        var p2=relative(CabinetPlacement.point(layer,r,t),camera);
        var p3=relative(CabinetPlacement.point(layer,l,t),camera);
        var n=basis.front();
        VertexConsumer vc=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        vertex(vc,p0,n,color,u0,v1);vertex(vc,p1,n,color,u1,v1);vertex(vc,p2,n,color,u1,v0);vertex(vc,p3,n,color,u0,v0);
    }

    private static void vertex(VertexConsumer vc,CabinetPlacement.Vec p,CabinetPlacement.Vec n,int color,float u,float v){
        vc.vertex((float)p.x(),(float)p.y(),(float)p.z())
                .color(color>>>16&255,color>>>8&255,color&255,color>>>24&255)
                .texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(FULL_LIGHT)
                .normal((float)n.x(),(float)n.y(),(float)n.z());
    }

    private static void text(VertexConsumerProvider c,TextRenderer tr,CabinetPlacement.Basis b,Vec3d cam,
                             double sx,double sy,String value,float screenScale,int color){
        text0(c,tr,b,cam,sx,sy,value,screenScale,color,false);
    }

    private static void textCentered(VertexConsumerProvider c,TextRenderer tr,CabinetPlacement.Basis b,Vec3d cam,
                                     double sx,double sy,String value,float screenScale,int color){
        text0(c,tr,b,cam,sx,sy,value,screenScale,color,true);
    }

    private static void text0(VertexConsumerProvider consumers,TextRenderer tr,CabinetPlacement.Basis b,Vec3d cam,
                              double sx,double sy,String value,float screenScale,int color,boolean centered){
        double lx=(sx-CAB_X)/CAB_W*CabinetPlacement.WIDTH-CabinetPlacement.WIDTH/2.0;
        double ly=CabinetPlacement.HEIGHT/2.0-(sy-CAB_Y)/CAB_H*CabinetPlacement.HEIGHT;
        float scale=(float)(screenScale*CabinetPlacement.WIDTH/CAB_W);
        var origin=CabinetPlacement.point(b,lx,ly).add(b.front().scale(.0035));
        CabinetPlacement.Vec t=relative(origin,cam);
        CabinetPlacement.Vec r=b.right();
        CabinetPlacement.Vec u=b.up();
        CabinetPlacement.Vec f=b.front();
        Matrix4f m=new Matrix4f().identity();
        m.m00((float)(r.x()*scale)).m01((float)(r.y()*scale)).m02((float)(r.z()*scale));
        m.m10((float)(-u.x()*scale)).m11((float)(-u.y()*scale)).m12((float)(-u.z()*scale));
        m.m20((float)f.x()).m21((float)f.y()).m22((float)f.z());
        m.m30((float)t.x()).m31((float)t.y()).m32((float)t.z());
        float x=centered?-tr.getWidth(value)/2f:0;
        tr.draw(value,x,0,color,false,m,consumers,TextRenderer.TextLayerType.NORMAL,0,FULL_LIGHT);
    }

    private static void localText(VertexConsumerProvider consumers,TextRenderer tr,CabinetPlacement.Basis b,Vec3d cam,
                                  double lx,double ly,String value,float scale,int color,boolean centered){
        var origin=CabinetPlacement.point(b,lx,ly).add(b.front().scale(.0040));
        CabinetPlacement.Vec t=relative(origin,cam);
        CabinetPlacement.Vec r=b.right();
        CabinetPlacement.Vec u=b.up();
        CabinetPlacement.Vec f=b.front();
        Matrix4f m=new Matrix4f().identity();
        m.m00((float)(r.x()*scale)).m01((float)(r.y()*scale)).m02((float)(r.z()*scale));
        m.m10((float)(-u.x()*scale)).m11((float)(-u.y()*scale)).m12((float)(-u.z()*scale));
        m.m20((float)f.x()).m21((float)f.y()).m22((float)f.z());
        m.m30((float)t.x()).m31((float)t.y()).m32((float)t.z());
        float x=centered?-tr.getWidth(value)/2f:0;
        tr.draw(value,x,0,color,false,m,consumers,TextRenderer.TextLayerType.NORMAL,0,FULL_LIGHT);
    }

    private static CabinetPlacement.Vec relative(CabinetPlacement.Vec p,Vec3d cam){
        return new CabinetPlacement.Vec(p.x()-cam.x,p.y()-cam.y,p.z()-cam.z);
    }
}
