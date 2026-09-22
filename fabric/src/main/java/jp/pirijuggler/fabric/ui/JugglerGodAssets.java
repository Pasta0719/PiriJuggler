package jp.pirijuggler.fabric.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Dedicated presentation namespace for the JUGGLER_GOD successor machine.
 *
 * Current appearance intentionally falls back to the existing JUGGLER assets.
 * A resource pack or later committed asset at textures/juggler_god/<path>
 * overrides only JUGGLER_GOD without changing ordinary JUGGLER.
 */
public final class JugglerGodAssets {
    public static Identifier texture(String machineType, String ordinaryPath) {
        Identifier ordinary = Identifier.of("piri", "textures/" + ordinaryPath);
        if (!"JUGGLER_GOD".equals(machineType)) return ordinary;
        Identifier dedicated = Identifier.of("piri", "textures/juggler_god/" + ordinaryPath);
        return MinecraftClient.getInstance().getResourceManager().getResource(dedicated).isPresent()
                ? dedicated : ordinary;
    }

    private JugglerGodAssets() {}
}
