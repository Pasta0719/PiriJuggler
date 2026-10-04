package jp.pirijuggler.fabric.ui;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class UiConstants {
    private static final JsonObject DATA;
    static {try(var in=Objects.requireNonNull(UiConstants.class.getResourceAsStream("/assets/piri/client-ui.json"))){DATA=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    public static int color(String name){return 0xff000000|Integer.parseInt(DATA.getAsJsonObject("colors").get(name).getAsString().substring(1),16);}
    /** SKILL STOP-only gothic cabinet palette; other machines retain configured colors. */
    public static int machineColor(String machineType,String key){
        if(!"SKILL_STOP".equals(machineType))return color(key);
        return switch(key){
            case "CABINET_BG" -> 0xff111015;
            case "CABINET_EDGE" -> 0xffa68139;
            case "CABINET_EDGE_LIGHT" -> 0xffd9b76b;
            case "REEL_SEPARATOR" -> 0xffa68139;
            case "REEL_BG" -> 0xff1c1017;
            case "DISPLAY_BG" -> 0xff200e15;
            case "DISPLAY_GREEN" -> 0xffd9b76b;
            case "BUTTON_RED" -> 0xff852332;
            default -> color(key);
        };
    }
    public static String symbol(int reel,int index){return jp.pirijuggler.common.reel.FixedReels.at(jp.pirijuggler.common.reel.Reel.values()[reel],index).name().toLowerCase(Locale.ROOT);}
    public static String symbol(String machineType,int reel,int index){return "SKILL_STOP".equals(machineType)?jp.pirijuggler.common.reel.SkillStopReels.at(jp.pirijuggler.common.reel.Reel.values()[reel],index).name().toLowerCase(Locale.ROOT):symbol(reel,index);}
    public static int brighter(int color){return color&0xff000000|Math.min(255,(int)((color>>16&255)*1.15))<<16|Math.min(255,(int)((color>>8&255)*1.15))<<8|Math.min(255,(int)((color&255)*1.15));}
    private UiConstants(){}
}
