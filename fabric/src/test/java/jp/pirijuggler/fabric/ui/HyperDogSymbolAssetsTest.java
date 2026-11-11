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

    @Test void allApprovedSymbolsAreTransparentPngsWithStableSources() throws IOException, NoSuchAlgorithmException {
        Map<String,String> expectedHashes=Map.of(
                "bar","94c8a7fb32d43abafecb677757c1c4c062114b307d8e49b78eacd fefa52e151f".replace(" ",""),
                "cherry","8f50769ec4e2c551fa199a452db37aa9d35d1d1fe62032efb734ae97b57cc2a0",
                "grape","c2ef0a66e18fce4d911be491dc4123dde1c21579069d3f62a26ad2c1443b6195",
                "rainbow7","5b11f8ed7faf0324a36c9b14909bbee3be71597d07632e6091e2febbac99fd23",
                "red7","da91b4731a1ceb3e574f66e3273acdbe3d5b09120c8638c4e4420a314afd991e",
                "replay","8b3175365830a6c49933d72bff75c4efcd96e5930f202964fdf362f6c5318280",
                "watermelon","07f5222b2e58ee1679bd3e764667be93eb7dbe5ea47e4e51545add7f57bdf3b6");
        List<String> names=List.of("replay","red7","rainbow7","bar","grape","cherry","watermelon");
        for(String name:names) {
            String path="assets/piri/textures/gui/original_at/symbols/"+name+".png";
            URL url=getClass().getClassLoader().getResource(path);
            assertNotNull(url,"Missing Fabric symbol asset: "+path);
            BufferedImage png=ImageIO.read(url);
            assertNotNull(png,"Corrupt PNG: "+path);
            assertEquals(256,png.getWidth(),name);
            assertEquals(256,png.getHeight(),name);
            byte[] bytes;
            try(var stream=url.openStream()){bytes=stream.readAllBytes();}
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(expectedHashes.get(name),actual,name+" must be byte-identical to the approved source");
            assertTrue(png.getColorModel().hasAlpha(),name+" must preserve transparency");
            assertEquals(0,(png.getRGB(0,0)>>>24)&255,name+" must have transparent background");
        }
    }
}
