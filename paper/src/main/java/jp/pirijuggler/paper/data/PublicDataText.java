package jp.pirijuggler.paper.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Pure formatter for public current-period machine data shown in chat. */
public final class PublicDataText {
    public static String summary(JsonObject data) {
        if(isHyper(data)){
            long total=value(data,"totalGames"),rush=value(data,"rushCount"),hyper=value(data,"hyperCount");
            return "#"+value(data,"machineId")+"  "+total+"G  HYPER "+hyper+" ("+probability(total,hyper)+")"+
                    "  RUSH "+rush+" ("+probability(total,rush)+")  ALL "+probability(total,rush+hyper)+
                    "  DIFF "+signed(value(data,"todayDifference"));
        }
        long total=value(data,"totalGames"),big=value(data,"bigCount"),reg=value(data,"regCount"),diff=value(data,"todayDifference");
        return "#"+value(data,"machineId")+"  "+total+"G  BIG "+big+" ("+probability(total,big)+")  REG "+reg+" ("+probability(total,reg)+")  ALL "+probability(total,big+reg)+"  DIFF "+signed(diff);
    }

    public static List<String> detail(JsonObject data) {
        if(isHyper(data))return hyperDetail(data);
        long total=value(data,"totalGames"),current=value(data,"currentGames"),big=value(data,"bigCount"),reg=value(data,"regCount");
        List<String> lines=new ArrayList<>();
        lines.add("--- MACHINE #"+value(data,"machineId")+" ---");
        lines.add("TOTAL "+total+"G / CURRENT "+current+"G");
        lines.add("BIG "+big+" ("+probability(total,big)+") / REG "+reg+" ("+probability(total,reg)+") / ALL "+probability(total,big+reg));
        lines.add("DIFF "+signed(value(data,"todayDifference"))+" / MAX "+signed(value(data,"todayMaxDifference")));
        if(data.has("piriChain")&&data.get("piriChain").getAsBoolean())lines.add("PIRI CHAIN x"+Math.max(1,(int)value(data,"piriChainCount")));
        lines.add("BONUS HISTORY (NEWEST)");
        JsonArray history=data.has("history")?data.getAsJsonArray("history"):new JsonArray();
        if(history.isEmpty())lines.add("-- no bonus yet --");
        else for(int i=0;i<Math.min(10,history.size());i++){
            JsonObject item=history.get(i).getAsJsonObject();
            lines.add(item.get("type").getAsString()+"  "+item.get("games").getAsLong()+"G");
        }
        return lines;
    }

    private static boolean isHyper(JsonObject data){
        return data.has("machineType")&&
                "HYPER_DOG".equals(data.get("machineType").getAsString());
    }
    private static List<String> hyperDetail(JsonObject data){
        long total=value(data,"totalGames"),current=value(data,"currentGames");
        long rush=value(data,"rushCount"),hyper=value(data,"hyperCount");
        List<String> lines=new ArrayList<>();
        lines.add("--- MACHINE #"+value(data,"machineId")+" ---");
        lines.add("TOTAL "+total+"G / CURRENT "+current+"G");
        // Existing BIG/REG positions are HYPER/RUSH only for this machine family.
        lines.add("HYPER "+hyper+" ("+probability(total,hyper)+") / RUSH "+rush+
                " ("+probability(total,rush)+") / ALL "+probability(total,rush+hyper));
        lines.add("DIFF "+signed(value(data,"todayDifference"))+
                " / MAX "+signed(value(data,"todayMaxDifference")));
        lines.add("RUSH / CZ / HYPER HISTORY (NEWEST)");
        JsonArray history=data.has("history")?data.getAsJsonArray("history"):new JsonArray();
        if(history.isEmpty())lines.add("-- no history yet --");
        else for(int i=0;i<Math.min(10,history.size());i++){
            JsonObject item=history.get(i).getAsJsonObject();
            String type=item.get("type").getAsString();
            String games=item.has("games")?" "+item.get("games").getAsLong()+"G":"";
            String medals=item.has("medals")?" "+signed(item.get("medals").getAsLong()):"";
            lines.add(type+games+medals);
        }
        return lines;
    }
    static String probability(long games,long hits) {
        if(games<=0||hits<=0)return "---";
        return "1/"+Math.max(1,Math.round(games/(double)hits));
    }
    private static long value(JsonObject data,String key) { return data.has(key)?data.get(key).getAsLong():0; }
    private static String signed(long value) { return value>0?"+"+value:Long.toString(value); }
    private PublicDataText() {}
}
