package jp.pirijuggler.paper.data;

import jp.pirijuggler.paper.database.PiriDatabase;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-only current-period machine data intended for public chat display. */
public final class PublicMachineData {
    public record Bonus(String type,long games) {}
    public record Snapshot(int machineId,long totalGames,long currentGames,long bigCount,long regCount,long difference,long maxDifference,List<Bonus> history) {
        public Snapshot {
            history=List.copyOf(history);
        }
        public String summaryLine() {
            return "#"+machineId+"  "+totalGames+"G  BIG "+bigCount+" ("+probability(totalGames,bigCount)+")  REG "+regCount+" ("+probability(totalGames,regCount)+")  ALL "+probability(totalGames,bigCount+regCount)+"  DIFF "+signed(difference);
        }
        public List<String> detailLines() {
            List<String> lines=new ArrayList<>();
            lines.add("--- MACHINE #"+machineId+" ---");
            lines.add("TOTAL "+totalGames+"G / CURRENT "+currentGames+"G");
            lines.add("BIG "+bigCount+" ("+probability(totalGames,bigCount)+") / REG "+regCount+" ("+probability(totalGames,regCount)+") / ALL "+probability(totalGames,bigCount+regCount));
            lines.add("DIFF "+signed(difference)+" / MAX "+signed(maxDifference));
            lines.add("BONUS HISTORY (NEWEST)");
            if(history.isEmpty()) lines.add("-- no bonus yet --");
            else for(Bonus bonus:history) lines.add(bonus.type()+"  "+bonus.games()+"G");
            return lines;
        }
    }

    public static Snapshot read(PiriDatabase database,int machineId,String businessPeriodId) throws SQLException {
        if(database==null||businessPeriodId==null||businessPeriodId.isBlank())throw new IllegalArgumentException("snapshot args");
        List<Map<String,Object>> stats=database.rows("SELECT total_games,current_games,big_count,reg_count,today_difference,today_max_difference FROM machine_period_stats WHERE machine_id=? AND business_period_id=?",machineId,businessPeriodId);
        if(stats.isEmpty())throw new SQLException("Missing machine_period_stats for current period");
        Map<String,Object> row=stats.getFirst();
        List<Bonus> history=new ArrayList<>();
        for(Map<String,Object> item:database.rows("SELECT bonus_type,games FROM bonus_history WHERE machine_id=? AND business_period_id=? ORDER BY id DESC LIMIT 10",machineId,businessPeriodId))
            history.add(new Bonus((String)item.get("bonus_type"),number(item,"games")));
        return new Snapshot(machineId,number(row,"total_games"),number(row,"current_games"),number(row,"big_count"),number(row,"reg_count"),number(row,"today_difference"),number(row,"today_max_difference"),history);
    }

    public static String probability(long games,long hits) {
        if(games<=0||hits<=0)return "---";
        return "1/"+Math.max(1,Math.round(games/(double)hits));
    }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static String signed(long value) { return value>0?"+"+value:Long.toString(value); }
    private PublicMachineData() {}
}
