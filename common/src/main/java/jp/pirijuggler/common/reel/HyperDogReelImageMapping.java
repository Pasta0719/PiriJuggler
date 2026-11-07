package jp.pirijuggler.common.reel;

import java.util.Locale;

/** Logical resource path mapping; actual PNG delivery is independent from stop verification. */
public final class HyperDogReelImageMapping {
    public static String texturePath(Reel reel, int middleStop, int rowOffset) {
        return "gui/original_at/symbols/"+
                HyperDogReelControl.visible(reel,middleStop,rowOffset).name().toLowerCase(Locale.ROOT)+".png";
    }
    private HyperDogReelImageMapping(){}
}
