package jp.pirijuggler.fabric.render;

import jp.pirijuggler.fabric.PiriJugglerClient;
import jp.pirijuggler.fabric.network.RemoteMachineViewState;
import jp.pirijuggler.fabric.ui.UiConstants;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

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

        // Exact SlotScreen cabinet palette: gold edge + burgundy face.
        quad(consumers,WHITE,basis,camera,0,0,1.60,1.00,0,UiConstants.color("CABINET_EDGE"),0,0,1,1);
        quad(consumers,WHITE,basis,camera,0,0,1.585,.985,.0006,UiConstants.color("CABINET_BG"),0,0,1,1);

        // SlotScreen reel separator: (670,300)-(1570,690), mapped inside CABINET.
        rect(consumers,WHITE,basis,camera,670,300,900,390,.0010,UiConstants.color("REEL_SEPARATOR"),0,0,1,1);

        for(int reel=0;reel<3;reel++){
            int x=670+315*reel;
            rect(consumers,WHITE,basis,camera,x,300,270,390,.0015,UiConstants.color("REEL_BG"),0,0,1,1);

            double phase=state.phase(reel,now);
            int middle=(int)Math.floor(phase);
            double fraction=phase-middle;
            for(int row=-2;row<=2;row++){
                String symbol=UiConstants.symbol(reel,middle+row);
                boolean wide=symbol.equals("seven")||symbol.equals("bar");
                double sw=wide?230:130;
                double sh=symbol.equals("bar")?150:130;
                double sy=430+(row-fraction)*130-(sh-130)/2.0;
                symbol(consumers,basis,camera,symbol,x+(270-sw)/2.0,sy,sw,sh,300,690);
            }
        }

        // Exact SlotScreen lamp placement: (350,390,300,170).
        rect(consumers,state.lampVisible(now)?LAMP_ON:LAMP_OFF,basis,camera,350,390,300,170,.0025,0xffffffff,0,0,1,1);

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

    private static void symbol(VertexConsumerProvider c,CabinetPlacement.Basis b,Vec3d cam,String symbol,
                               double x,double y,double w,double h,double clipTop,double clipBottom){
        double y0=Math.max(y,clipTop), y1=Math.min(y+h,clipBottom);
        if(y1<=y0)return;
        float v0=(float)((y0-y)/h),v1=(float)((y1-y)/h);
        rect(c,Identifier.of("piri","textures/symbols/"+symbol+".png"),b,cam,x,y0,w,y1-y0,.0025,0xffffffff,0,v0,1,v1);
    }

    private static void rect(VertexConsumerProvider c,Identifier tex,CabinetPlacement.Basis b,Vec3d cam,
                             double sx,double sy,double sw,double sh,double depth,int color,
                             float u0,float v0,float u1,float v1){
        double cx=((sx-CAB_X)+sw/2.0)/CAB_W*1.60-.80;
        double cy=.50-((sy-CAB_Y)+sh/2.0)/CAB_H*1.00;
        quad(c,tex,b,cam,cx,cy,sw/CAB_W*1.60,sh/CAB_H,depth,color,u0,v0,u1,v1);
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
        double lx=(sx-CAB_X)/CAB_W*1.60-.80;
        double ly=.50-(sy-CAB_Y)/CAB_H;
        float scale=(float)(screenScale*1.60/CAB_W);
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

    private static CabinetPlacement.Vec relative(CabinetPlacement.Vec p,Vec3d cam){
        return new CabinetPlacement.Vec(p.x()-cam.x,p.y()-cam.y,p.z()-cam.z);
    }
}
