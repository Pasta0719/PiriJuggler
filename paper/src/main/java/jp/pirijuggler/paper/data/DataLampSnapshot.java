package jp.pirijuggler.paper.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read-only Phase09 data-lamp projection. Every query is scoped to one business period. */
public final class DataLampSnapshot {
    public static JsonObject read(Connection connection,int machineId,String businessPeriodId) throws SQLException {
        if(connection==null||businessPeriodId==null||businessPeriodId.isBlank())throw new IllegalArgumentException("snapshot args");
        long totalGames,bigCount,regCount,currentGames,todayDifference,todayMaxDifference;
        try(PreparedStatement ps=connection.prepareStatement("SELECT total_games,big_count,reg_count,current_games,today_difference,today_max_difference FROM machine_period_stats WHERE machine_id=? AND business_period_id=?")){
            ps.setInt(1,machineId);ps.setString(2,businessPeriodId);
            try(ResultSet rs=ps.executeQuery()){
                if(!rs.next())throw new SQLException("Missing machine_period_stats for current period");
                totalGames=rs.getLong(1);bigCount=rs.getLong(2);regCount=rs.getLong(3);currentGames=rs.getLong(4);todayDifference=rs.getLong(5);todayMaxDifference=rs.getLong(6);
            }
        }

        JsonArray history=new JsonArray();
        try(PreparedStatement ps=connection.prepareStatement("SELECT bonus_type,games,occurred_at FROM bonus_history WHERE machine_id=? AND business_period_id=? ORDER BY id DESC LIMIT 10")){
            ps.setInt(1,machineId);ps.setString(2,businessPeriodId);
            try(ResultSet rs=ps.executeQuery()){
                while(rs.next()){
                    JsonObject item=new JsonObject();item.addProperty("type",rs.getString(1));item.addProperty("games",rs.getLong(2));item.addProperty("occurredAt",rs.getLong(3));history.add(item);
                }
            }
        }

        List<Lttb.Point> raw=new ArrayList<>();
        boolean completedBonus=false;Long previousGame=null;
        try(PreparedStatement ps=connection.prepareStatement("SELECT game,difference FROM graph_points WHERE machine_id=? AND business_period_id=? ORDER BY id ASC")){
            ps.setInt(1,machineId);ps.setString(2,businessPeriodId);
            try(ResultSet rs=ps.executeQuery()){
                while(rs.next()){
                    long game=rs.getLong(1),difference=rs.getLong(2);
                    if(previousGame!=null&&previousGame==game)completedBonus=true;
                    previousGame=game;raw.add(new Lttb.Point(game,difference));
                }
            }
        }
        List<Lttb.Point> display=Lttb.downsample(raw,300);
        JsonArray graph=new JsonArray();
        for(Lttb.Point point:display){JsonObject item=new JsonObject();item.addProperty("game",point.x());item.addProperty("difference",point.y());graph.add(item);}

        JsonObject result=new JsonObject();
        result.addProperty("machineId",machineId);
        result.addProperty("totalGames",totalGames);
        result.addProperty("bigCount",bigCount);
        result.addProperty("regCount",regCount);
        result.addProperty("currentGames",currentGames);
        result.addProperty("todayDifference",todayDifference);
        result.addProperty("todayMaxDifference",todayMaxDifference);
        result.addProperty("piriChain",completedBonus&&currentGames<=100);
        result.add("history",history);
        result.add("graph",graph);
        return result;
    }

    private DataLampSnapshot() {}
}
