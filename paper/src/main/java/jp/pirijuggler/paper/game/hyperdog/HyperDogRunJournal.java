package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.session.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * HYPER_DOG-only projection of RUSH/HYPER/CZ and an uninterrupted one-shot.
 * Durable in the SAME SQLite transaction as the game and wallet.
 * No BIG/REG or legacy machine tables are modified.
 */
public final class HyperDogRunJournal {
    private static final String RUNS="hyper_dog_runs";
    private static final String ENTRIES="hyper_dog_run_entries";

    private HyperDogRunJournal(){}

    private static void ensure(PiriDatabase db)throws SQLException{
        db.sql("CREATE TABLE IF NOT EXISTS "+RUNS+" ("+
                "run_id TEXT PRIMARY KEY, machine_id INTEGER NOT NULL, "+
                "business_period_id TEXT NOT NULL, first_games INTEGER, "+
                "medals INTEGER NOT NULL, final INTEGER NOT NULL, "+
                "updated_at INTEGER NOT NULL)");
        db.sql("CREATE TABLE IF NOT EXISTS "+ENTRIES+" ("+
                "machine_id INTEGER NOT NULL, business_period_id TEXT NOT NULL, "+
                "event_id TEXT NOT NULL, run_id TEXT NOT NULL, "+
                "event_type TEXT NOT NULL, games INTEGER, medals INTEGER, "+
                "occurred_at INTEGER NOT NULL, PRIMARY KEY(machine_id,event_id))");
    }

    private static long currentGames(PiriDatabase db,Session owner,String period)throws SQLException{
        List<Map<String,Object>> rows=db.rows(
                "SELECT current_games FROM machine_period_stats WHERE machine_id=? AND business_period_id=?",
                owner.machine(),period);
        if(rows.size()!=1)throw new SQLException("HYPER_DOG normal G row absent");
        return ((Number)rows.getFirst().get("current_games")).longValue();
    }
    private static String runId(Session owner,HyperDogFullGame after){
        return owner.id()+":"+after.shotSerial();
    }
    public static void append(PiriDatabase db,Session owner,HyperDogFullGame before,
                              HyperDogFullGame after,long sequence,long now)throws SQLException{
        if(after.shotSerial()<=0)return;
        String period=owner.text("source_business_period_id");
        if(period==null||period.isBlank())throw new SQLException("No HYPER_DOG business period");
        ensure(db);
        String run=runId(owner,after);
        boolean began=before==null||before.shotSerial()!=after.shotSerial();
        long first=0;
        if(began){
            first=currentGames(db,owner,period);
            // The winning normal spin is committed into period G after this journal write.
            if(before!=null&&before.at()==null&&before.normal().pending()!=null&&
                    before.normal().pending().bet()>0&&after.normal().pending()==null)
                first=Math.addExact(first,1);
        }
        // Read-time history can be reconstructed from the entry ledger alone;
        // the current one-shot counter stays durable even when nobody is seated.
        db.sql("INSERT OR IGNORE INTO "+RUNS+
                "(run_id,machine_id,business_period_id,first_games,medals,final,updated_at)"+
                " VALUES(?,?,?,?,?,?,?)",
                run,owner.machine(),period,began?first:null,after.shotGain(),
                after.shotFinal()?1:0,now);

        HyperDogAtGame.Stage oldStage=before==null||before.at()==null?null:before.at().stage();
        HyperDogAtGame.Stage newStage=after.at()==null?null:after.at().stage();
        if(oldStage!=HyperDogAtGame.Stage.LOWER_INITIAL&&newStage==HyperDogAtGame.Stage.LOWER_INITIAL)
            entry(db,owner,period,run,sequence,"RUSH",now);
        if(oldStage!=HyperDogAtGame.Stage.UPPER_INITIAL&&newStage==HyperDogAtGame.Stage.UPPER_INITIAL)
            entry(db,owner,period,run,sequence,"HYPER",now);
        if(oldStage!=HyperDogAtGame.Stage.UPPER_CZ&&newStage==HyperDogAtGame.Stage.UPPER_CZ)
            entry(db,owner,period,run,sequence,"CZ",now);

        // State 0 = GET (active), 1 = TOTAL (finalized and still visible),
        // 2 = finalized but hidden after the NEXT completed normal paid game.
        // Never restore the old TOTAL on later BET/LEVER or after server restart.
        db.sql("UPDATE "+RUNS+" SET medals=?,final=CASE WHEN final=2 THEN 2 ELSE ? END,updated_at=? WHERE run_id=?",
                after.shotGain(),after.shotFinal()?1:0,now,run);
        hideFinishedShotAfterOneNormalGame(db,run,before,after);
        if(after.shotFinal()&&(before==null||!before.shotFinal())){
            // All RUSH/HYPER entries in the chain show the same final one-shot net.
            db.sql("UPDATE "+ENTRIES+" SET medals=? WHERE run_id=? AND event_type IN ('RUSH','HYPER')",
                    after.shotGain(),run);
        }
    }

    /**
     * The 32nd comeback game completes a shot but must still show TOTAL.
     * Only the first separate, completed normal game after that clears the
     * live panel. The shot and all historical ledger entries remain intact.
     */
    private static void hideFinishedShotAfterOneNormalGame(PiriDatabase db,String run,
            HyperDogFullGame before,HyperDogFullGame after)throws SQLException{
        if(before!=null&&before.shotFinal()&&after.shotFinal()&&
                before.shotSerial()==after.shotSerial()&&
                before.at()==null&&after.at()==null&&
                after.normal().games()>before.normal().games())
            db.sql("UPDATE "+RUNS+" SET final=2 WHERE run_id=? AND final=1",run);
    }

    private static void entry(PiriDatabase db,Session owner,String period,String run,
                              long sequence,String type,long now)throws SQLException{
        Long firstG=null;
        if(!"CZ".equals(type)){
            List<Map<String,Object>> prior=db.rows("SELECT COUNT(*) AS n FROM "+ENTRIES+
                    " WHERE run_id=? AND event_type IN ('RUSH','HYPER')",run);
            if(((Number)prior.getFirst().get("n")).longValue()==0){
                List<Map<String,Object>> saved=db.rows(
                        "SELECT first_games FROM "+RUNS+" WHERE run_id=?",run);
                if(!saved.isEmpty()&&saved.getFirst().get("first_games")!=null)
                    firstG=((Number)saved.getFirst().get("first_games")).longValue();
            }
        }
        db.sql("INSERT OR IGNORE INTO "+ENTRIES+
                "(machine_id,business_period_id,event_id,run_id,event_type,games,medals,occurred_at)"+
                " VALUES(?,?,?,?,?,?,?,?)",owner.machine(),period,
                owner.id()+":"+sequence+":"+type,run,type,firstG,null,now);
    }

    private static boolean exists(Connection c,String name)throws SQLException{
        try(PreparedStatement p=c.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")){
            p.setString(1,name);
            try(ResultSet rs=p.executeQuery()){return rs.next();}
        }
    }

    /** Read-only current-period projection: all UIs use these exact fields. */
    public static JsonObject read(Connection c,int machineId,String period)throws SQLException{
        if(c==null||machineId<1||period==null||period.isBlank())
            throw new IllegalArgumentException("HYPER_DOG data read");
        long rush=0,hyper=0,cz=0;
        JsonArray history=new JsonArray();
        if(exists(c,ENTRIES)){
            try(PreparedStatement p=c.prepareStatement(
                    "SELECT event_type,games,medals,occurred_at FROM "+ENTRIES+
                    " WHERE machine_id=? AND business_period_id=? "+
                    "ORDER BY occurred_at DESC,event_id DESC LIMIT 100")){
                p.setInt(1,machineId);p.setString(2,period);
                try(ResultSet rs=p.executeQuery()){
                    while(rs.next()){
                        JsonObject j=new JsonObject();
                        j.addProperty("type",rs.getString(1));
                        long g=rs.getLong(2);
                        if(!rs.wasNull())j.addProperty("games",g);
                        long medals=rs.getLong(3);
                        if(!rs.wasNull())j.addProperty("medals",medals);
                        j.addProperty("occurredAt",rs.getLong(4));
                        history.add(j);
                    }
                }
            }
            try(PreparedStatement p=c.prepareStatement(
                    "SELECT event_type,COUNT(*) FROM "+ENTRIES+
                    " WHERE machine_id=? AND business_period_id=? GROUP BY event_type")){
                p.setInt(1,machineId);p.setString(2,period);
                try(ResultSet rs=p.executeQuery()){
                    while(rs.next()){
                        switch(rs.getString(1)){
                            case "RUSH" -> rush=rs.getLong(2);
                            case "HYPER" -> hyper=rs.getLong(2);
                            case "CZ" -> cz=rs.getLong(2);
                            default -> {}
                        }
                    }
                }
            }
        }
        // Older data already records every HYPER/CZ entry in this canonical
        // event journal. Use it for these two totals to avoid erasing the
        // current period's visible history during upgrade. RUSH lacked a
        // dedicated historic event and is counted only when actually observed.
        if(exists(c,"hyper_dog_events")){
            try(PreparedStatement p=c.prepareStatement(
                    "SELECT event_type,COUNT(*) FROM hyper_dog_events "+
                    "WHERE machine_id=? AND business_period_id=? "+
                    "AND event_type IN ('UPPER_ENTRY','CZ_CHALLENGE') GROUP BY event_type")){
                p.setInt(1,machineId);p.setString(2,period);
                try(ResultSet rs=p.executeQuery()){
                    while(rs.next()){
                        if("UPPER_ENTRY".equals(rs.getString(1)))hyper=rs.getLong(2);
                        if("CZ_CHALLENGE".equals(rs.getString(1)))cz=rs.getLong(2);
                    }
                }
            }
        }
        boolean shotExists=false,shotFinal=false;
        long shotNet=0;
        if(exists(c,RUNS)){
            try(PreparedStatement p=c.prepareStatement(
                    "SELECT medals,final FROM "+RUNS+
                    " WHERE machine_id=? ORDER BY updated_at DESC,run_id DESC LIMIT 1")){
                p.setInt(1,machineId);
                try(ResultSet rs=p.executeQuery()){
                    if(rs.next()){
                        shotNet=rs.getLong(1);
                        int visibility=rs.getInt(2);
                        shotExists=visibility!=2;
                        shotFinal=visibility==1;
                    }
                }
            }
        }
        JsonObject out=new JsonObject();
        out.addProperty("machineType","HYPER_DOG");
        out.addProperty("rushCount",rush);
        out.addProperty("hyperCount",hyper);
        out.addProperty("czCount",cz);
        out.addProperty("shotExists",shotExists);
        out.addProperty("shotFinal",shotFinal);
        out.addProperty("shotNet",shotNet);
        out.add("history",history);
        return out;
    }
}
