package jp.pirijuggler.paper.game;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JugglerGodStockOddsTest extends GameFixture {
    @Test void allTwelveProfilesKeepLegacyStockOddsAndNearOriginalHotStrength() {
        var weights=new RoleWeights(config);
        int[][] normal={
            {0,270000,267000,270500,270500,268000,255000},
            {0,198000,192500,194000,197000,199500,187000}
        };
        for(int profile=0;profile<2;profile++){
            String type=profile==0?"juggler_god":"juggler_god_extreme";
            for(int setting=1;setting<=6;setting++){
                @SuppressWarnings("unchecked")
                Map<String,Object> settings=(Map<String,Object>)((Map<String,Object>)config.get(type)).get("settings");
                @SuppressWarnings("unchecked")
                Map<String,Object> row=(Map<String,Object>)settings.get(Integer.toString(setting));
                int base=((Number)row.get("bonus_scale_ppm")).intValue();
                int stock=((Number)row.get("bonus_stock_scale_ppm")).intValue();
                assertEquals(normal[profile][setting],base);
                assertEquals(JugglerGodOdds.defaultStockScale(type,setting),stock);
                assertTrue(stock>base,"stock must be independent and stronger");
                long raw=weights.unscaledBonusFamilyWeight(setting);
                int high=JugglerGodOdds.hotScale(base,JugglerGodOdds.referenceBase(type,setting),raw,false);
                int ultra=JugglerGodOdds.hotScale(base,JugglerGodOdds.referenceBase(type,setting),raw,true);
                double highPerGame=raw*high/1_000_000_000_000_000.0;
                double ultraPerGame=raw*ultra/1_000_000_000_000_000.0;
                double high20=1-Math.pow(1-highPerGame,20);
                double ultra15=1-Math.pow(1-ultraPerGame,15);
                assertTrue(high20>=0.38-0.00001,type+" setting "+setting+" HIGH "+high20);
                assertTrue(ultra15>=0.68-0.00001,type+" setting "+setting+" ULTRA "+ultra15);
                assertTrue(high20<=0.41,type+" HIGH must not be overpowered");
                assertTrue(ultra15<=0.71,type+" ULTRA must not be overpowered");
            }
        }
    }

    @Test void loweringNormalBaseNeverLowersHotBelowFloor() {
        for(String type:new String[]{"juggler_god","juggler_god_extreme"}){
            for(int setting=1;setting<=6;setting++){
                int ref=JugglerGodOdds.referenceBase(type,setting);
                long raw=new RoleWeights(config).unscaledBonusFamilyWeight(setting);
                int high=JugglerGodOdds.hotScale(0,ref,raw,false);
                int ultra=JugglerGodOdds.hotScale(0,ref,raw,true);
                assertTrue(1-Math.pow(1-raw*high/1e15,20)>=.38-.00001);
                assertTrue(1-Math.pow(1-raw*ultra/1e15,15)>=.68-.00001);
            }
        }
    }
}
