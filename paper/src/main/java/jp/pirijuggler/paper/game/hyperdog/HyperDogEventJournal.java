package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.session.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Machine-only, period-scoped HYPER_DOG history. Never synthesizes BIG/REG. */
public final class HyperDogEventJournal {
    public enum Type { AT_FIRST_HIT, UPPER_ENTRY, CZ_CHALLENGE, CZ_SUCCESS, CZ_FAILURE,
        BAR, AT_END, COMEBACK }
    public record Event(Type type,long medals) {}
    private static final String TABLE="hyper_dog_events";

    /** Created only inside an HYPER_DOG owner transaction; no legacy table changes. */
    public static void ensure(PiriDatabase db) throws SQLException {
        db.sql("CREATE TABLE IF NOT EXISTS "+TABLE+" ("+
                "machine_id INTEGER NOT NULL, business_period_id TEXT NOT NULL, "+
                "event_id TEXT NOT NULL, event_type TEXT NOT NULL, medals INTEGER NOT NULL, "+
                "occurred_at INTEGER NOT NULL, PRIMARY KEY(machine_id,event_id))");
    }

    /** No RNG, no player balance mutation and no second transaction. */
    public static void append(PiriDatabase db,Session owner,HyperDogFullGame before,
                              HyperDogFullGame after,long sequence,long now) throws SQLException {
        Objects.requireNonNull(db);Objects.requireNonNull(owner);
        Objects.requireNonNull(after);
        List<Event> events=classify(before,after);
        if(events.isEmpty())return;
        String period=owner.text("source_business_period_id");
        if(period==null||period.isBlank())throw new IllegalArgumentException("business period");
        ensure(db);
        for(Event event:events){
            String eventId=owner.id()+":"+sequence+":"+event.type().name();
            db.sql("INSERT OR IGNORE INTO "+TABLE+
                    "(machine_id,business_period_id,event_id,event_type,medals,occurred_at) "+
                    "VALUES(?,?,?,?,?,?)",owner.machine(),period,eventId,
                    event.type().name(),event.medals(),now);
        }
    }

    static List<Event> classify(HyperDogFullGame before,HyperDogFullGame after){
        if(before==null)return List.of();
        List<Event> out=new ArrayList<>();
        var oldAt=before.at();
        var newAt=after.at();
        var oldStage=oldAt==null?null:oldAt.stage();
        var newStage=newAt==null?null:newAt.stage();
        if(oldAt==null&&newAt!=null){
            out.add(new Event(Type.AT_FIRST_HIT,0));
            if(before.comebackPaidLeft()>0)out.add(new Event(Type.COMEBACK,0));
        }
        if((oldStage!=HyperDogAtGame.Stage.UPPER_INITIAL)&&
                newStage==HyperDogAtGame.Stage.UPPER_INITIAL)
            out.add(new Event(Type.UPPER_ENTRY,0));
        if(oldStage!=HyperDogAtGame.Stage.UPPER_CZ&&
                newStage==HyperDogAtGame.Stage.UPPER_CZ)
            out.add(new Event(Type.CZ_CHALLENGE,0));
        if(oldStage==HyperDogAtGame.Stage.UPPER_CZ&&newStage!=oldStage){
            if(newStage==HyperDogAtGame.Stage.UPPER_INITIAL)
                out.add(new Event(Type.CZ_SUCCESS,0));
            else if(newStage==HyperDogAtGame.Stage.LOWER_INITIAL||newAt==null)
                out.add(new Event(Type.CZ_FAILURE,0));
        }
        if(oldAt==null){
            var pending=before.normal().pending();
            if(pending!=null&&pending.role()==HyperDogNormalGame.Role.BAR&&
                    after.normal().pending()==null)
                out.add(new Event(Type.BAR,0));
        }else{
            var pending=oldAt.pending();
            if(pending!=null&&pending.role()==HyperDogAtGame.Role.BAR&&
                    (newAt==null||newAt.pending()==null))
                out.add(new Event(Type.BAR,0));
        }
        if(oldAt!=null&&newAt==null)
            out.add(new Event(Type.AT_END,after.lastAtNet()));
        return List.copyOf(out);
    }

    public static boolean exists(Connection c)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")){
            ps.setString(1,TABLE);
            try(ResultSet rs=ps.executeQuery()){return rs.next();}
        }
    }

    /** Read-only remote/admin projection; no private modes, settings, stock or RNG. */
    public static JsonObject read(Connection connection,int machineId,String period)throws SQLException{
        if(connection==null||machineId<1||period==null||period.isBlank())
            throw new IllegalArgumentException("history args");
        JsonObject out=new JsonObject();
        JsonArray history=new JsonArray();
        long atCount=0,upperCount=0,czAttempts=0,czSuccess=0,czFailure=0,
                barCount=0,comebacks=0,totalAtNet=0;
        if(exists(connection)){
            try(PreparedStatement ps=connection.prepareStatement(
                    "SELECT event_type,medals,occurred_at FROM "+TABLE+
                    " WHERE machine_id=? AND business_period_id=? "+
                    "ORDER BY occurred_at DESC,event_id DESC LIMIT 100")){
                ps.setInt(1,machineId);ps.setString(2,period);
                try(ResultSet rs=ps.executeQuery()){
                    while(rs.next()){
                        Type type=Type.valueOf(rs.getString(1));
                        JsonObject event=new JsonObject();
                        event.addProperty("type",type.name());
                        event.addProperty("medals",rs.getLong(2));
                        event.addProperty("occurredAt",rs.getLong(3));
                        history.add(event);
                    }
                }
            }
            try(PreparedStatement ps=connection.prepareStatement(
                    "SELECT event_type,COUNT(*),COALESCE(SUM(medals),0) FROM "+TABLE+
                    " WHERE machine_id=? AND business_period_id=? GROUP BY event_type")){
                ps.setInt(1,machineId);ps.setString(2,period);
                try(ResultSet rs=ps.executeQuery()){
                    while(rs.next()){
                        Type type=Type.valueOf(rs.getString(1));
                        long count=rs.getLong(2);
                        switch(type){
                            case AT_FIRST_HIT -> atCount=count;
                            case UPPER_ENTRY -> upperCount=count;
                            case CZ_CHALLENGE -> czAttempts=count;
                            case CZ_SUCCESS -> czSuccess=count;
                            case CZ_FAILURE -> czFailure=count;
                            case BAR -> barCount=count;
                            case AT_END -> totalAtNet=rs.getLong(3);
                            case COMEBACK -> comebacks=count;
                        }
                    }
                }
            }
        }
        out.addProperty("machineType","HYPER_DOG");
        out.addProperty("atFirstHits",atCount);
        out.addProperty("upperEntries",upperCount);
        out.addProperty("czAttempts",czAttempts);
        out.addProperty("czSuccesses",czSuccess);
        out.addProperty("czFailures",czFailure);
        out.addProperty("barCount",barCount);
        out.addProperty("comebacks",comebacks);
        out.addProperty("totalAtNet",totalAtNet);
        out.addProperty("bigCount",0);out.addProperty("regCount",0);
        out.add("history",history);
        return out;
    }
    private HyperDogEventJournal(){}
}
