package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.*;
import jp.pirijuggler.paper.database.PiriDatabase;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.machine.MachineType;
import jp.pirijuggler.paper.session.Session;
import jp.pirijuggler.paper.reel.HyperDogStopSession;
import jp.pirijuggler.common.reel.HyperDogReelControl;
import java.sql.SQLException;
import java.util.*;

/**
 * Phase04 HYPER_DOG per-player transaction boundary on the EXISTING piri.db
 * and its dedicated DB thread. No new schema revision or legacy machine rows.
 *
 * Every accepted BET/LEVER/STOP, including in-progress reel stop history,
 * can be committed atomically to player_sessions. Existing coin capacity is
 * preserved: credit [0..50], held_medals holds any overflow.
 *
 * Only a Phase04 owner may use these APIs; no public machine type routing is
 * activated until the complete cabinet and session transport are ready.
 */
public final class HyperDogStateStore {
    public record Loaded(Session session, HyperDogFullGame game, JsonObject reel) {}
    private final PiriDatabase db;
    public HyperDogStateStore(PiriDatabase db){this.db=Objects.requireNonNull(db);}

    public Loaded load(UUID player,UUID sessionId,int machineId) throws Exception {
        Session owner=requireOwner(player,sessionId,machineId,false);
        JsonObject state=owner.machineState();
        if(state==null||!state.has("hyperDog"))
            throw new DomainException("HYPER_DOG_STATE_NOT_INITIALIZED");
        HyperDogFullGame game=HyperDogFullGame.restore(state.getAsJsonObject("hyperDog"));
        long wallet=Math.addExact(owner.number("credit"),owner.number("held_medals"));
        if(game.credit()!=wallet)throw new IllegalStateException("HYPER_DOG_LEDGER_MISMATCH");
        JsonObject reel=state.has("reel")?state.getAsJsonObject("reel").deepCopy():null;
        return new Loaded(owner,game,reel);
    }

    /**
     * Idempotent retry accepts the exact same state and reel payload only.
     * A stale, conflicting or foreign write never replaces a more recent spin.
     */
    public Session commit(UUID player,UUID sessionId,int machineId,long expectedSequence,
                          long nextSequence,HyperDogFullGame game,JsonObject reel,long now)
            throws Exception {
        return commit(player,sessionId,machineId,expectedSequence,nextSequence,game,reel,now,null);
    }

    /** Native Paper GameEngine supplies server-owned visible stops. Test-only
     * Phase04/07 fixture callers keep the original overload unchanged.
     */
    public Session commit(UUID player,UUID sessionId,int machineId,long expectedSequence,
                          long nextSequence,HyperDogFullGame game,JsonObject reel,long now,
                          int[] confirmedDisplayStops) throws Exception {
        Objects.requireNonNull(game);
        if(confirmedDisplayStops!=null &&
           (confirmedDisplayStops.length!=3 ||
             java.util.Arrays.stream(confirmedDisplayStops).anyMatch(n->n<0||n>=21)))
            throw new DomainException("INVALID_STATE");
        if(nextSequence!=expectedSequence+1||nextSequence<1)
            throw new DomainException("SEQUENCE_OLD");
        return db.transaction(()->{
            Session owner=requireOwner(player,sessionId,machineId,true);
            if(owner.sequence()!=expectedSequence){
                // Retried packet: the exact sequence + payload must be identical.
                if(owner.sequence()==nextSequence){
                    JsonObject old=owner.machineState();
                    if(old!=null&&old.has("hyperDog")&&old.getAsJsonObject("hyperDog").equals(game.snapshot())
                            &&Objects.equals(old.has("reel")?old.getAsJsonObject("reel"):null,reel))
                        return owner;
                }
                throw new DomainException("SEQUENCE_OLD");
            }
            // The private AT/normal state must reconcile exactly with the
            // existing session wallet. A malformed server transition may not
            // mint medals even if it successfully serializes as JSON.
            long previousBalance=Math.addExact(owner.number("credit"),owner.number("held_medals"));
            JsonObject previousState=owner.machineState();
            HyperDogFullGame prior=null;
            if(previousState!=null&&previousState.has("hyperDog")){
                prior=HyperDogFullGame.restore(previousState.getAsJsonObject("hyperDog"));
                // External Vault-backed loan/insert/cashout changes the
                // authoritative player_sessions wallet without touching game
                // role RNG or AT winnings. A mismatch is legal only as an
                // external-wallet reconciliation. The next transition must
                // still satisfy exact conservation from the database balance.
                if(game.totalIn()<prior.totalIn()||game.totalOut()<prior.totalOut())
                    throw new IllegalStateException("HYPER_DOG_PREVIOUS_LEDGER_CORRUPT");
                long deltaOut=Math.subtractExact(game.totalOut(),prior.totalOut());
                long deltaIn=Math.subtractExact(game.totalIn(),prior.totalIn());
                long expected=Math.addExact(Math.subtractExact(previousBalance,deltaIn),deltaOut);
                if(game.credit()!=expected)
                    throw new IllegalStateException("HYPER_DOG_LEDGER_MISMATCH");
            }else{
                // First native game may be the same action as its initial
                // paid 3BET; there is no preceding server-owned snapshot yet.
                // No prepared or finished spin is allowed before the initial
                // durable transaction. v7 game RNG remains server-only.
                if(game.totalIn()>3||game.totalOut()!=0||
                        game.normal().games()!=0||game.at()!=null||
                        game.credit()!=Math.subtractExact(previousBalance,game.totalIn()))
                    throw new IllegalStateException("HYPER_DOG_INITIAL_WALLET_MISMATCH");
            }
            long balance=game.credit();
            if(balance<0)throw new IllegalArgumentException("negative medals");
            long credit=Math.min(50,balance),held=Math.subtractExact(balance,credit);
            // A prepared game is not recoverable unless its authoritative
            // physical reel/stop history is persisted in the SAME transaction.
            HyperDogReelControl.Role expectedRole=null;
            if(game.at()!=null && game.at().pending()!=null){
                var outcome=game.at().pending();
                expectedRole=outcome.stage()==HyperDogAtGame.Stage.UPPER_CZ
                        ?(outcome.czSuccess()?HyperDogReelControl.Role.CZ_WIN:
                            HyperDogReelControl.Role.CZ_LOSE)
                        :HyperDogReelControl.Role.valueOf(outcome.role().name());
            }else if(game.at()==null && game.normal().pending()!=null){
                expectedRole=game.normal().pending().reelRole();
            }else if(game.at()!=null &&
                    (game.at().stage()==HyperDogAtGame.Stage.RED7_CONFIRM ||
                     game.at().stage()==HyperDogAtGame.Stage.RAINBOW7_CONFIRM)){
                // Confirmation is not a paid spin: persist its authoritative real
                // physical stops independently from AT draw and coin accounting.
                // In particular, RAINBOW7 is manual, survives the second stop and
                // must never be reinterpreted as automatic CZ outer reels.
                if(reel!=null)expectedRole=game.at().stage()==HyperDogAtGame.Stage.RED7_CONFIRM
                        ?HyperDogReelControl.Role.AT_ENTRY_RED7
                        :HyperDogReelControl.Role.AT_ENTRY_RAINBOW7;
            }
            if(expectedRole!=null||reel!=null){
                if(reel==null)throw new DomainException("RECOVERY_REQUIRED");
                final HyperDogStopSession physical;
                try{physical=HyperDogStopSession.fromJson(reel);}
                catch(IllegalArgumentException invalid){throw new DomainException("INVALID_STATE");}
                if(!physical.snapshot().sessionId().equals(sessionId)||
                   physical.snapshot().machineId()!=machineId ||
                   expectedRole==null ||
                   physical.snapshot().round().role()!=expectedRole)
                    throw new DomainException("SESSION_MISMATCH");
            }
            JsonObject snapshot=new JsonObject();
            snapshot.addProperty("schema",1);
            snapshot.add("hyperDog",game.snapshot());
            if(reel!=null)snapshot.add("reel",reel.deepCopy());

            String state=game.at()!=null?"NORMAL_SPINNING":
                    game.normal().pending()!=null?"NORMAL_SPINNING":
                    game.normal().betPosted()?"NORMAL_BETTED":
                    game.normal().replayFree()?"REPLAY_READY":"SEATED_READY";
            int bet=game.at()!=null?
                    (game.at().pending()!=null?game.at().pending().bet():
                        game.at().snapshot().get("postedBet").getAsInt()):
                    (game.normal().pending()!=null?game.normal().pending().bet():
                        game.normal().betPosted()?3:0);
            int changed=db.sql("UPDATE player_sessions SET game_state=?,credit=?,held_medals=?,"+
                            "current_bet=?,machine_state_json=?,last_client_sequence=?,last_activity=? "+
                            "WHERE session_id=? AND player_uuid=? AND machine_id=? "+
                            "AND lifecycle='ACTIVE' AND last_client_sequence=?",
                    state,credit,held,bet,snapshot.toString(),nextSequence,now,sessionId.toString(),
                    player.toString(),machineId,expectedSequence);
            if(changed!=1)throw new DomainException("SEQUENCE_OLD");
            // Same SQLite transaction as wallet/reel/sequence: no event without an accepted game.
            // Event ID is session+sequence+type and safe against packet retries/restarts.
            HyperDogEventJournal.append(db,owner,prior,game,nextSequence,now);
            HyperDogRunJournal.append(db,owner,prior,game,nextSequence,now);
            updatePeriodStats(owner,prior,game,now);
            if(confirmedDisplayStops!=null)
                db.sql("UPDATE machines SET last_left_stop=?,last_center_stop=?,"+
                    "last_right_stop=?,updated_at=? WHERE machine_id=?",
                    confirmedDisplayStops[0],confirmedDisplayStops[1],confirmedDisplayStops[2],
                    now,machineId);
            List<Map<String,Object>> rows=db.rows(
                    "SELECT * FROM player_sessions WHERE session_id=?",sessionId.toString());
            if(rows.size()!=1)throw new SQLException("committed session vanished");
            return new Session(rows.getFirst());
        });
    }

    /**
     * Paid-game/coin-flow accounting on the very same SQLite transaction as the
     * owner snapshot. Only the current HYPER_DOG machine row is updated; no BIG/REG
     * counters or shared Juggler bonus history are ever incremented.
     */
    private void updatePeriodStats(Session owner,HyperDogFullGame before,
                                   HyperDogFullGame after,long now)throws SQLException {
        // The first native transition may include the first 3BET before an
        // initial HyperDog snapshot exists. Still debit that wager exactly
        // once; legacy fixture initial snapshots (before!=null) are unchanged.
        long spent=before==null?after.totalIn():Math.subtractExact(after.totalIn(),before.totalIn());
        long earned=before==null?after.totalOut():Math.subtractExact(after.totalOut(),before.totalOut());
        int finishedPaid=0;
        // Only normal-paid third stops advance G. RUSH, HYPER and CZ are excluded.
        if(before!=null&&before.at()==null&&before.normal().pending()!=null&&
                before.normal().pending().bet()>0&&after.normal().pending()==null)finishedPaid=1;
        boolean atStarted=before!=null&&before.at()==null&&after.at()!=null;
        boolean atEnded=before!=null&&before.at()!=null&&after.at()==null;
        if(spent==0&&earned==0&&finishedPaid==0&&!atEnded&&!atStarted)return;
        String period=owner.text("source_business_period_id");
        var rows=db.rows("SELECT total_games,current_games,today_difference,today_max_difference "+
                "FROM machine_period_stats WHERE machine_id=? AND business_period_id=?",
                owner.machine(),period);
        if(rows.size()!=1)throw new SQLException("Missing HYPER_DOG period stats");
        var row=rows.getFirst();
        long games=Math.addExact(((Number)row.get("total_games")).longValue(),finishedPaid);
        long current=atStarted?0:Math.addExact(((Number)row.get("current_games")).longValue(),finishedPaid);
        long difference=Math.addExact(Math.subtractExact(
                ((Number)row.get("today_difference")).longValue(),spent),earned);
        long maximum=Math.max(((Number)row.get("today_max_difference")).longValue(),difference);
        int changed=db.sql("UPDATE machine_period_stats SET total_games=?,current_games=?,"+
                "today_difference=?,today_max_difference=? WHERE machine_id=? AND business_period_id=?",
                games,current,difference,maximum,owner.machine(),period);
        if(changed!=1)throw new SQLException("HYPER_DOG period update mismatch");
        if(spent!=0||earned!=0||finishedPaid!=0||atEnded)db.sql(
                "INSERT INTO graph_points(machine_id,business_period_id,game,difference,occurred_at) "+
                "VALUES(?,?,?,?,?)",owner.machine(),period,games,difference,now);
    }

    private Session requireOwner(UUID player,UUID sessionId,int machineId,boolean active)
            throws Exception {
        if(player==null||sessionId==null||machineId<1)
            throw new DomainException("INVALID_STATE");
        List<Map<String,Object>> machines=db.rows(
                "SELECT machine_type,deleted FROM machines WHERE machine_id=?",machineId);
        if(machines.size()!=1||!MachineType.HYPER_DOG.name().equals(machines.getFirst().get("machine_type"))||
                ((Number)machines.getFirst().get("deleted")).intValue()!=0)
            throw new DomainException("INVALID_STATE");
        List<Map<String,Object>> rows=db.rows(
                "SELECT * FROM player_sessions WHERE session_id=?",sessionId.toString());
        if(rows.size()!=1)throw new DomainException("RECOVERY_REQUIRED");
        Session session=new Session(rows.getFirst());
        if(!session.player().equals(player)||session.machine()!=machineId)
            throw new DomainException("MACHINE_OCCUPIED");
        if(active&&session.lifecycle()!=Session.Lifecycle.ACTIVE)
            throw new DomainException("RECOVERY_REQUIRED");
        return session;
    }
}
