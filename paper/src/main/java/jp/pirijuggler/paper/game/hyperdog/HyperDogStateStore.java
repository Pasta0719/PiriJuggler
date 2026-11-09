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
        Objects.requireNonNull(game);
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
                if(prior.credit()!=previousBalance||
                        game.totalIn()<prior.totalIn()||game.totalOut()<prior.totalOut())
                    throw new IllegalStateException("HYPER_DOG_PREVIOUS_LEDGER_CORRUPT");
                long deltaOut=Math.subtractExact(game.totalOut(),prior.totalOut());
                long deltaIn=Math.subtractExact(game.totalIn(),prior.totalIn());
                long expected=Math.addExact(Math.subtractExact(previousBalance,deltaIn),deltaOut);
                if(game.credit()!=expected)
                    throw new IllegalStateException("HYPER_DOG_LEDGER_MISMATCH");
            }else{
                if(game.totalIn()!=0||game.totalOut()!=0||
                        game.normal().games()!=0||game.at()!=null||
                        game.credit()!=previousBalance)
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
            }else if(game.at()!=null && game.at().stage()==HyperDogAtGame.Stage.RED7_CONFIRM){
                // AT_RED7_CONFIRM can be parked before/after its automatic reel
                // motion. Its presentation history is optional until spin starts.
                if(reel!=null)expectedRole=HyperDogReelControl.Role.AT_ENTRY_RED7;
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
            List<Map<String,Object>> rows=db.rows(
                    "SELECT * FROM player_sessions WHERE session_id=?",sessionId.toString());
            if(rows.size()!=1)throw new SQLException("committed session vanished");
            return new Session(rows.getFirst());
        });
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
