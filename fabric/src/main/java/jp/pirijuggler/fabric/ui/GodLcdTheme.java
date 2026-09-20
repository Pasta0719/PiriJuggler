package jp.pirijuggler.fabric.ui;

import com.google.gson.*;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.*;

/**
 * Resource-pack driven GOD LCD scenes.
 *
 * Users can override piri:god_lcd/scenes.json and add arbitrary PNG layers in a
 * normal Minecraft resource pack. No mod rebuild is required.
 */
public final class GodLcdTheme {
    public record Layer(Identifier texture,int x,int y,int w,int h,int textureWidth,int textureHeight,float alpha) {}
    public record Scene(int background,int accent,String title,String subtitle,List<Layer> layers) {}

    private static final Identifier MANIFEST=Identifier.of("piri","god_lcd/scenes.json");
    private static volatile Map<String,Scene> scenes=defaults();

    public static void register(){
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId(){return Identifier.of("piri","god_lcd_theme");}
            @Override public void reload(ResourceManager manager){scenes=load(manager);}
        });
    }

    public static Scene scene(String event,String phase){
        Map<String,Scene> current=scenes;
        if(event!=null){
            Scene byEvent=current.get("EVENT_"+event.toUpperCase(Locale.ROOT));
            if(byEvent!=null)return byEvent;
        }
        if(phase!=null){
            Scene byPhase=current.get(phase.toUpperCase(Locale.ROOT));
            if(byPhase!=null)return byPhase;
        }
        return current.getOrDefault("NORMAL",defaults().get("NORMAL"));
    }

    public static void renderLayers(DrawContext c,Scene scene,int ox,int oy){
        MinecraftClient client=MinecraftClient.getInstance();
        for(Layer layer:scene.layers()){
            if(client.getResourceManager().getResource(layer.texture()).isEmpty())continue;
            client.getTextureManager().bindTexture(layer.texture());
            client.getTextureManager().getTexture(layer.texture()).setFilter(true,false);
            c.setShaderColor(1,1,1,layer.alpha());
            c.drawTexture(layer.texture(),ox+layer.x(),oy+layer.y(),0,0,layer.w(),layer.h(),layer.textureWidth(),layer.textureHeight());
            c.setShaderColor(1,1,1,1);
        }
    }

    private static Map<String,Scene> load(ResourceManager manager){
        try{
            var resource=manager.getResource(MANIFEST);
            if(resource.isEmpty())return defaults();
            try(Reader reader=resource.get().getReader()){
                JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject source=root.has("scenes")?root.getAsJsonObject("scenes"):root;
                Map<String,Scene> parsed=new LinkedHashMap<>();
                for(var entry:source.entrySet()){
                    JsonObject o=entry.getValue().getAsJsonObject();
                    int bg=parseColor(o,"background",0xff101522);
                    int accent=parseColor(o,"accent",0xffffd15a);
                    String title=o.has("title")?o.get("title").getAsString():entry.getKey();
                    String subtitle=o.has("subtitle")?o.get("subtitle").getAsString():"";
                    List<Layer> layers=new ArrayList<>();
                    if(o.has("layers"))for(JsonElement element:o.getAsJsonArray("layers")){
                        JsonObject l=element.getAsJsonObject();
                        Identifier id=Identifier.of(l.get("texture").getAsString());
                        int x=integer(l,"x",0),y=integer(l,"y",0),w=integer(l,"w",1260),h=integer(l,"h",650);
                        int tw=integer(l,"textureWidth",w),th=integer(l,"textureHeight",h);
                        float alpha=l.has("alpha")?l.get("alpha").getAsFloat():1f;
                        layers.add(new Layer(id,x,y,w,h,tw,th,Math.max(0,Math.min(1,alpha))));
                    }
                    parsed.put(entry.getKey().toUpperCase(Locale.ROOT),new Scene(bg,accent,title,subtitle,List.copyOf(layers)));
                }
                Map<String,Scene> result=new LinkedHashMap<>(defaults());
                result.putAll(parsed);
                return Map.copyOf(result);
            }
        }catch(Exception ignored){return defaults();}
    }

    private static int integer(JsonObject o,String key,int fallback){return o.has(key)?o.get(key).getAsInt():fallback;}
    private static int parseColor(JsonObject o,String key,int fallback){
        if(!o.has(key))return fallback;
        String v=o.get(key).getAsString().trim().replace("#","");
        try{
            long raw=Long.parseUnsignedLong(v,16);
            if(v.length()<=6)raw|=0xff000000L;
            return (int)raw;
        }catch(NumberFormatException ignored){return fallback;}
    }

    private static Map<String,Scene> defaults(){
        Map<String,Scene> m=new LinkedHashMap<>();
        m.put("NORMAL",s(0xff10213a,0xffffd15a,"OLYMPUS","GOD SYSTEM"));
        m.put("GG",s(0xff3b2308,0xffffc53d,"GOD GAME","GG"));
        m.put("G_ZONE",s(0xff172947,0xffb8d6ff,"G-ZONE","NEXT GOD GAME"));
        m.put("SGG",s(0xff3b0808,0xffff5050,"SUPER GOD GAME","SGG"));
        m.put("SGG_COMEBACK",s(0xff38150b,0xffff9966,"SGG","3G COMEBACK"));
        m.put("Z_ZONE",s(0xff052f3b,0xff57e8ff,"Z-ZONE","YELLOW 7 CHANCE"));
        m.put("Z_GAME",s(0xff053a31,0xff70ffd9,"Z-GAME","STOCK CHANCE"));
        m.put("EVENT_GOD",s(0xff4a3100,0xffffe16b,"G O D","PREMIUM GOD GAME"));
        m.put("EVENT_GOD_IN_GG",s(0xff4a3100,0xffffe16b,"G O D","PREMIUM IN GG"));
        m.put("EVENT_RED7_SGG",s(0xff480707,0xffff4545,"7 7 7","SUPER GOD GAME"));
        m.put("EVENT_CEILING_Z",s(0xff111111,0xffffffff,"0 0 0","CEILING"));
        m.put("EVENT_GAIA_Z",s(0xff07382e,0xff7affd4,"GAIA","Z-ZONE"));
        return Map.copyOf(m);
    }
    private static Scene s(int bg,int accent,String title,String subtitle){return new Scene(bg,accent,title,subtitle,List.of());}
    private GodLcdTheme(){}
}
