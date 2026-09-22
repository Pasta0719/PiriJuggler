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
        var chainGames=new ArrayList<Long>();
        boolean hasGodHistory;
        try(PreparedStatement ps=connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name='juggler_god_history'")){
            try(ResultSet rs=ps.executeQuery()){hasGodHistory=rs.next();}
        }
        String historySql=hasGodHistory
                ?"SELECT type,games,occurred_at FROM ("+
                    "SELECT bonus_type AS type,games,occurred_at,id*2 AS ord FROM bonus_history WHERE machine_id=? AND business_period_id=? UNION ALL "+
                    "SELECT event_type AS type,games,occurred_at,id*2+1 AS ord FROM juggler_god_history WHERE machine_id=? AND business_period_id=?"+
                    ") ORDER BY occurred_at DESC,ord DESC LIMIT 10"
                :"SELECT bonus_type AS type,games,occurred_at FROM bonus_history WHERE machine_id=? AND business_period_id=? ORDER BY id DESC LIMIT 10";
        try(PreparedStatement ps=connection.prepareStatement(historySql)){
            ps.setInt(1,machineId);ps.setString(2,businessPeriodId);
            if(hasGodHistory){ps.setInt(3,machineId);ps.setString(4,businessPeriodId);}
            try(ResultSet rs=ps.executeQuery()){
                while(rs.next()){
                    JsonObject item=new JsonObject();item.addProperty("type",rs.getString(1));item.addProperty("games",rs.getLong(2));item.addProperty("occurredAt",rs.getLong(3));history.add(item);
                }
            }
        }
        try(PreparedStatement ps=connection.prepareStatement("SELECT games FROM bonus_history WHERE machine_id=? AND business_period_id=? ORDER BY id DESC")){
            ps.setInt(1,machineId);ps.setString(2,businessPeriodId);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())chainGames.add(rs.getLong(1));}
        }

        List<Lttb.Point> raw=new ArrayList<>();
        boolean completedBonus=false;Long previousGame=null;
        try(PreparedStatement ps=connection.prepareStatement("SELECT game,difference FROM graph_points WHERE machine_id=? AND business_period_id=? ORDER BY id ASC")){
            ps.setInt(1,machineId);ps.setString(2,businessPeriodId);
            try(ResultSet rs=ps.executeQuery()){
                while(rs.next()){
                    long game=rs.getLong(1),difference=rs.getLong(2);
                    if(previousGame!=null&&previousGame==game)completedBonus=true;
                    previousGame=game;
                    // game=0 is the period-initialization baseline. The visible graph is explicitly 1G through the current game.
                    if(game>=1&&game<=totalGames)raw.add(new Lttb.Point(game,difference));
                }
            }
        }
        // LTTB only reduces rendering density. It keeps the first/last points and therefore the whole 1G..current range.
        List<Lttb.Point> display=Lttb.downsample(raw,300);
        JsonArray graph=new JsonArray();
        for(Lttb.Point point:display){JsonObject item=new JsonObject();item.addProperty("game",point.x());item.addProperty("difference",point.y());graph.add(item);}

        boolean piriChain=completedBonus&&currentGames<=100;
        int piriChainCount=0;
        if(piriChain&&!chainGames.isEmpty()){
            piriChainCount=1;
            for(int i=0;i+1<chainGames.size()&&chainGames.get(i)<=100;i++)piriChainCount++;
        }

        JsonObject result=new JsonObject();
        result.addProperty("machineId",machineId);
        result.addProperty("totalGames",totalGames);
        result.addProperty("bigCount",bigCount);
        result.addProperty("regCount",regCount);
        result.addProperty("currentGames",currentGames);
        result.addProperty("todayDifference",todayDifference);
        result.addProperty("todayMaxDifference",todayMaxDifference);
        result.addProperty("piriChain",piriChain);
        result.addProperty("piriChainCount",piriChainCount);
        result.add("history",history);
        result.add("graph",graph);
        return result;
    }

    private DataLampSnapshot() {}
}
