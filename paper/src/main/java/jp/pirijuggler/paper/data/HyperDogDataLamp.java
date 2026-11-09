package jp.pirijuggler.paper.data;

import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import jp.pirijuggler.paper.game.hyperdog.HyperDogEventJournal;

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
        base.addProperty("atCount",events.get("atFirstHits").getAsLong());
        return base;
    }
    private HyperDogDataLamp(){}
}
