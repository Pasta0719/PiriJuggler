package jp.pirijuggler.paper.game;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JugglerGodSettingHintTest {
    @Test void missesAtTheHundredthThirdStopLightsAndTheNextLeverClears() {
        Random rng=new Random(5);
        var before=new JugglerGodSettingHint.State(7,99,"OFF");
        var spinning=JugglerGodSettingHint.advance(before,true,false,false,false,false,1,false,rng);
        assertEquals(100,spinning.games());
        assertEquals("OFF",spinning.color());
        var completed=JugglerGodSettingHint.advance(spinning,false,true,false,false,false,1,false,rng);
        assertEquals(0,completed.chain());
        assertEquals("PURPLE",completed.color());
        // Between 100G's third stop and 101G's lever the lamp is unchanged.
        assertEquals("PURPLE",completed.color());
        var next=JugglerGodSettingHint.advance(completed,true,false,false,false,false,1,false,rng);
        assertEquals("OFF",next.color());
    }

    @Test void hittingExactlyAt100GContinuesTheExistingChain() {
        Random rng=new Random(2);
        var before=new JugglerGodSettingHint.State(6,99,"OFF");
        var spinning=JugglerGodSettingHint.advance(before,true,false,false,false,false,2,false,rng);
        var hit=JugglerGodSettingHint.advance(spinning,false,true,true,false,false,2,false,rng);
        assertEquals(6,hit.chain());
        assertEquals(100,hit.games());
        assertEquals("OFF",hit.color());
        // The internally earned bonus can start on an entry reel later.
        var started=JugglerGodSettingHint.advance(hit,false,false,false,true,false,2,false,rng);
        assertEquals(7,started.chain());
        var bonusOver=JugglerGodSettingHint.advance(started,false,false,false,false,true,2,false,rng);
        assertEquals(0,bonusOver.games());
        assertEquals(7,bonusOver.chain());
    }

    @Test void ninetyNineMissesMustNotLight() {
        var s=JugglerGodSettingHint.advance(new JugglerGodSettingHint.State(8,98,"OFF"),
                true,true,false,false,false,6,true,new Random(0));
        assertEquals(99,s.games());
        assertEquals("OFF",s.color());
        assertEquals(8,s.chain());
    }

    @Test void sevenPlusCannotShowOffOrRegularAndSettingRangesAreAbsolute() {
        for(int setting=1;setting<=6;setting++)for(boolean extreme:new boolean[]{false,true}){
            Random rng=new Random(setting+(extreme?80:0));
            for(int i=0;i<3000;i++){
                String c=JugglerGodSettingHint.sample(setting,extreme,7,rng);
                assertNotEquals("OFF",c);
                assertTrue(Set.of("PURPLE","RED","GOLD","RAINBOW").contains(c));
                if("PURPLE".equals(c))assertTrue(setting<=4);
                if("RED".equals(c))assertTrue(setting>=3&&setting<=5);
                if("GOLD".equals(c))assertTrue(setting>=4);
                if("RAINBOW".equals(c))assertEquals(6,setting);
            }
        }
    }

    @Test void regularColorsAllOccurAtAllSettingsAndOffChanceExists() {
        for(int setting=1;setting<=6;setting++)for(boolean extreme:new boolean[]{false,true}){
            Set<String> appeared=new HashSet<>();
            Random rng=new Random(setting+(extreme?20:0));
            for(int i=0;i<1000;i++)appeared.add(JugglerGodSettingHint.sample(setting,extreme,4,rng));
            assertEquals(Set.of("OFF","WHITE","BLUE","GREEN","YELLOW"),appeared);
        }
    }

    @Test void persistedHintRestoresWithoutExposingPrivateFields() {
        JsonObject saved=new JsonObject();
        var s=new JugglerGodSettingHint.State(9,99,"RED");
        JugglerGodSettingHint.write(saved,s);
        assertEquals(s,JugglerGodSettingHint.read(saved));
        assertEquals("RED",saved.get("settingHintColor").getAsString());
        assertEquals(new JugglerGodSettingHint.State(0,0,"OFF"),JugglerGodSettingHint.read(new JsonObject()));
    }
}
