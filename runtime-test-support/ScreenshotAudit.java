import java.nio.file.*;
import javax.imageio.ImageIO;
/** Reads actual Minecraft screenshots; never creates or edits an image. */
public final class ScreenshotAudit {
    public static void main(String[] args) throws Exception {
        var image=ImageIO.read(Path.of(args[0]).toFile());double scale=Math.min(image.getWidth()/1920.0,image.getHeight()/1080.0),ox=(image.getWidth()-1920*scale)/2,oy=(image.getHeight()-1080*scale)/2;
        for(int reel=0;reel<3;reel++) {
            int colored=0;for(int y=(int)Math.ceil(oy+300*scale);y<(int)(oy+690*scale);y++)for(int x=(int)Math.ceil(ox+(670+315*reel)*scale);x<(int)(ox+(940+315*reel)*scale);x++) {
                int p=image.getRGB(x,y),r=p>>16&255,g=p>>8&255,b=p&255;
                if(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>45)colored++;
            }
            System.out.println("reel "+reel+" colored symbol pixels="+colored);if(colored<50)throw new AssertionError("Reel symbols missing in actual screenshot: "+reel);
        }
    }
}
