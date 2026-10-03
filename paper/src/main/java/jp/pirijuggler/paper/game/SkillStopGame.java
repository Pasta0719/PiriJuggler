package jp.pirijuggler.paper.game;

import com.google.gson.*;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.game.GameTransition.Scheduled;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.config.FixedGameRules;
import jp.pirijuggler.paper.reel.*;
import jp.pirijuggler.paper.session.Session;
import jp.pirijuggler.paper.threading.MainThread;
import java.util.*;

/** Plans immutable action snapshots on the main thread; publishes only after durable commit. */
public final class SkillStopGame implements GameEngine {
    private record Motion(UUID spin,double left,double center,double right,ReelMotion.Profile profile,long started) {
        double[] starts(){return new double[]{left,center,right};}
    }
    private final SkillStopWeights weights;private final RandomStreams random;private final SkillStopControl solver;private final MainThread main;private final PremiumPolicy premium;
    private final int bigThreshold,regThreshold;
    private final Map<UUID,Motion> motions=new HashMap<>();

    public SkillStopGame(RandomStreams random,MainThread main,Map<String,Object> config) {
        this.weights=new SkillStopWeights();this.random=random;this.solver=new SkillStopControl();this.main=main;
        this.premium=new PremiumPolicy(config);this.bigThreshold=280-14;this.regThreshold=112-14;
    }
    @Override public GameTransition plan(Session before,Machine machine,PacketType action,long sequence,long now,long receivedNanos,int ping,Integer pressed) {
        SkillStopRole forced=null;PremiumPolicy.Type forcedPremium=null;
        JsonObject state=machine.runtimeJson()==null?new JsonObject():JsonParser.parseString(machine.runtimeJson()).getAsJsonObject();
        if(state.has("forceSkillRole")&&(before.state()==Session.GameState.NORMAL_BETTED||before.state()==Session.GameState.REPLAY_READY)) {
            forced=SkillStopRole.valueOf(state.get("forceSkillRole").getAsString());
            if(state.has("forceSkillPremium"))forcedPremium=PremiumPolicy.Type.valueOf(state.get("forceSkillPremium").getAsString());
        }
        GameTransition t=plan(before,action,sequence,machine.setting(),now,receivedNanos,ping,pressed,forced,forcedPremium);
        if(t.lever()&&forced!=null){state.remove("forceSkillRole");state.remove("forceSkillPremium");return new GameTransition(t.transaction(),t.before(),t.after(),t.bet(),t.payout(),t.normalSpins(),t.finished(),t.lever(),t.bonusStarted(),t.bonusEnded(),t.publicDelayMs(),t.packets(),t.afterStart(),t.scheduled(),state.toString());}
        return t;
    }
    public GameTransition plan(Session before,PacketType action,long sequence,int setting,long now,long receivedNanos,int ping) {
        return plan(before,action,sequence,setting,now,receivedNanos,ping,null);
    }
    public GameTransition plan(Session before,PacketType action,long sequence,int setting,long now,long receivedNanos,int ping,Integer clientPressedIndex) {
        return plan(before,action,sequence,setting,now,receivedNanos,ping,clientPressedIndex,null,null);
    }
    public GameTransition plan(Session before,PacketType action,long sequence,int setting,long now,long receivedNanos,int ping,Integer clientPressedIndex,SkillStopRole forcedNormalRole,PremiumPolicy.Type forcedPremium) {
        main.requireMainThread();
        if(before.lifecycle()!=Session.Lifecycle.ACTIVE)throw new DomainException("SESSION_MISMATCH");
        if(sequence<=before.sequence())throw new DomainException("SEQUENCE_OLD");
        var values=new LinkedHashMap<>(before.snapshot());values.put("last_client_sequence",sequence);values.put("last_activity",now);
        var packets=new ArrayList<Envelope>();var afterStart=new ArrayList<Envelope>();var scheduled=new ArrayList<Scheduled>();
        int bet=0,payout=0,spins=0;boolean finished=false,lever=false,bonusEnded=false;String bonusStarted=null;long publicDelay=0;

        Session.GameState state=before.state();
        if(action==PacketType.SPACE_ACTION&&state==Session.GameState.SEATED_READY) {
            var result=balance(before).bet(3);putBalance(values,result.balance());
            if(result.accepted()) {bet=3;values.put("game_state","NORMAL_BETTED");values.put("current_bet",3);values.put("pay_display",0);packets.add(accepted(action,sequence));}
            else packets.add(ErrorPackets.rejected(sequence,ErrorCode.NOT_ENOUGH_CREDIT));
        } else if(action==PacketType.SPACE_ACTION&&(state==Session.GameState.NORMAL_BETTED||state==Session.GameState.REPLAY_READY)) {
            SkillStopRole role=forcedNormalRole==null?weights.draw(setting,random.gameplay(before.machine())):forcedNormalRole;
            PremiumPolicy.Type p=forcedPremium!=null?forcedPremium:drawPremium(role,before.machine());
            ReelMotion.Profile profile=p==PremiumPolicy.Type.A?ReelMotion.Profile.REVERSE_500MS:ReelMotion.Profile.NORMAL;
            beginSpin(values,before,role.name(),profile,stateForNormalSpin(),role.bonus());
            values.put("premium_type",p==null?null:p.name());
            configureNoticeAtLever(values,afterStart,scheduled,role,p);
            spins=1;lever=true;packets.add(accepted(action,sequence));
        } else if(action==PacketType.SPACE_ACTION&&(state==Session.GameState.BONUS_PENDING_BIG||state==Session.GameState.BONUS_PENDING_REG)) {
            boolean free=pendingReplay(before);var result=free?new GameRules.Bet(balance(before),true):balance(before).bet(1);putBalance(values,result.balance());
            if(result.accepted()) {bet=free?0:1;setPendingReplay(values,false);values.put("game_state",state==Session.GameState.BONUS_PENDING_BIG?"BONUS_ENTRY_BETTED_BIG":"BONUS_ENTRY_BETTED_REG");values.put("current_bet",free?0:1);values.put("pay_display",0);packets.add(accepted(action,sequence));}
            else packets.add(ErrorPackets.rejected(sequence,ErrorCode.NOT_ENOUGH_CREDIT));
        } else if(action==PacketType.SPACE_ACTION&&(state==Session.GameState.BONUS_ENTRY_BETTED_BIG||state==Session.GameState.BONUS_ENTRY_BETTED_REG)) {
            String next=state==Session.GameState.BONUS_ENTRY_BETTED_BIG?"BONUS_ENTRY_SPINNING_BIG":"BONUS_ENTRY_SPINNING_REG";
            beginSpin(values,before,SkillStopWeights.pending(random.gameplay(before.machine())).name(),ReelMotion.Profile.NORMAL,next,before.text("bonus_type"));lever=true;packets.add(accepted(action,sequence));
        } else if(action==PacketType.SPACE_ACTION&&(state==Session.GameState.BIG_READY||state==Session.GameState.REG_READY)) {
            var result=balance(before).bet(2);putBalance(values,result.balance());
            if(result.accepted()) {bet=2;values.put("game_state",state==Session.GameState.BIG_READY?"BIG_BETTED":"REG_BETTED");values.put("current_bet",2);values.put("pay_display",0);packets.add(accepted(action,sequence));}
            else packets.add(ErrorPackets.rejected(sequence,ErrorCode.NOT_ENOUGH_CREDIT));
        } else if(action==PacketType.SPACE_ACTION&&(state==Session.GameState.BIG_BETTED||state==Session.GameState.REG_BETTED)) {
            SkillStopRole display=drawBonusDisplay(before.machine());
            beginSpin(values,before,display.name(),ReelMotion.Profile.NORMAL,state==Session.GameState.BIG_BETTED?"BIG_SPINNING":"REG_SPINNING",before.text("bonus_type"));
            lever=true;packets.add(accepted(action,sequence));
        } else if(isSpinning(state)&&Set.of(PacketType.SPACE_ACTION,PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT).contains(action)) {
            Motion motion=motions.get(before.id());
            if(motion==null||!motion.spin.toString().equals(before.text("spin_id")))throw new DomainException("SPIN_MISMATCH");
            SkillStopRound round=round(before,motion);round.begin(motion.started);
            var body=before.identity();body.addProperty("clientSequence",sequence);if(clientPressedIndex!=null)body.addProperty("pressedIndex",clientPressedIndex);
            var stop=round.receive(before.player(),Envelope.current(action,body),receivedNanos,ping);packets.addAll(stop.packets());
            if(stop.accepted()) {
                values.put("stopped_mask",round.stoppedMask());saveHistory(values,round.history());
                for(var reel:Reel.values())values.put("display_"+reel.name().toLowerCase(Locale.ROOT)+"_stop",round.display().stop(reel));
                sample(values,motion,receivedNanos);
                int delay=stop.choice().durationMs();
                if(stop.tenpaiSound()){JsonObject b=new JsonObject();b.addProperty("spinId",before.text("spin_id"));scheduled.add(new Scheduled(delay,Envelope.current(PacketType.TENPAI_SOUND,b)));}
                if(state==Session.GameState.NORMAL_SPINNING&&premiumType(before)==PremiumPolicy.Type.B&&stoppedReel(action,round)==Reel.LEFT)scheduled.add(new Scheduled(delay,notice(before,"OFF","NOTICE","STEADY")));
                if(premiumType(before)==PremiumPolicy.Type.A)values.put("lamp_on",1);
                if(round.stoppedMask()==7) {
                    finished=true;publicDelay=delay;
                    switch(state) {
                        case NORMAL_SPINNING -> {
                            SkillStopRole role=SkillStopRole.valueOf(before.text("internal_role"));
                            var outcome=round.outcome();String bonus=role.bonus();
                            payout=outcome.payout();putBalance(values,balance(before).payout(payout));values.put("pay_display",payout);values.put("current_bet",0);
                            if(outcome.entryBonus()!=null){bonusStarted=outcome.entryBonus();values.put("game_state",bonusStarted+"_READY");values.put("bonus_payout_count",0);values.put("lamp_on",1);values.put("notice_state","ON");JsonObject b=new JsonObject();b.addProperty("bonusType",bonusStarted);scheduled.add(new Scheduled(delay,Envelope.current(PacketType.BONUS_START,b)));}
                            else{values.put("game_state",bonus!=null?"BONUS_PENDING_"+bonus:outcome.replay()?"REPLAY_READY":"SEATED_READY");values.put("current_bet",outcome.replay()?3:0);values.put("lamp_on",bonus!=null?1:0);values.put("notice_state",bonus!=null?"ON":"NONE");}
                            addFinalNotice(before,bonus,scheduled,delay);clearSpin(values);
                            if(payout>0)scheduled.add(new Scheduled(delay,Envelope.current(PacketType.PAYOUT,new JsonObject())));
                        }
                        case BONUS_ENTRY_SPINNING_BIG, BONUS_ENTRY_SPINNING_REG -> {
                            var outcome=round.outcome();payout=outcome.payout();putBalance(values,balance(before).payout(payout));values.put("pay_display",payout);values.put("current_bet",0);
                            String type=before.text("bonus_type");
                            if(outcome.entryBonus()!=null){bonusStarted=type;values.put("game_state",type+"_READY");values.put("bonus_payout_count",0);JsonObject b=new JsonObject();b.addProperty("bonusType",type);scheduled.add(new Scheduled(delay,Envelope.current(PacketType.BONUS_START,b)));}
                            else{values.put("game_state","BONUS_PENDING_"+type);setPendingReplay(values,outcome.replay());}
                            clearSpin(values);
                            if(payout>0)scheduled.add(new Scheduled(delay,Envelope.current(PacketType.PAYOUT,new JsonObject())));
                        }
                        case BIG_SPINNING, REG_SPINNING -> {
                            round.outcome();
                            payout=14;putBalance(values,balance(before).payout(14));values.put("pay_display",14);
                            long count=Math.addExact(before.number("bonus_payout_count"),14);values.put("bonus_payout_count",count);values.put("current_bet",0);
                            boolean big=state==Session.GameState.BIG_SPINNING;boolean end=big?count>bigThreshold:count>regThreshold;
                            if(end){bonusEnded=true;values.put("game_state","SEATED_READY");values.put("bonus_payout_count",0);values.put("lamp_on",0);values.put("notice_state","NONE");values.put("bonus_type",null);}
                            else values.put("game_state",big?"BIG_READY":"REG_READY");
                            clearSpin(values);scheduled.add(new Scheduled(delay,Envelope.current(PacketType.PAYOUT,new JsonObject())));
                            if(end){JsonObject b=new JsonObject();b.addProperty("bonusType",big?"BIG":"REG");scheduled.add(new Scheduled(delay,Envelope.current(PacketType.BONUS_END,b)));}
                        }
                        default -> throw new IllegalStateException("Unexpected spinning state "+state);
                    }
                }
            }
        } else packets.add(ErrorPackets.rejected(sequence,ErrorCode.INVALID_STATE));
        return new GameTransition(UUID.randomUUID(),before,new Session(values),bet,payout,spins,finished,lever,bonusStarted,bonusEnded,publicDelay,packets,afterStart,scheduled,null);
    }

    public List<Envelope> committed(GameTransition action,long sentNanos) {
        main.requireMainThread();var packets=new ArrayList<>(action.packets());Session after=action.after();
        if(action.finished())motions.remove(after.id());
        if(action.publicDelayMs()==0)packets.add(Envelope.current(PacketType.PUBLIC_STATE,after.publicState()));
        if(action.lever())packets.add(start(after,sentNanos,profile(after)));
        packets.addAll(action.afterStart());return List.copyOf(packets);
    }
    public List<Scheduled> scheduled(GameTransition action){
        main.requireMainThread();var result=new ArrayList<Scheduled>();
        if(action.publicDelayMs()>0)result.add(new Scheduled(action.publicDelayMs(),Envelope.current(PacketType.PUBLIC_STATE,action.after().publicState())));
        result.addAll(action.scheduled());return List.copyOf(result);
    }
    public Optional<Envelope> resume(Session saved,long sentNanos) {
        main.requireMainThread();
        return isSpinning(saved.state())?Optional.of(start(saved,sentNanos,ReelMotion.Profile.RESUME_NORMAL)):Optional.empty();
    }
    private Envelope start(Session saved,long now,ReelMotion.Profile profile) {
        Motion motion=new Motion(UUID.fromString(saved.text("spin_id")),phase(saved,"left"),phase(saved,"center"),phase(saved,"right"),profile,now);
        motions.put(saved.id(),motion);return round(saved,motion).begin(now);
    }
    public Session capture(Session saved,long now) {
        main.requireMainThread();Motion motion=motions.get(saved.id());
        if(motion==null||!isSpinning(saved.state()))return saved;
        var values=new LinkedHashMap<>(saved.snapshot());sample(values,motion,now);return new Session(values);
    }
    public void forget(UUID session){main.requireMainThread();motions.remove(session);}

    private PremiumPolicy.Type drawPremium(SkillStopRole role,int machine){
        if(premium==null)return null;var legacy=role.oneMedal()?jp.pirijuggler.paper.reel.InternalRole.BIG:jp.pirijuggler.paper.reel.InternalRole.valueOf(role.name());return premium.draw(legacy,random.gameplay(machine)).orElse(null);
    }
    private void configureNoticeAtLever(Map<String,Object> values,List<Envelope> afterStart,List<Scheduled> scheduled,SkillStopRole role,PremiumPolicy.Type p){
        if(role.bonus()==null){values.put("notice_state","NONE");values.put("lamp_on",0);return;}
        if(p==null){
            values.put("notice_state","AFTER");values.put("lamp_on",0);return;
        }
        values.put("notice_state","PREMIUM_"+p.name());
        switch(p){
            case A -> {values.put("lamp_on",0);scheduled.add(new Scheduled(500,notice((String)values.get("spin_id"),"ON","NONE","STEADY")));}
            case B,D,F -> values.put("lamp_on",0);
            case C -> {values.put("lamp_on",1);afterStart.add(notice((String)values.get("spin_id"),"ON","NOTICE","STEADY"));}
            case E -> {values.put("lamp_on",1);afterStart.add(notice((String)values.get("spin_id"),"ON","NOTICE_X5","FAST_BLINK_1S"));}
        }
    }
    private void addFinalNotice(Session before,String bonus,List<Scheduled> scheduled,int delay){
        if(bonus==null)return;PremiumPolicy.Type p=premiumType(before);String state=before.text("notice_state");
        if(p==null&&"AFTER".equals(state))scheduled.add(new Scheduled(delay,notice(before,"ON","NOTICE","STEADY")));
        else if(p==PremiumPolicy.Type.B||p==PremiumPolicy.Type.F)scheduled.add(new Scheduled(delay,notice(before,"ON","NOTICE","STEADY")));
        else if(p==PremiumPolicy.Type.D)scheduled.add(new Scheduled(delay,notice(before,"ON","NOTICE_STRONG","STEADY")));
    }
    private static Envelope notice(Session s,String lamp,String sound,String pattern){return notice(s.text("spin_id"),lamp,sound,pattern);}
    private static Envelope notice(String spin,String lamp,String sound,String pattern){JsonObject b=new JsonObject();b.addProperty("spinId",spin);b.addProperty("lamp",lamp);b.addProperty("pattern",pattern);b.addProperty("sound",sound);return Envelope.current(PacketType.NOTICE,b);}

    private void beginSpin(Map<String,Object> values,Session before,String internalRole,ReelMotion.Profile profile,String nextState,String bonus){
        values.put("game_state",nextState);values.put("spin_id",UUID.randomUUID().toString());values.put("internal_role",internalRole);values.put("premium_type",null);
        values.put("bonus_type",bonus);values.put("stopped_mask",0);values.put("motion_profile",profile.name());values.put("pay_display",0);
        saveHistory(values,SkillStopHistory.empty());
        for(String reel:List.of("left","center","right"))values.put("phase_"+reel,(double)before.number("display_"+reel+"_stop"));
    }
    private static void clearSpin(Map<String,Object> values){values.put("spin_id",null);values.put("internal_role",null);values.put("premium_type",null);values.put("motion_profile",null);}
    private static boolean isSpinning(Session.GameState state){return state==Session.GameState.NORMAL_SPINNING||state==Session.GameState.BONUS_ENTRY_SPINNING_BIG||state==Session.GameState.BONUS_ENTRY_SPINNING_REG||state==Session.GameState.BIG_SPINNING||state==Session.GameState.REG_SPINNING;}
    private static String stateForNormalSpin(){return "NORMAL_SPINNING";}
    private static PremiumPolicy.Type premiumType(Session s){String text=s.text("premium_type");return text==null?null:PremiumPolicy.Type.valueOf(text);}
    private static Reel stoppedReel(PacketType action,SkillStopRound round){return switch(action){case STOP_LEFT->Reel.LEFT;case STOP_CENTER->Reel.CENTER;case STOP_RIGHT->Reel.RIGHT;case SPACE_ACTION->Reel.values()[round.history().order()[round.history().count()-1]];default->null;};}
    private SkillStopRole drawBonusDisplay(int machine){return random.gameplay(machine).nextLong(1_000_000)<850275?SkillStopRole.GRAPE:SkillStopRole.CHERRY;}
    private static ReelMotion.Profile profile(Session s){String value=s.text("motion_profile");return value==null?ReelMotion.Profile.NORMAL:ReelMotion.Profile.valueOf(value);}
    private void sample(Map<String,Object> values,Motion motion,long now) {
        double[] starts=motion.starts();int mask=((Number)values.get("stopped_mask")).intValue();
        for(var reel:Reel.values()) {String name=reel.name().toLowerCase(Locale.ROOT);values.put("phase_"+name,(mask&reel.bit())!=0?((Number)values.get("display_"+name+"_stop")).doubleValue():ReelMotion.phase(motion.profile,starts[reel.ordinal()],Math.max(0,(now-motion.started)/1e9)));}
    }
    private SkillStopRound round(Session s,Motion m) {
        SkillStopRole role=SkillStopRole.valueOf(s.text("internal_role"));SkillStopControl.Context context;String mode;
        switch(s.state()){
            case NORMAL_SPINNING -> {PremiumPolicy.Type p=premiumType(s);context=SkillStopControl.Context.normal(role,p==null?SkillStopControl.Premium.NONE:SkillStopControl.Premium.valueOf(p.name()));mode="NORMAL";}
            case BONUS_ENTRY_SPINNING_BIG,BONUS_ENTRY_SPINNING_REG -> {context=SkillStopControl.Context.pending(role,s.text("bonus_type"));mode="BONUS_ENTRY";}
            case BIG_SPINNING,REG_SPINNING -> {context=SkillStopControl.Context.bonusDisplay(role);mode=s.state()==Session.GameState.BIG_SPINNING?"BIG":"REG";}
            default -> throw new IllegalArgumentException("not spinning");
        }
        return new SkillStopRound(solver,context,history(s),new ReelRound.Identity(s.player(),s.id(),s.machine(),m.spin),m.profile,mode,m.starts(),new StopTriplet((int)s.number("display_left_stop"),(int)s.number("display_center_stop"),(int)s.number("display_right_stop")),s.sequence(),main);
    }
    private static SkillStopHistory history(Session s){JsonObject state=s.machineState();if(state==null||!state.has("skillInputs"))throw new IllegalStateException("Missing skill input history");return new SkillStopHistory(array(state,"skillInputs"),array(state,"skillStops"),array(state,"skillOrder"));}
    private static int[] array(JsonObject state,String name){JsonArray a=state.getAsJsonArray(name);int[] out=new int[a.size()];for(int i=0;i<out.length;i++)out[i]=a.get(i).getAsInt();return out;}
    private static JsonObject runtime(Map<String,Object> values){Object raw=values.get("machine_state_json");return raw instanceof String text&&!text.isBlank()?JsonParser.parseString(text).getAsJsonObject():new JsonObject();}
    private static void saveHistory(Map<String,Object> values,SkillStopHistory history){JsonObject state=runtime(values);for(var item:Map.of("skillInputs",history.inputs(),"skillStops",history.stops(),"skillOrder",history.order()).entrySet()){JsonArray a=new JsonArray();for(int n:item.getValue())a.add(n);state.add(item.getKey(),a);}values.put("machine_state_json",state.toString());}
    private static void setPendingReplay(Map<String,Object> values,boolean replay){JsonObject state=runtime(values);state.addProperty("skillPendingReplay",replay);values.put("machine_state_json",state.toString());}
    private static boolean pendingReplay(Session s){JsonObject state=s.machineState();return state!=null&&state.has("skillPendingReplay")&&state.get("skillPendingReplay").getAsBoolean();}
    private static double phase(Session s,String name){return ((Number)s.snapshot().get("phase_"+name)).doubleValue();}
    public static GameRules.Balance balance(Session s){return new GameRules.Balance(Math.toIntExact(s.number("credit")),s.number("held_medals"));}
    private static void putBalance(Map<String,Object> values,GameRules.Balance balance){values.put("credit",balance.credit());values.put("held_medals",balance.held());}
    private static Envelope accepted(PacketType type,long sequence){JsonObject b=new JsonObject();b.addProperty("clientSequence",sequence);b.addProperty("action",type.name());return Envelope.current(PacketType.ACTION_ACCEPTED,b);}
}
