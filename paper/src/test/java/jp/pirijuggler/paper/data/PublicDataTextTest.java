package jp.pirijuggler.paper.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PublicDataTextTest {
    @Test void summaryAndDetailUseCurrentPeriodStatsWithoutExposingSetting() {
        JsonObject data=new JsonObject();
        data.addProperty("machineId",2);
        data.addProperty("totalGames",1260);
        data.addProperty("currentGames",38);
        data.addProperty("bigCount",7);
        data.addProperty("regCount",6);
        data.addProperty("todayDifference",812);
        data.addProperty("todayMaxDifference",1044);
        data.addProperty("piriChain",true);
        data.addProperty("piriChainCount",3);
        JsonArray history=new JsonArray();
        history.add(bonus("BIG",38));
        history.add(bonus("REG",91));
        data.add("history",history);

        String summary=PublicDataText.summary(data);
        assertEquals("#2  1260G  BIG 7 (1/180)  REG 6 (1/210)  ALL 1/97  DIFF +812",summary);
        assertFalse(summary.toLowerCase().contains("setting"));

        List<String> detail=PublicDataText.detail(data);
        assertEquals("--- MACHINE #2 ---",detail.get(0));
        assertTrue(detail.contains("PIRI CHAIN x3"));
        assertTrue(detail.contains("BIG  38G"));
        assertTrue(detail.contains("REG  91G"));
        assertTrue(detail.stream().noneMatch(line->line.toLowerCase().contains("setting")));
    }

    @Test void zeroHitsRenderAsDashes() {
        assertEquals("---",PublicDataText.probability(500,0));
        assertEquals("---",PublicDataText.probability(0,0));
    }

    private static JsonObject bonus(String type,long games){
        JsonObject item=new JsonObject();item.addProperty("type",type);item.addProperty("games",games);return item;
    }
}
