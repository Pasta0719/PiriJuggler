import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Replace only GOLD tile (frame 7) in the JUGGLER_GOD hint atlas. */
public final class PatchRoyalGoldHint {
    private static final double[] STOPS = {0, .18, .42, .57, .76, 1};
    private static final int[][] COLORS = {
            {0xFF,0xF5,0xCC}, {0xFF,0xE1,0x7A}, {0xD9,0x9C,0x19},
            {0xFF,0xF1,0xAE}, {0xC8,0x87,0x0C}, {0x77,0x45,0x07}
    };

    public static void main(String[] args) throws Exception {
        Path source = args.length == 0
                ? Path.of("fabric/src/main/resources/assets/piri/textures/juggler_god/lamp/setting_hint_atlas.png")
                : Path.of(args[0]);
        BufferedImage atlas = ImageIO.read(source.toFile());
        if (atlas == null || atlas.getWidth() != 240 || atlas.getHeight() != 900)
            throw new IllegalArgumentException("Expected 240x900 hint atlas: " + source);
        int modified = 0;
        for (int y = 700; y < 800; y++) {
            double t = Math.min(1, Math.max(0, (y - 725.0) / 54.0));
            int stop = 0;
            while (stop + 1 < STOPS.length - 1 && t > STOPS[stop+1]) stop++;
            double u = (t-STOPS[stop])/(STOPS[stop+1]-STOPS[stop]);
            for (int x = 0; x < 240; x++) {
                int pixel = atlas.getRGB(x,y);
                int a = (pixel >>> 24) & 255;
                int r = (pixel >>> 16) & 255;
                int g = (pixel >>> 8) & 255;
                int b = pixel & 255;
                // Preserve untouched frames, the frame border and highlight stroke.
                if (a != 255 || r <= 140 || g <= 90 || b >= 52 || r <= g*1.18) continue;
                double shimmer = Math.max(0,1-Math.abs(x-74)/110.0)
                        *Math.max(0,1-Math.abs(y-730)/16.0)*.055;
                int[] channels = new int[3];
                for (int c=0;c<3;c++) {
                    double base=(1-u)*COLORS[stop][c]+u*COLORS[stop+1][c];
                    channels[c]=Math.min(255, (int)Math.round(base+(255-base)*shimmer));
                }
                atlas.setRGB(x,y,0xFF000000|(channels[0]<<16)|(channels[1]<<8)|channels[2]);
                modified++;
            }
        }
        if(modified<8000)throw new IllegalStateException("Original GOLD tile not found, changed="+modified);
        if(!ImageIO.write(atlas,"png",source.toFile())) throw new IllegalStateException("PNG encoding failed");
        System.out.println("Royal GOLD A applied, updated pixels: "+modified);
    }
}
