package jp.pirijuggler.fabric.ui;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.net.URL;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Checks that all seven approved symbols ship in the actual Fabric resource set. */
class HyperDogSymbolAssetsTest {
    /** World renders extra rows outside the stop index and crosses the 21-symbol seam. */
    @Test void spectatorRowsWrapAndNeverUseStrictAuthoritativeStopValidation() {
        for(int reel=0;reel<3;reel++)for(int middle=-65;middle<84;middle++)
            for(int row=-2;row<=2;row++){
                int index=middle+row;
                String expected=jp.pirijuggler.common.reel.HyperDogReelControl
                        .icon(jp.pirijuggler.common.reel.Reel.values()[reel],index)
                        .name().toLowerCase(java.util.Locale.ROOT);
                assertEquals(expected,UiConstants.symbol("HYPER_DOG",reel,index),
                        "world reel "+reel+" at visible index "+index);
            }
        for(int reel=0;reel<3;reel++)for(int index=0;index<21;index++)
            assertEquals(UiConstants.symbol(reel,index),
                    UiConstants.symbol("JUGGLER",reel,index),
                    "existing reel symbol path must not change");
    }

    @Test void allApprovedSymbolsAreTransparent256PixelPngs() throws IOException {
        List<String> names=List.of("replay","red7","rainbow7","bar","grape","cherry","watermelon");
        for(String name:names) {
            String path="assets/piri/textures/gui/original_at/symbols/"+name+".png";
            URL url=getClass().getClassLoader().getResource(path);
            assertNotNull(url,"Missing Fabric symbol asset: "+path);
            BufferedImage png=ImageIO.read(url);
            assertNotNull(png,"Corrupt PNG: "+path);
            assertEquals(256,png.getWidth(),name);
            assertEquals(256,png.getHeight(),name);
            assertTrue(png.getColorModel().hasAlpha(),name+" must preserve transparency");
            assertEquals(0,(png.getRGB(0,0)>>>24)&255,name+" must have transparent background");
        }
    }
}
