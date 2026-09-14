package jp.pirijuggler.assets;
import com.google.gson.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AssetGeneratorTest {
    private static final Path ROOT=Path.of(System.getProperty("piri.specRoot"));
    @Test void lockedGeneratedAssetsAndUserSymbolOverridesAreValid() throws Exception {
        var generator=new AssetGenerator(ROOT);var images=generator.generate();assertEquals(9,images.size());
        var manifest=JsonParser.parseString(Files.readString(ROOT.resolve("asset-tools/src/test/resources/image-sha256.json"))).getAsJsonObject();
        assertEquals(images.keySet(),manifest.keySet());
        for(var entry:images.entrySet()){
            String key=entry.getKey();
            String hash=manifest.get(key).getAsString();
            assertEquals(hash,AssetGenerator.sha(AssetGenerator.png(entry.getValue())),key);
            Path committed=ROOT.resolve("fabric/src/main/resources/assets/piri/textures/"+key);
            assertTrue(Files.isRegularFile(committed),key);
            if(key.startsWith("lamp/")){
                assertEquals(hash,AssetGenerator.sha(Files.readAllBytes(committed)),key);
                assertEquals(512,entry.getValue().getWidth());
                assertEquals(256,entry.getValue().getHeight());
                assertEquals(0,entry.getValue().getRGB(0,0)>>>24);
            } else {
                // Symbol textures are intentionally replaceable user art. They are no longer required to match
                // the deterministic placeholder generator or its historical SHA manifest.
                BufferedImage symbol=ImageIO.read(committed.toFile());
                assertNotNull(symbol,key);
                assertTrue(symbol.getWidth()>0&&symbol.getHeight()>0,key);
            }
        }
        var source=generator.lock().getAsJsonObject("symbolShapes");assertEquals(source.get("sourceSha256").getAsString(),AssetGenerator.sha(Files.readAllBytes(ROOT.resolve("docs/v3-section-121.txt"))));
    }
    @Test void bitmapWordGapIsExactlyTwentyPixelsAndHasNoAntialias() throws Exception {
        var generator=new AssetGenerator(ROOT);assertEquals(23,generator.textColumns("PIRI"));assertEquals(62,generator.textColumns("PIRI CHANCE"));
        var image=new BufferedImage(400,80,BufferedImage.TYPE_INT_ARGB);var g=AssetGenerator.graphics(image);generator.glyphs(g,"PIRI CHANCE",200,40,5,Color.WHITE);g.dispose();
        for(int y=0;y<80;y++)for(int x=0;x<400;x++){int alpha=image.getRGB(x,y)>>>24;assertTrue(alpha==0||alpha==255);if(x>=160&&x<180)assertEquals(0,alpha,"20px word gap");}
        assertEquals(255,image.getRGB(159,23)>>>24);assertEquals(255,image.getRGB(180,28)>>>24);
    }
}
