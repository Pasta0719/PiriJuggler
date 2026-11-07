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
