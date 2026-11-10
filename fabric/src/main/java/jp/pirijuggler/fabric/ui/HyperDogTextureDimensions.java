package jp.pirijuggler.fabric.ui;

/**
 * Resolve the source pixel dimensions for a HYPER_DOG reel symbol.
 *
 * Resource packs and approved dog images may be 256px or 320px.
 * DrawContext.drawTexture must receive the full source dimensions; a
 * hard-coded 256px source rectangle crops 320px artwork.
 */
public final class HyperDogTextureDimensions {
    private static final String PREFIX = "gui/original_at/";

    public static int dimension(String path) {
        if (path == null || !path.startsWith(PREFIX + "symbols/") || !path.endsWith(".png")) {
            throw new IllegalArgumentException("Invalid HYPER_DOG symbol path: " + path);
        }
        var size = JugglerGodAssets.textureSize("HYPER_DOG", path.substring(PREFIX.length()));
        if (size.width() != size.height() || size.width() <= 0) {
            throw new IllegalStateException("Invalid square HYPER_DOG symbol dimensions: " + path);
        }
        return size.width();
    }

    private HyperDogTextureDimensions() {}
}
