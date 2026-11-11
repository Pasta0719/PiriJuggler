package jp.pirijuggler.paper.data;

import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import jp.pirijuggler.paper.game.hyperdog.HyperDogEventJournal;
import jp.pirijuggler.paper.game.hyperdog.HyperDogRunJournal;

/**
 * Data-lamp projection for HYPER_DOG only. BIG/REG are never used for AT,
 * and existing machine-family lamp projections are unchanged.
 */
public final class HyperDogDataLamp {
    public static JsonObject read(Connection connection,int machineId,String period)
            throws SQLException {
        JsonObject base=DataLampSnapshot.read(connection,machineId,period);
        JsonObject events=HyperDogEventJournal.read(connection,machineId,period);
        for(Map.Entry<String,com.google.gson.JsonElement> entry:events.entrySet())
            base.add(entry.getKey(),entry.getValue().deepCopy());
        JsonObject unified=HyperDogRunJournal.read(connection,machineId,period);
        for(Map.Entry<String,com.google.gson.JsonElement> entry:unified.entrySet())
            base.add(entry.getKey(),entry.getValue().deepCopy());
        // PIRI CHAIN is legacy-only: HYPER_DOG exposes its own shot counter.
        base.addProperty("piriChain",false);
        base.addProperty("atCount",events.get("atFirstHits").getAsLong());
        return base;
    }
    private HyperDogDataLamp(){}
}
