package jp.pirijuggler.paper.game;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.reel.*;
import jp.pirijuggler.paper.session.Session;
import jp.pirijuggler.paper.threading.MainThread;
import java.util.*;

/** Plans immutable action snapshots on the main thread; publishes only after durable commit. */
public final class NormalGame {
    public record Transition(UUID transaction,Session before,Session after,int bet,int payout,int normalSpins,
                             boolean finished,boolean lever,List<Envelope> packets) {
        public Transition {packets=List.copyOf(packets);}
    }
    private record Motion(UUID spin,double left,double center,double right,ReelMotion.Profile profile,long started) {
        double[] starts(){return new double[]{left,center,right};}
    }
    private final RoleWeights weights;private final RandomStreams random;private final StopSolver solver;private final MainThread main;
    private final Map<UUID,Motion> motions=new HashMap<>();
    public NormalGame(RoleWeights weights,RandomStreams random,StopSolver solver,MainThread main) {
        this.weights=weights;this.random=random;this.solver=solver;this.main=main;
    }
    public Transition plan(Session before,PacketType action,long sequence,int setting,long now,long receivedNanos,int ping) {
        main.requireMainThread();
        if(before.lifecycle()!=Session.Lifecycle.ACTIVE)throw new DomainException("SESSION_MISMATCH");
        if(sequence<=before.sequence())throw new DomainException("SEQUENCE_OLD");
        var values=new LinkedHashMap<>(before.snapshot());values.put("last_client_sequence",sequence);values.put("last_activity",now);
        var packets=new ArrayList<Envelope>();int bet=0,payout=0,spins=0;boolean finished=false,lever=false;
        if(action==PacketType.SPACE_ACTION&&before.state()==Session.GameState.SEATED_READY) {
            var result=balance(before).bet(3);putBalance(values,result.balance());
            if(result.accepted()) {
                bet=3;values.put("game_state","NORMAL_BETTED");values.put("current_bet",3);values.put("pay_display",0);
                packets.add(accepted(action,sequence));
            } else packets.add(ErrorPackets.rejected(sequence,ErrorCode.NOT_ENOUGH_CREDIT));
        } else if(action==PacketType.SPACE_ACTION&&(before.state()==Session.GameState.NORMAL_BETTED||before.state()==Session.GameState.REPLAY_READY)) {
            // Setting is supplied from the current machine at this LEVER, never sampled at BET.
            InternalRole role=weights.draw(setting,random.gameplay(before.machine()));
            values.put("game_state","NORMAL_SPINNING");values.put("spin_id",UUID.randomUUID().toString());
            values.put("internal_role",role.name());values.put("premium_type",null);values.put("notice_state","NONE");
            values.put("bonus_type",GameRules.bonus(role));values.put("stopped_mask",0);values.put("motion_profile","NORMAL");
            values.put("current_bet",3);values.put("pay_display",0);
            for(String reel:List.of("left","center","right"))values.put("phase_"+reel,(double)before.number("display_"+reel+"_stop"));
            spins=1;lever=true;packets.add(accepted(action,sequence));
        } else if(before.state()==Session.GameState.NORMAL_SPINNING&&Set.of(PacketType.SPACE_ACTION,PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT).contains(action)) {
            Motion motion=motions.get(before.id());
            if(motion==null||!motion.spin.toString().equals(before.text("spin_id")))throw new DomainException("SPIN_MISMATCH");
            ReelRound round=round(before,motion);round.begin(motion.started);
            var body=before.identity();body.addProperty("clientSequence",sequence);
            var stop=round.receive(before.player(),Envelope.current(action,body),receivedNanos,ping);
            packets.addAll(stop.packets());
            if(stop.accepted()) {
                values.put("stopped_mask",round.stoppedMask());
                for(var reel:Reel.values())values.put("display_"+reel.name().toLowerCase(Locale.ROOT)+"_stop",round.display().stop(reel));
                sample(values,motion,receivedNanos);
                if(stop.tenpaiSound()){JsonObject b=new JsonObject();b.addProperty("spinId",before.text("spin_id"));packets.add(Envelope.current(PacketType.TENPAI_SOUND,b));}
                if(round.stoppedMask()==7) {
                    finished=true;InternalRole role=InternalRole.valueOf(before.text("internal_role"));
                    if(!solver.catalogue().evaluation(round.display()).valid(role.display(false)))throw new IllegalStateException("Unexpected final reel shape");
                    payout=GameRules.payout(role);putBalance(values,balance(before).payout(payout));values.put("pay_display",payout);
                    String bonus=GameRules.bonus(role);
                    values.put("game_state",bonus!=null?"BONUS_PENDING_"+bonus:role==InternalRole.REPLAY?"REPLAY_READY":"SEATED_READY");
                    values.put("current_bet",role==InternalRole.REPLAY?3:0);values.put("lamp_on",bonus!=null?1:0);
                    values.put("spin_id",null);values.put("internal_role",null);values.put("premium_type",null);values.put("motion_profile",null);
                    // PAYOUT is an event; authoritative numeric values are in PUBLIC_STATE.
                    if(payout>0)packets.add(Envelope.current(PacketType.PAYOUT,new JsonObject()));
                }
            }
        } else packets.add(ErrorPackets.rejected(sequence,ErrorCode.INVALID_STATE));
        return new Transition(UUID.randomUUID(),before,new Session(values),bet,payout,spins,finished,lever,packets);
    }
    public List<Envelope> committed(Transition action,long sentNanos) {
        main.requireMainThread();var packets=new ArrayList<>(action.packets());Session after=action.after();
        if(action.finished())motions.remove(after.id());
        // Publish the snapshot before SPIN_START so the client knows the current stopped mask.
        packets.add(Envelope.current(PacketType.PUBLIC_STATE,after.publicState()));
        if(action.lever())packets.add(start(after,sentNanos,ReelMotion.Profile.NORMAL));
        return List.copyOf(packets);
    }
    public Optional<Envelope> resume(Session saved,long sentNanos) {
        main.requireMainThread();
        return saved.state()==Session.GameState.NORMAL_SPINNING?Optional.of(start(saved,sentNanos,ReelMotion.Profile.RESUME_NORMAL)):Optional.empty();
    }
    private Envelope start(Session saved,long now,ReelMotion.Profile profile) {
        Motion motion=new Motion(UUID.fromString(saved.text("spin_id")),phase(saved,"left"),phase(saved,"center"),phase(saved,"right"),profile,now);
        motions.put(saved.id(),motion);return round(saved,motion).begin(now);
    }
    public Session capture(Session saved,long now) {
        main.requireMainThread();Motion motion=motions.get(saved.id());
        if(motion==null||saved.state()!=Session.GameState.NORMAL_SPINNING)return saved;
        var values=new LinkedHashMap<>(saved.snapshot());sample(values,motion,now);return new Session(values);
    }
    public void forget(UUID session){main.requireMainThread();motions.remove(session);}
    private void sample(Map<String,Object> values,Motion motion,long now) {
        double[] starts=motion.starts();int mask=((Number)values.get("stopped_mask")).intValue();
        for(var reel:Reel.values()) {
            String name=reel.name().toLowerCase(Locale.ROOT);
            values.put("phase_"+name,(mask&reel.bit())!=0?((Number)values.get("display_"+name+"_stop")).doubleValue():ReelMotion.phase(motion.profile,starts[reel.ordinal()],Math.max(0,(now-motion.started)/1e9)));
        }
    }
    private ReelRound round(Session s,Motion m) {
        return new ReelRound(solver,new ReelRound.Identity(s.player(),s.id(),s.machine(),m.spin),InternalRole.valueOf(s.text("internal_role")).display(false),false,
            m.profile,"NORMAL",m.starts(),new StopTriplet((int)s.number("display_left_stop"),(int)s.number("display_center_stop"),(int)s.number("display_right_stop")),(int)s.number("stopped_mask"),s.sequence(),main);
    }
    private static double phase(Session s,String name){return ((Number)s.snapshot().get("phase_"+name)).doubleValue();}
    public static GameRules.Balance balance(Session s){return new GameRules.Balance(Math.toIntExact(s.number("credit")),s.number("held_medals"));}
    private static void putBalance(Map<String,Object> values,GameRules.Balance balance){values.put("credit",balance.credit());values.put("held_medals",balance.held());}
    private static Envelope accepted(PacketType type,long sequence){JsonObject b=new JsonObject();b.addProperty("clientSequence",sequence);b.addProperty("action",type.name());return Envelope.current(PacketType.ACTION_ACCEPTED,b);}
}
