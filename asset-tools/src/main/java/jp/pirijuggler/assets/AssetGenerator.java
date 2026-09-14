package jp.pirijuggler.assets;

import com.google.gson.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Java2D only. Every prescribed symbol coordinate is read from the v3 transcription. */
public final class AssetGenerator {
    private final JsonObject lock;
    public AssetGenerator(Path root) throws IOException {
        lock = JsonParser.parseString(Files.readString(root.resolve("docs/spec-lock.json"))).getAsJsonObject();
    }
    public JsonObject lock() { return lock.deepCopy(); }
    public static Color color(String hex) { return Color.decode(hex); }
    private Color ui(String name) { return color(lock.getAsJsonObject("uiColors").get(name).getAsString()); }
    public static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }
    public Map<String, BufferedImage> generate() {
        Map<String, BufferedImage> images = new TreeMap<>();
        var data = lock.getAsJsonObject("symbolShapes");
        for (var entry : data.getAsJsonObject("shapes").entrySet()) {
            var image = new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
            var g = graphics(image);
            for (var op : entry.getValue().getAsJsonArray()) draw(g,op.getAsJsonObject());
            if (entry.getKey().equals("piero")) {
                // The source leaves the triangulation unspecified: tessellate the face diameter
                // into four equal collar triangles, ending at the prescribed subject boundary.
                var collar = data.getAsJsonObject("pieroCollar");
                var face=entry.getValue().getAsJsonArray().get(0).getAsJsonObject().getAsJsonArray("coordinates");
                double left=d(face,0), span=d(face,2)/4, y=collar.get("y").getAsDouble();
                for(int n=0;n<4;n++) {
                    var p=new Path2D.Double(); p.moveTo(left+n*span,y);p.lineTo(left+(n+1)*span,y);
                    p.lineTo(left+(n+.5)*span,d(data.getAsJsonArray("subjectBounds"),3));p.closePath();
                    g.setColor(color(collar.getAsJsonArray("fills").get(n%2).getAsString()));g.fill(p);
                }
            }
            if(entry.getKey().equals("replay")) {
                // Final half of the specified cubic, exactly subdivided with de Casteljau.
                var tip=data.getAsJsonObject("replayTailTip");
                var operations=entry.getValue().getAsJsonArray();var tail=operations.get(operations.size()-1).getAsJsonObject().getAsJsonArray("coordinates");
                var m=tail.get(0).getAsJsonArray();var end=tail.get(1).getAsJsonArray();
                var curve=new CubicCurve2D.Double(d(m,1),d(m,2),d(end,1),d(end,2),d(end,3),d(end,4),d(end,5),d(end,6));
                var last=new CubicCurve2D.Double();curve.subdivide(new CubicCurve2D.Double(),last);
                g.setColor(color(tip.get("stroke").getAsString()));
                g.setStroke(new BasicStroke(tip.get("width").getAsFloat(),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(last);
            }
            g.dispose();images.put("symbols/"+entry.getKey()+".png",image);
        }
        var bar=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);var g=graphics(bar);
        for(int y:new int[]{65,112,159}) {
            var rect=new RoundRectangle2D.Double(38,y,180,42,36,36);
            g.setColor(color("#171717"));g.fill(rect);g.setColor(color("#070707"));g.setStroke(new BasicStroke(6));g.draw(rect);
            glyphs(g,"PIRI",128,y+21,3,color("#F0444C"));
        }
        g.dispose();images.put("symbols/bar.png",bar);
        for(boolean on:new boolean[]{false,true}) images.put("lamp/piri_chance_"+(on?"on":"off")+".png",lamp(on));
        return images;
    }
    private static void draw(Graphics2D g, JsonObject op) {
        var a=op.getAsJsonArray("coordinates"); Shape shape;
        switch(op.get("kind").getAsString()) {
            case "ellipse" -> shape=new Ellipse2D.Double(d(a,0),d(a,1),d(a,2),d(a,3));
            case "arc" -> shape=new Arc2D.Double(d(a,0),d(a,1),d(a,2),d(a,3),d(a,4),d(a,5),Arc2D.OPEN);
            case "line", "polygon" -> {
                var p=new Path2D.Double(); boolean first=true;
                for(var point:a) {var xy=point.getAsJsonArray(); if(first)p.moveTo(d(xy,0),d(xy,1));else p.lineTo(d(xy,0),d(xy,1));first=false;}
                if(op.get("kind").getAsString().equals("polygon"))p.closePath();shape=p;
            }
            case "path" -> {
                var p=new Path2D.Double();
                for(var command:a) { var c=command.getAsJsonArray();switch(c.get(0).getAsString()) {
                    case "M" -> p.moveTo(d(c,1),d(c,2));case "L" ->p.lineTo(d(c,1),d(c,2));
                    case "C" ->p.curveTo(d(c,1),d(c,2),d(c,3),d(c,4),d(c,5),d(c,6));case "Z" ->p.closePath();
                    default ->throw new IllegalArgumentException("Unknown path command");
                }} shape=p;
            }
            default ->throw new IllegalArgumentException("Unknown shape");
        }
        if(!op.get("fill").isJsonNull()){g.setColor(color(op.get("fill").getAsString()));g.fill(shape);}
        if(!op.get("stroke").isJsonNull()){g.setColor(color(op.get("stroke").getAsString()));g.setStroke(new BasicStroke(op.get("width").getAsFloat(),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(shape);}
    }
    private static double d(JsonArray a,int i) {return a.get(i).getAsDouble();}
    private BufferedImage lamp(boolean on) {
        var image=new BufferedImage(512,256,BufferedImage.TYPE_INT_ARGB);var g=graphics(image);
        g.setColor(ui(on?"PIRI_ON_BG":"PIRI_OFF_BG"));g.fill(new RoundRectangle2D.Double(4,4,504,248,64,64));
        g.setColor(ui("CABINET_EDGE"));g.setStroke(new BasicStroke(8));g.draw(new RoundRectangle2D.Double(4,4,504,248,64,64));
        g.setColor(ui(on?"PIRI_ON_BG":"PIRI_OFF_BG"));g.fill(new RoundRectangle2D.Double(16,16,480,224,48,48));
        var chili=new Path2D.Double();chili.moveTo(110,70);chili.curveTo(205,65,236,123,212,168);
        chili.curveTo(187,212,121,207,82,169);chili.curveTo(122,173,157,158,167,130);chili.curveTo(174,108,153,87,110,70);chili.closePath();
        g.setColor(ui(on?"PIRI_ON_CHILI":"PIRI_OFF_CHILI"));g.fill(chili);
        if(on){g.setColor(ui("PIRI_ON_EDGE"));g.setStroke(new BasicStroke(6));g.draw(chili);}
        var stem=new Path2D.Double();stem.moveTo(108,72);stem.lineTo(87,47);stem.lineTo(65,55);
        g.setColor(color("#3BAA4A"));g.setStroke(new BasicStroke(15,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(stem);
        glyphs(g,"PIRI CHANCE",256,145,5,on?ui("PIRI_ON_EDGE"):color("#7B4045"));g.dispose();return image;
    }
    public int textColumns(String text) {
        var spacing=lock.getAsJsonObject("bitmapSpacing");int columns=0;String[] words=text.split(" ");
        for(int i=0;i<words.length;i++){if(i>0)columns+=spacing.get("wordColumns").getAsInt();columns+=words[i].length()*5+(words[i].length()-1)*spacing.get("letterColumns").getAsInt();}
        return columns;
    }
    public void glyphs(Graphics2D g,String text,int cx,int cy,int scale,Color color) {
        Object previous=g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);g.setColor(color);
        int x=cx-textColumns(text)*scale/2,y=cy-7*scale/2;boolean firstWord=true;
        for(String word:text.split(" ")) {
            if(!firstWord)x+=4*scale;firstWord=false;
            for(int i=0;i<word.length();i++) {
                if(i>0)x+=scale;var rows=lock.getAsJsonObject("bitmapGlyphs").getAsJsonArray(word.substring(i,i+1));
                for(int row=0;row<7;row++)for(int col=0;col<5;col++)if(rows.get(row).getAsString().charAt(col)=='1')g.fillRect(x+col*scale,y+row*scale,scale,scale);
                x+=5*scale;
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,previous);
    }
    public static byte[] png(BufferedImage image) throws IOException {var out=new ByteArrayOutputStream();if(!ImageIO.write(image,"png",out))throw new IOException("PNG writer unavailable");return out.toByteArray();}
    public static String sha(byte[] data) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);var hashes=new TreeMap<String,String>();
        for(var entry:new AssetGenerator(root).generate().entrySet()) {
            var path=root.resolve("fabric/src/main/resources/assets/piri/textures/"+entry.getKey());Files.createDirectories(path.getParent());
            var bytes=png(entry.getValue());Files.write(path,bytes);hashes.put(entry.getKey(),sha(bytes));
        }
        Path manifest=root.resolve("asset-tools/src/test/resources/image-sha256.json");Files.createDirectories(manifest.getParent());
        Files.writeString(manifest,new GsonBuilder().setPrettyPrinting().create().toJson(hashes)+"\n");
    }
}
