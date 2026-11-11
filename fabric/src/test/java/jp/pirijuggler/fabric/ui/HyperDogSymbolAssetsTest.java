package jp.pirijuggler.fabric.ui;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    @Test void sevenAndBarStayLargerThanNormalArtworkWithoutDistortingTheSourceAspect() {
        int normal=HyperDogCabinetScreen.symbolRenderSize("gui/original_at/symbols/grape.png");
        assertEquals(124,normal);
        for(String name:List.of("red7","bar","rainbow7")){
            int special=HyperDogCabinetScreen.symbolRenderSize("gui/original_at/symbols/"+name+".png");
            assertEquals(166,special,name);
            assertTrue(special>normal,name+" must have the larger cabinet-symbol treatment");
        }
        for(String name:List.of("replay","cherry","watermelon"))
            assertEquals(normal,HyperDogCabinetScreen.symbolRenderSize("gui/original_at/symbols/"+name+".png"));
    }

    @Test void allApprovedSymbolsAreTransparentPngsWithExactDogSources() throws IOException, NoSuchAlgorithmException {
        Map<String,String> expectedHashes=Map.of(
                "replay","8507303cbeeaf4017a132687aaffc221dd8d886468ad13a6578b1d7820816266",
                "red7","ccdc512b7300fd5812bbdb1e3d5fbeca21f6d47628324b185c21657b94a5fabf",
                "rainbow7","0195c47805400a2e715267527f24e30fc788cf2a1d8ac0c1c8bc59c95e734ced",
                "bar","a8e9d0fb31eaa6b8748fb7f3c3ba1d4dd833faf776be759c173d816ffa0d9ac0");
        List<String> names=List.of("replay","red7","rainbow7","bar","grape","cherry","watermelon");
        for(String name:names) {
            String path="assets/piri/textures/gui/original_at/symbols/"+name+".png";
            URL url=getClass().getClassLoader().getResource(path);
            assertNotNull(url,"Missing Fabric symbol asset: "+path);
            BufferedImage png=ImageIO.read(url);
            assertNotNull(png,"Corrupt PNG: "+path);
            int expectedDimension=expectedHashes.containsKey(name)?320:256;
            assertEquals(expectedDimension,png.getWidth(),name);
            assertEquals(expectedDimension,png.getHeight(),name);
            if(expectedHashes.containsKey(name)){
                byte[] bytes;
                try(var stream=url.openStream()){bytes=stream.readAllBytes();}
                String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                assertEquals(expectedHashes.get(name),actual,name+" must be byte-identical to the approved source");
            }
            assertTrue(png.getColorModel().hasAlpha(),name+" must preserve transparency");
            assertEquals(0,(png.getRGB(0,0)>>>24)&255,name+" must have transparent background");
        }
    }
}
