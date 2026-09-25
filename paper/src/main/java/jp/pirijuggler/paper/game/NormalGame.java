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
    public record Scheduled(long delayMs,Envelope packet){public Scheduled{if(delayMs<0)throw new IllegalArgumentException("delay");Objects.requireNonNull(packet);}}
    public record Transition(UUID transaction,Session before,Session after,int bet,int payout,int normalSpins,
                             boolean finished,boolean lever,String bonusStarted,boolean bonusEnded,long publicDelayMs,
                             List<Envelope> packets,List<Envelope> afterStart,List<Scheduled> scheduled) {
        public Transition {packets=List.copyOf(packets);afterStart=List.copyOf(afterStart);scheduled=List.copyOf(scheduled);if(publicDelayMs<0)throw new IllegalArgumentException("public delay");}
    }
    private record Motion(UUID spin,double left,double center,double right,ReelMotion.Profile profile,long started) {
        double[] starts(){return new double[]{left,center,right};}
    }
    private final RoleWeights weights;private final RandomStreams random;private final StopSolver solver;private final MainThread main;private final PremiumPolicy premium;private final boolean legacyBarPremium;
    private final int bigThreshold,regThreshold;
    private final Map<UUID,Motion> motions=new HashMap<>();

    public NormalGame(RoleWeights weights,RandomStreams random,StopSolver solver,MainThread main) {
        this(weights,random,solver,main,null,true);
    }
    public NormalGame(RoleWeights weights,RandomStreams random,StopSolver solver,MainThread main,Map<String,Object> config) {
        this(weights,random,solver,main,config,true);
    }
    public NormalGame(RoleWeights weights,RandomStreams random,StopSolver solver,MainThread main,Map<String,Object> config,boolean legacyBarPremium) {
        this(weights,random,solver,main,config,legacyBarPremium,FixedGameRules.BIG_PAYOUT,FixedGameRules.REG_PAYOUT);
    }
    public NormalGame(RoleWeights weights,RandomStreams random,StopSolver solver,MainThread main,Map<String,Object> config,boolean legacyBarPremium,int bigPayout,int regPayout) {
        if(bigPayout<14||regPayout<14||bigPayout%14!=0||regPayout%14!=0)throw new IllegalArgumentException("Bonus payout must be positive multiples of 14");
        this.weights=weights;this.random=random;this.solver=solver;this.main=main;this.premium=config==null?null:new PremiumPolicy(config);this.legacyBarPremium=legacyBarPremium;
        this.bigThreshold=bigPayout-14;this.regThreshold=regPayout-14;
    }

    public Transition plan(Session before,PacketType action,long sequence,int setting,long now,long receivedNanos,int ping) {
        return plan(before,action,sequence,setting,now,receivedNanos,ping,null);
    }
    public Transition plan(Session before,PacketType action,long sequence,int setting,long now,long receivedNanos,int ping,Integer clientPressedIndex) {
        return plan(before,action,sequence,setting,now,receivedNanos,ping,clientPressedIndex,null);
    }
    public Transition plan(Session before,PacketType action,long sequence,int setting,long now,long receivedNanos,int ping,Integer clientPressedIndex,InternalRole forcedNormalRole) {
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
            InternalRole role=forcedNormalRole==null?weights.draw(setting,random.gameplay(before.machine())):forcedNormalRole;
            PremiumPolicy.Type p=role==InternalRole.GOD?null:drawPremium(role,before.machine());
            ReelMotion.Profile profile=p==PremiumPolicy.Type.A?ReelMotion.Profile.REVERSE_500MS:ReelMotion.Profile.NORMAL;
            beginSpin(values,before,role.name(),profile,stateForNormalSpin(),GameRules.bonus(role));
            values.put("premium_type",p==null?null:p.name());
            configureNoticeAtLever(values,afterStart,scheduled,role,p);
            spins=1;lever=true;packets.add(accepted(action,sequence));
        } else if(action==PacketType.SPACE_ACTION&&(state==Session.GameState.BONUS_PENDING_BIG||state==Session.GameState.BONUS_PENDING_REG)) {
            var result=balance(before).bet(1);putBalance(values,result.balance());
            if(result.accepted()) {bet=1;values.put("game_state",state==Session.GameState.BONUS_PENDING_BIG?"BONUS_ENTRY_BETTED_BIG":"BONUS_ENTRY_BETTED_REG");values.put("current_bet",1);values.put("pay_display",0);packets.add(accepted(action,sequence));}
            else packets.add(ErrorPackets.rejected(sequence,ErrorCode.NOT_ENOUGH_CREDIT));
        } else if(action==PacketType.SPACE_ACTION&&(state==Session.GameState.BONUS_ENTRY_BETTED_BIG||state==Session.GameState.BONUS_ENTRY_BETTED_REG)) {
            String next=state==Session.GameState.BONUS_ENTRY_BETTED_BIG?"BONUS_ENTRY_SPINNING_BIG":"BONUS_ENTRY_SPINNING_REG";
            beginSpin(values,before,null,ReelMotion.Profile.NORMAL,next,before.text("bonus_type"));lever=true;packets.add(accepted(action,sequence));
        } else if(action==PacketType.SPACE_ACTION&&(state==Session.GameState.BIG_READY||state==Session.GameState.REG_READY)) {
            var result=balance(before).bet(2);putBalance(values,result.balance());
            if(result.accepted()) {bet=2;values.put("game_state",state==Session.GameState.BIG_READY?"BIG_BETTED":"REG_BETTED");values.put("current_bet",2);values.put("pay_display",0);packets.add(accepted(action,sequence));}
            else packets.add(ErrorPackets.rejected(sequence,ErrorCode.NOT_ENOUGH_CREDIT));
        } else if(action==PacketType.SPACE_ACTION&&(state==Session.GameState.BIG_BETTED||state==Session.GameState.REG_BETTED)) {
            DisplayRole display=drawBonusDisplay(before.machine());
            beginSpin(values,before,display.name(),ReelMotion.Profile.NORMAL,state==Session.GameState.BIG_BETTED?"BIG_SPINNING":"REG_SPINNING",before.text("bonus_type"));
            lever=true;packets.add(accepted(action,sequence));
        } else if(isSpinning(state)&&Set.of(PacketType.SPACE_ACTION,PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT).contains(action)) {
            Motion motion=motions.get(before.id());
            if(motion==null||!motion.spin.toString().equals(before.text("spin_id")))throw new DomainException("SPIN_MISMATCH");
            ReelRound round=round(before,motion);round.begin(motion.started);
            var body=before.identity();body.addProperty("clientSequence",sequence);if(clientPressedIndex!=null)body.addProperty("pressedIndex",clientPressedIndex);
            var stop=round.receive(before.player(),Envelope.current(action,body),receivedNanos,ping);packets.addAll(stop.packets());
            if(stop.accepted()) {
                values.put("stopped_mask",round.stoppedMask());
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
                            InternalRole role=InternalRole.valueOf(before.text("internal_role"));PremiumPolicy.Type p=premiumType(before);boolean premiumB=p==PremiumPolicy.Type.B;
                            var evaluation=solver.catalogue().evaluation(round.display());
                            DisplayRole baseRole=role.display(premiumB);DisplayRole directRole=directEntryRole(role,p);String bonus=GameRules.bonus(role);
                            DisplayRole fallbackRole=bonus==null?null:awardFallbackRole(role,premiumB);
                            boolean directEntry=directRole!=null&&evaluation.valid(directRole);
                            boolean naturalFallback=fallbackRole!=null&&evaluation.valid(fallbackRole);
                            boolean naturalAwardShape=directRole!=null;
                            if(!evaluation.valid(baseRole)&&!directEntry&&!naturalFallback&&!naturalAwardShape)throw new IllegalStateException("Unexpected final reel shape");
                            if(role==InternalRole.GOD){
                                payout=GameRules.payout(role);putBalance(values,balance(before).payout(payout));values.put("pay_display",payout);bonusStarted="BIG";
                                values.put("game_state","BIG_READY");values.put("current_bet",0);values.put("bonus_payout_count",0);values.put("lamp_on",1);values.put("notice_state","GOD");
                                clearSpin(values);
                                if(payout>0)scheduled.add(new Scheduled(delay,Envelope.current(PacketType.PAYOUT,new JsonObject())));
                                JsonObject b=new JsonObject();b.addProperty("bonusType","BIG");scheduled.add(new Scheduled(delay,Envelope.current(PacketType.BONUS_START,b)));
                            }else if(directEntry){
                                if(bonus==null)throw new IllegalStateException("Direct entry requires a bonus role");
                                payout=0;values.put("pay_display",0);bonusStarted=bonus;
                                values.put("game_state",bonus+"_READY");values.put("current_bet",0);values.put("bonus_payout_count",0);values.put("lamp_on",1);values.put("notice_state","ON");
                                addFinalNotice(before,bonus,scheduled,delay);clearSpin(values);
                                JsonObject b=new JsonObject();b.addProperty("bonusType",bonus);scheduled.add(new Scheduled(delay,Envelope.current(PacketType.BONUS_START,b)));
                            }else{
                                payout=GameRules.payout(role);putBalance(values,balance(before).payout(payout));values.put("pay_display",payout);
                                values.put("game_state",bonus!=null?"BONUS_PENDING_"+bonus:role==InternalRole.REPLAY?"REPLAY_READY":"SEATED_READY");
                                values.put("current_bet",role==InternalRole.REPLAY?3:0);values.put("lamp_on",bonus!=null?1:0);
                                addFinalNotice(before,bonus,scheduled,delay);
                                clearSpin(values);if(bonus==null){values.put("bonus_type",null);values.put("notice_state","NONE");}
                                else values.put("notice_state","ON");
                                if(payout>0)scheduled.add(new Scheduled(delay,Envelope.current(PacketType.PAYOUT,new JsonObject())));
                            }
                        }
                        case BONUS_ENTRY_SPINNING_BIG, BONUS_ENTRY_SPINNING_REG -> {
                            DisplayRole expected=state==Session.GameState.BONUS_ENTRY_SPINNING_BIG?DisplayRole.BIG_ENTRY:DisplayRole.REG_ENTRY;
                            if(!solver.catalogue().evaluation(round.display()).valid(expected))throw new IllegalStateException("Unexpected bonus entry shape");
                            String type=state==Session.GameState.BONUS_ENTRY_SPINNING_BIG?"BIG":"REG";bonusStarted=type;
                            values.put("game_state",type+"_READY");values.put("current_bet",0);values.put("bonus_payout_count",0);values.put("lamp_on",1);values.put("notice_state","ON");clearSpin(values);
                            JsonObject b=new JsonObject();b.addProperty("bonusType",type);scheduled.add(new Scheduled(delay,Envelope.current(PacketType.BONUS_START,b)));
                        }
                        case BIG_SPINNING, REG_SPINNING -> {
                            DisplayRole role=DisplayRole.valueOf(before.text("internal_role"));
                            if(!solver.catalogue().evaluation(round.display()).valid(role))throw new IllegalStateException("Unexpected bonus display shape");
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
        return new Transition(UUID.randomUUID(),before,new Session(values),bet,payout,spins,finished,lever,bonusStarted,bonusEnded,publicDelay,packets,afterStart,scheduled);
    }

    public List<Envelope> committed(Transition action,long sentNanos) {
        main.requireMainThread();var packets=new ArrayList<>(action.packets());Session after=action.after();
        if(action.finished())motions.remove(after.id());
        if(action.publicDelayMs()==0)packets.add(Envelope.current(PacketType.PUBLIC_STATE,after.publicState()));
        if(action.lever())packets.add(start(after,sentNanos,profile(after)));
        packets.addAll(action.afterStart());return List.copyOf(packets);
    }
    public List<Scheduled> scheduled(Transition action){
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

    private PremiumPolicy.Type drawPremium(InternalRole role,int machine){
        if(premium==null)return null;return premium.draw(role,random.gameplay(machine)).orElse(null);
    }
    private void configureNoticeAtLever(Map<String,Object> values,List<Envelope> afterStart,List<Scheduled> scheduled,InternalRole role,PremiumPolicy.Type p){
        if(role==InternalRole.GOD){values.put("notice_state","GOD_FREEZE");values.put("lamp_on",0);return;}
        if(GameRules.bonus(role)==null){values.put("notice_state","NONE");values.put("lamp_on",0);return;}
        if(p==null){
            boolean first=random.gameplay(((Number)values.get("machine_id")).intValue()).nextLong(1_000_000)<250_000;
            values.put("notice_state",first?"FIRST":"AFTER");values.put("lamp_on",first?1:0);
            if(first)afterStart.add(notice((String)values.get("spin_id"),"ON","NONE","STEADY"));return;
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
        for(String reel:List.of("left","center","right"))values.put("phase_"+reel,(double)before.number("display_"+reel+"_stop"));
    }
    private static void clearSpin(Map<String,Object> values){values.put("spin_id",null);values.put("internal_role",null);values.put("premium_type",null);values.put("motion_profile",null);}
    private static boolean isSpinning(Session.GameState state){return state==Session.GameState.NORMAL_SPINNING||state==Session.GameState.BONUS_ENTRY_SPINNING_BIG||state==Session.GameState.BONUS_ENTRY_SPINNING_REG||state==Session.GameState.BIG_SPINNING||state==Session.GameState.REG_SPINNING;}
    private static String stateForNormalSpin(){return "NORMAL_SPINNING";}
    private static PremiumPolicy.Type premiumType(Session s){String text=s.text("premium_type");return text==null?null:PremiumPolicy.Type.valueOf(text);}
    private static DisplayRole directEntryRole(InternalRole role,PremiumPolicy.Type premium){return premium==PremiumPolicy.Type.B||premium==PremiumPolicy.Type.F?null:role.directEntryDisplay();}
    private static DisplayRole awardFallbackRole(InternalRole role,boolean premiumB){
        if(premiumB)return DisplayRole.CHERRY;
        return switch(role){
            case BIG,REG->DisplayRole.MISS;
            case CHERRY_BIG,CHERRY_REG->DisplayRole.CHERRY;
            case PIERO_BIG,PIERO_REG->DisplayRole.PIERO;
            default->null;
        };
    }
    private static Reel stoppedReel(PacketType action,ReelRound round){return switch(action){case STOP_LEFT->Reel.LEFT;case STOP_CENTER->Reel.CENTER;case STOP_RIGHT->Reel.RIGHT;case SPACE_ACTION->{int mask=round.stoppedMask();if((mask&Reel.RIGHT.bit())!=0&&(mask&Reel.CENTER.bit())==0)yield Reel.CENTER;if((mask&Reel.CENTER.bit())!=0&&(mask&Reel.LEFT.bit())==0)yield Reel.LEFT;if((mask&Reel.RIGHT.bit())!=0)yield Reel.RIGHT;if((mask&Reel.CENTER.bit())!=0)yield Reel.CENTER;yield Reel.LEFT;}default->null;};}
    private DisplayRole drawBonusDisplay(int machine){long roll=random.gameplay(machine).nextLong(1_000_000);if(roll<916)return DisplayRole.BELL;if(roll<1832)return DisplayRole.PIERO;if(roll<850275)return DisplayRole.GRAPE;return DisplayRole.CHERRY;}
    private static ReelMotion.Profile profile(Session s){String value=s.text("motion_profile");return value==null?ReelMotion.Profile.NORMAL:ReelMotion.Profile.valueOf(value);}
    private void sample(Map<String,Object> values,Motion motion,long now) {
        double[] starts=motion.starts();int mask=((Number)values.get("stopped_mask")).intValue();
        for(var reel:Reel.values()) {String name=reel.name().toLowerCase(Locale.ROOT);values.put("phase_"+name,(mask&reel.bit())!=0?((Number)values.get("display_"+name+"_stop")).doubleValue():ReelMotion.phase(motion.profile,starts[reel.ordinal()],Math.max(0,(now-motion.started)/1e9)));}
    }
    private ReelRound round(Session s,Motion m) {
        DisplayRole role;DisplayRole alternateRole=null;boolean premiumF=false;boolean premiumEffect=false;String mode;
        switch(s.state()){
            case NORMAL_SPINNING -> {InternalRole internal=InternalRole.valueOf(s.text("internal_role"));PremiumPolicy.Type p=premiumType(s);role=internal.display(p==PremiumPolicy.Type.B);premiumF=p==PremiumPolicy.Type.F;premiumEffect=internal==InternalRole.GOD||legacyBarPremium&&p!=null;alternateRole=directEntryRole(internal,p);mode="NORMAL";}
            case BONUS_ENTRY_SPINNING_BIG -> {role=DisplayRole.BIG_ENTRY;mode="BONUS_ENTRY";}
            case BONUS_ENTRY_SPINNING_REG -> {role=DisplayRole.REG_ENTRY;mode="BONUS_ENTRY";}
            case BIG_SPINNING -> {role=DisplayRole.valueOf(s.text("internal_role"));mode="BIG";}
            case REG_SPINNING -> {role=DisplayRole.valueOf(s.text("internal_role"));mode="REG";}
            default -> throw new IllegalArgumentException("Not spinning: "+s.state());
        }
        return new ReelRound(solver,new ReelRound.Identity(s.player(),s.id(),s.machine(),m.spin),role,alternateRole,premiumF,premiumEffect,m.profile,mode,m.starts(),new StopTriplet((int)s.number("display_left_stop"),(int)s.number("display_center_stop"),(int)s.number("display_right_stop")),(int)s.number("stopped_mask"),s.sequence(),main);
    }
    private static double phase(Session s,String name){return ((Number)s.snapshot().get("phase_"+name)).doubleValue();}
    public static GameRules.Balance balance(Session s){return new GameRules.Balance(Math.toIntExact(s.number("credit")),s.number("held_medals"));}
    private static void putBalance(Map<String,Object> values,GameRules.Balance balance){values.put("credit",balance.credit());values.put("held_medals",balance.held());}
    private static Envelope accepted(PacketType type,long sequence){JsonObject b=new JsonObject();b.addProperty("clientSequence",sequence);b.addProperty("action",type.name());return Envelope.current(PacketType.ACTION_ACCEPTED,b);}
}
