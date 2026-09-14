package jp.pirijuggler.paper.database;

import jp.pirijuggler.paper.game.NormalGame;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.session.Session;
import java.util.*;

/** Atomic session, statistics, graph and server transaction receipt on the DB executor. */
public final class GameStore {
    private static final List<String> COLUMNS=List.of("game_state","credit","held_medals","spin_id","internal_role","premium_type","notice_state","lamp_on","bonus_type","bonus_payout_count","current_bet","pay_display","display_left_stop","display_center_stop","display_right_stop","stopped_mask","phase_left","phase_center","phase_right","motion_profile","last_client_sequence","last_activity");
    private final PiriDatabase db;
    public GameStore(PiriDatabase db){this.db=db;}
    public Session commit(NormalGame.Transition action) throws Exception {
        return db.transaction(()->{
            Session before=action.before(),after=action.after();String receipt="GAME_TX:"+action.transaction();
            var done=db.rows("SELECT value FROM metadata WHERE key=?",receipt);
            if(!done.isEmpty()) {
                if(!before.id().toString().equals(done.getFirst().get("value")))throw new IllegalStateException("Transaction identity mismatch");
                return db.state().session(before.player());
            }
            if(!before.id().equals(after.id())||!before.player().equals(after.player())||before.machine()!=after.machine()||after.sequence()<=before.sequence())throw new IllegalArgumentException("Invalid game transaction");
            var params=new ArrayList<Object>();for(String column:COLUMNS)params.add(after.snapshot().get(column));
            params.add(before.id().toString());params.add(before.sequence());params.add(before.text("game_state"));params.add(before.number("credit"));params.add(before.number("held_medals"));
            int changed=db.sql("UPDATE player_sessions SET "+String.join(",",COLUMNS.stream().map(c->c+"=?").toList())+" WHERE session_id=? AND last_client_sequence=? AND game_state=? AND credit=? AND held_medals=? AND lifecycle='ACTIVE'",params.toArray());
            if(changed!=1)throw new DomainException("SEQUENCE_OLD");
            String period=before.text("source_business_period_id");
            var stats=db.rows("SELECT * FROM machine_period_stats WHERE machine_id=? AND business_period_id=?",before.machine(),period).getFirst();
            long difference=Math.addExact(((Number)stats.get("today_difference")).longValue(),(long)action.payout()-action.bet());
            long total=Math.addExact(((Number)stats.get("total_games")).longValue(),action.normalSpins());
            long current=Math.addExact(((Number)stats.get("current_games")).longValue(),action.normalSpins());
            long max=Math.max(((Number)stats.get("today_max_difference")).longValue(),difference);
            long big=((Number)stats.get("big_count")).longValue(),reg=((Number)stats.get("reg_count")).longValue();
            if(action.bonusStarted()!=null){
                if(action.bonusStarted().equals("BIG"))big=Math.addExact(big,1);else if(action.bonusStarted().equals("REG"))reg=Math.addExact(reg,1);else throw new IllegalArgumentException("Unknown bonus type");
                db.sql("INSERT INTO bonus_history(machine_id,business_period_id,bonus_type,games,occurred_at) VALUES(?,?,?,?,?)",before.machine(),period,action.bonusStarted(),current,after.number("last_activity"));
            }
            if(action.bonusEnded())current=0;
            db.sql("UPDATE machine_period_stats SET total_games=?,big_count=?,reg_count=?,current_games=?,today_difference=?,today_max_difference=?,last_bonus_type=COALESCE(?,last_bonus_type),last_bonus_at=CASE WHEN ? IS NULL THEN last_bonus_at ELSE ? END WHERE machine_id=? AND business_period_id=?",
                    total,big,reg,current,difference,max,action.bonusStarted(),action.bonusStarted(),after.number("last_activity"),before.machine(),period);
            db.sql("UPDATE machines SET last_left_stop=?,last_center_stop=?,last_right_stop=?,updated_at=? WHERE machine_id=?",after.number("display_left_stop"),after.number("display_center_stop"),after.number("display_right_stop"),after.number("last_activity"),before.machine());
            if((action.finished()&&action.normalSpins()>0)||action.bonusEnded())db.sql("INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) VALUES(?,?,?,?,?)",before.machine(),period,total,difference,after.number("last_activity"));
            db.sql("INSERT INTO metadata(key,value) VALUES(?,?)",receipt,before.id().toString());
            return after;
        });
    }
    public void saveMotion(Session snapshot) throws Exception {
        db.sql("UPDATE player_sessions SET phase_left=?,phase_center=?,phase_right=? WHERE session_id=? AND last_client_sequence=?",snapshot.snapshot().get("phase_left"),snapshot.snapshot().get("phase_center"),snapshot.snapshot().get("phase_right"),snapshot.id().toString(),snapshot.sequence());
    }
}
