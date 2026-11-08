package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.fabric.PiriJugglerClient;
import jp.pirijuggler.fabric.network.PiriPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.Objects;
import org.lwjgl.glfw.GLFW;

/**
 * Phase02-only 3x3 preview with approved real PNG resources and genuine C2S
 * stop buttons. This is NOT the full Phase05 cabinet/game economy UI.
 */
public final class HyperDogReelPreviewScreen extends Screen {
    private final HyperDogClientReels reels;
    private int renderedFrames, renderedSymbols;
    private static final int SYMBOL=126;
    private static final int[] REEL_X={720,1020,1320};
    private static final int[] ROW_Y={300,430,560};

    public HyperDogReelPreviewScreen(HyperDogClientReels reels) {
        super(Text.literal("HYPER_DOG reel verification"));
        this.reels=Objects.requireNonNull(reels);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override protected void init(){client.mouse.unlockCursor();}
    @Override public void close(){} // The owning engine's Phase03+ session lifecycle controls exit.

    public int renderedFrames(){return renderedFrames;}
    public int renderedSymbols(){return renderedSymbols;}

    /** Uses real production client sequence and the server-authoritative stop request. */
    public boolean requestStop(PacketType action) {
        if(!reels.canStop(action))return false;
        Reel reel=switch(action) {
            case STOP_LEFT->Reel.LEFT;case STOP_CENTER->Reel.CENTER;case STOP_RIGHT->Reel.RIGHT;
            case SPACE_ACTION -> {
                Reel first=null;
                for(Reel r:Reel.values())if(reels.stopIndex(r)<0){first=r;break;}
                yield first;
            }
            default->null;
        };
        if(reel==null)return false;
        var session=PiriJugglerClient.session();
        if(session.sessionId()==null || session.machineId()!=reels.machineId())return false;
        JsonObjectBuilder b=new JsonObjectBuilder();
        b.add("sessionId",session.sessionId().toString());
        b.add("machineId",session.machineId());
        b.add("clientSequence",session.takeSequence());
        b.add("pressedIndex",reels.currentIndex(reel));
        ClientPlayNetworking.send(new PiriPayload(EnvelopeCodec.encode(Envelope.current(action,b.build()))));
        return true;
    }

    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        PacketType action=SlotKeys.action(key,scan);
        return action!=null && requestStop(action);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return false;
        var v=SlotLayout.Viewport.fit(width,height);
        double x=v.logicalX(mx),y=v.logicalY(my);
        if(y>=865 && y<975) {
            if(x>=720 && x<900)return requestStop(PacketType.STOP_LEFT);
            if(x>=990 && x<1170)return requestStop(PacketType.STOP_CENTER);
            if(x>=1260 && x<1440)return requestStop(PacketType.STOP_RIGHT);
        }
        return false;
    }
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta){}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        context.fill(0,0,width,height,0xff15151b);
        var vp=SlotLayout.Viewport.fit(width,height);
        context.getMatrices().push();
        context.getMatrices().translate(vp.x(),vp.y(),0);
        context.getMatrices().scale((float)vp.scale(),(float)vp.scale(),1f);
        context.fill(668,287,1572,701,0xff0a0a10);
        for(Reel reel:Reel.values()){
            int r=reel.ordinal();
            for(int row=-1;row<=1;row++){
                String path=reels.spinning()
                        ?reels.texturePath(reel,row)
                        :HyperDogReelImageMapping.texturePath(reel,0,row);
                Identifier id=Identifier.of("piri","textures/"+path);
                // DrawTexture's region size is SOURCE pixels in 1.21. Render the
                // entire 256x256 supplied image and scale it to the 126px cell.
                // Passing SYMBOL as the source region cropped off the long body.
                context.getMatrices().push();
                context.getMatrices().translate(REEL_X[r],ROW_Y[row+1],0);
                context.getMatrices().scale(SYMBOL/256f,SYMBOL/256f,1f);
                context.drawTexture(id,0,0,0,0,256,256,256,256);
                context.getMatrices().pop();
                renderedSymbols++;
            }
            int outline=reels.stopIndex(reel)<0?0xff595a66:0xff42c9a6;
            context.drawBorder(REEL_X[r]-4,ROW_Y[0]-4,SYMBOL+8,SYMBOL*3+12,outline);
        }
        context.getMatrices().pop();
        renderedFrames++;
        super.render(context,mouseX,mouseY,delta);
    }
    /** Local helper keeps the payload builder strongly scoped to this screen. */
    private static final class JsonObjectBuilder {
        private final com.google.gson.JsonObject body=new com.google.gson.JsonObject();
        void add(String k,String value){body.addProperty(k,value);}
        void add(String k,int value){body.addProperty(k,value);}
        void add(String k,long value){body.addProperty(k,value);}
        com.google.gson.JsonObject build(){return body;}
    }
}
