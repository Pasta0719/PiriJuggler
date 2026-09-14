package jp.pirijuggler.fabric.ui;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class UiConstants {
    private static final JsonObject DATA;
    static {try(var in=Objects.requireNonNull(UiConstants.class.getResourceAsStream("/assets/piri/client-ui.json"))){DATA=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    public static int color(String name){return 0xff000000|Integer.parseInt(DATA.getAsJsonObject("colors").get(name).getAsString().substring(1),16);}
    public static String symbol(int reel,int index){return DATA.getAsJsonObject("reels").getAsJsonArray(new String[]{"LEFT_REEL","CENTER_REEL","RIGHT_REEL"}[reel]).get(Math.floorMod(index,21)).getAsString().toLowerCase(Locale.ROOT);}
    public static int brighter(int color){return color&0xff000000|Math.min(255,(int)((color>>16&255)*1.15))<<16|Math.min(255,(int)((color>>8&255)*1.15))<<8|Math.min(255,(int)((color&255)*1.15));}
    private UiConstants(){}
}
