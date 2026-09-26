package jp.pirijuggler.fabric.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedicated presentation namespace for the JUGGLER_GOD successor machine.
 *
 * Current appearance intentionally falls back to the existing JUGGLER assets.
 * A resource pack or later committed asset at textures/juggler_god/<path>
 * overrides only JUGGLER_GOD without changing ordinary JUGGLER.
 */
public final class JugglerGodAssets {
    public record TextureSize(int width,int height) {}
    private static final Map<Identifier,TextureSize> SIZE_CACHE=new ConcurrentHashMap<>();

    public static Identifier texture(String machineType, String ordinaryPath) {
        Identifier ordinary = Identifier.of("piri", "textures/" + ordinaryPath);
        if (!"JUGGLER_GOD".equals(machineType)&&!"JUGGLER_GOD_EXTREME".equals(machineType)) return ordinary;
        Identifier dedicated = Identifier.of("piri", "textures/juggler_god/" + ordinaryPath);
        return MinecraftClient.getInstance().getResourceManager().getResource(dedicated).isPresent()
                ? dedicated : ordinary;
    }

    /** Read the actual PNG dimensions used after GOD-specific resource-pack resolution. */
    public static TextureSize textureSize(String machineType,String ordinaryPath) {
        Identifier id=texture(machineType,ordinaryPath);
        return SIZE_CACHE.computeIfAbsent(id,key->{
            var resource=MinecraftClient.getInstance().getResourceManager().getResource(key)
                    .orElseThrow(()->new IllegalStateException("Missing texture "+key));
            try(var in=resource.getInputStream();var image=NativeImage.read(in)){
                return new TextureSize(image.getWidth(),image.getHeight());
            }catch(IOException failure){
                throw new IllegalStateException("Unable to read texture size "+key,failure);
            }
        });
    }

    private JugglerGodAssets() {}
}
