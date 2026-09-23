package jp.pirijuggler.fabric.ui;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UiResourcesTest {
    private static final Path ROOT=Path.of(System.getProperty("piri.specRoot"));
    @Test void canvasPreservesAspectAndUsesExactHitboxes(){
        var wide=SlotLayout.Viewport.fit(2560,1080);assertEquals(1,wide.scale());assertEquals(320,wide.x());assertEquals(0,wide.y());assertEquals(720,wide.logicalX(1040));
        var small=SlotLayout.Viewport.fit(960,540);assertEquals(.5,small.scale());assertEquals(0,small.x());assertEquals(0,small.y());
        assertEquals(new SlotLayout.Rect(335,150,135,195),small.clip(new SlotLayout.Rect(670,300,270,390)));
        assertEquals(new SlotLayout.Rect(670,300,900,390),SlotLayout.REELS);assertEquals(new SlotLayout.Rect(350,390,300,170),SlotLayout.LAMP);
        for(var c:SlotLayout.CONTROLS){assertTrue(c.rect().contains(c.rect().x(),c.rect().y()));assertFalse(c.rect().contains(c.rect().x()+c.rect().w(),c.rect().y()));}
    }
    @Test void clientReelsAndColorsMatchSpecAndAllSoundIdsAcceptOptionalFiles() throws Exception {
        var lock=JsonParser.parseString(Files.readString(ROOT.resolve("docs/spec-lock.json"))).getAsJsonObject();
        var themeOverrides=Map.of(
            "CABINET_BG","#3C0A10",
            "CABINET_BG_DARK","#220508",
            "BUTTON_RED","#C92734",
            "BUTTON_RED_HOVER","#E64350",
            "BUTTON_RED_PRESSED","#8A1720",
            "PIRI_OFF_BG","#180407",
            "PIRI_OFF_CHILI","#5B1118",
            "PIRI_ON_BG","#2A060A",
            "PIRI_ON_CHILI","#FF3044"
        );
        for(var entry:lock.getAsJsonObject("uiColors").entrySet()){
            String expected=themeOverrides.getOrDefault(entry.getKey(),entry.getValue().getAsString());
            assertEquals(0xff000000|Integer.parseInt(expected.substring(1),16),UiConstants.color(entry.getKey()));
        }
        for(int reel=0;reel<3;reel++)for(int i=0;i<21;i++)assertEquals(lock.getAsJsonObject("reelArrays").getAsJsonArray(new String[]{"LEFT_REEL","CENTER_REEL","RIGHT_REEL"}[reel]).get(i).getAsString().toLowerCase(Locale.ROOT),UiConstants.symbol(reel,i));
        var registry=JsonParser.parseString(Files.readString(ROOT.resolve("fabric/src/main/resources/assets/piri/sounds.json"))).getAsJsonObject();
        var base=List.of("notice","notice_strong","tenpai","bet","lever","stop","payout","error","bonus_start","bonus_end","big_bgm","reg_bgm");
        var ids=new LinkedHashSet<String>(base);
        for(String id:base)ids.add("juggler_god_"+id);
        ids.add("god_freeze");
        ids.addAll(List.of(
                "juggler_god_god_freeze",
                "juggler_god_god_stop_1",
                "juggler_god_god_stop_2",
                "juggler_god_god_stop_3",
                "juggler_god_god_bonus_start",
                "juggler_god_god_big_bgm"
        ));
        assertEquals(ids,registry.keySet());
        for(String id:ids){
            JsonElement sound=registry.getAsJsonObject(id).getAsJsonArray("sounds").get(0);
            if(id.endsWith("big_bgm")||id.endsWith("reg_bgm")){
                assertTrue(sound.isJsonObject());
                assertEquals("piri:"+id,sound.getAsJsonObject().get("name").getAsString());
                assertTrue(sound.getAsJsonObject().get("stream").getAsBoolean());
            }else assertEquals("piri:"+id,sound.getAsString());
        }
        // Sound files are deliberately optional: verify the registry contract, never a waveform/hash.
    }
    @Test void jugglerGodPresentationRoutesThroughDedicatedReplaceableNamespace() throws Exception {
        String resolver=Files.readString(ROOT.resolve("fabric/src/main/java/jp/pirijuggler/fabric/ui/JugglerGodAssets.java"));
        assertTrue(resolver.contains("\"textures/juggler_god/\" + ordinaryPath"));
        assertTrue(resolver.contains("? dedicated : ordinary"));
        String slot=Files.readString(ROOT.resolve("fabric/src/main/java/jp/pirijuggler/fabric/ui/SlotScreen.java"));
        String world=Files.readString(ROOT.resolve("fabric/src/main/java/jp/pirijuggler/fabric/render/WorldCabinetRenderer.java"));
        assertTrue(slot.contains("JugglerGodAssets.texture(view.machineType(),path)"));
        assertTrue(world.contains("JugglerGodAssets.texture(state.machineType()"));
        String sounds=Files.readString(ROOT.resolve("fabric/src/main/java/jp/pirijuggler/fabric/ui/PiriSounds.java"));
        assertTrue(sounds.contains("\"JUGGLER_GOD\".equals(machineType)"));
        assertTrue(sounds.contains("\"juggler_god_\"+base"));
        assertTrue(sounds.contains("if(available(dedicated))return dedicated"));
        String ui=Files.readString(ROOT.resolve("fabric/src/main/java/jp/pirijuggler/fabric/ui/SlotUi.java"));
        assertTrue(ui.contains("juggler_god_god_freeze"));
        assertTrue(ui.contains("juggler_god_god_stop_"));
        assertTrue(ui.contains("juggler_god_god_bonus_start"));
        assertTrue(ui.contains("juggler_god_god_big_bgm"));
    }
    @Test void sevenSegmentGeometryAndInactiveAlphaContract(){assertEquals(7,SevenSegment.RECTANGLES.length);assertArrayEquals(new int[]{8,32,24,8},SevenSegment.RECTANGLES[6]);for(int n=0;n<10;n++)for(int s=0;s<7;s++)assertEquals(SevenSegment.DIGITS[n].indexOf('A'+s)>=0,SevenSegment.active((char)('0'+n),s));assertTrue(SevenSegment.active('-',6));}
}
