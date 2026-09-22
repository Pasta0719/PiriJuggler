package jp.pirijuggler.paper.game;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.reel.InternalRole;
import jp.pirijuggler.paper.session.Session;

import java.util.*;

/**
 * Successor engine: current Juggler core plus 1/8192 GOD, 32G heaven, and GOD BIG chain.
 * Economy rates that Phase 03 must fit remain explicit tuning values rather than presentation logic.
 */
public final class JugglerGodGameEngine implements GameEngine {
    private static final int GOD_DENOMINATOR=8192;
    private static final int[] CONTINUATION_PERCENT={0,25,30,35,45,55,70};

    private final NormalGame delegate;
    private final RandomStreams random;
    private final RoleWeights weights;
    private final long normalToHeavenPpm;
    private final long heavenToHeavenPpm;

    public JugglerGodGameEngine(NormalGame delegate,RandomStreams random,RoleWeights weights,Map<String,Object> config) {
        this.delegate=Objects.requireNonNull(delegate);
        this.random=Objects.requireNonNull(random);
        this.weights=Objects.requireNonNull(weights);
        Map<String,Object> tuning=map(config==null?null:config.get("juggler_god"));
        normalToHeavenPpm=number(tuning.get("normal_to_heaven_ppm"),0);
        heavenToHeavenPpm=number(tuning.get("heaven_to_heaven_ppm"),0);
        if(normalToHeavenPpm<0||normalToHeavenPpm>1_000_000||heavenToHeavenPpm<0||heavenToHeavenPpm>1_000_000)
            throw new IllegalArgumentException("JUGGLER_GOD heaven tuning");
    }

    @Override
    public GameTransition plan(Session before, Machine machine, PacketType action, long sequence,
                               long now, long receivedNanos, int ping, Integer clientPressedIndex) {
        JugglerGodRuntime runtime=load(before,machine);
        JugglerGodRuntime prepared=runtime;
        InternalRole forced=null;
        boolean suppressNormalSpinCount=false;

        boolean normalLever=action==PacketType.SPACE_ACTION
                &&(before.state()==Session.GameState.NORMAL_BETTED||before.state()==Session.GameState.REPLAY_READY);

        if(normalLever){
            if(runtime.mode()==JugglerGodRuntime.Mode.GOD_CHAIN&&runtime.forceChainBig()){
                forced=InternalRole.BIG;
                suppressNormalSpinCount=!runtime.countNextChainGame();
            }else if(runtime.mode()!=JugglerGodRuntime.Mode.GOD_CHAIN
                    &&random.gameplay(machine.id()).nextInt(GOD_DENOMINATOR)==0){
                forced=InternalRole.GOD;
            }else if(runtime.mode()==JugglerGodRuntime.Mode.HEAVEN){
                int progress=Math.min(32,runtime.heavenProgress()+1);
                prepared=new JugglerGodRuntime(runtime.mode(),runtime.heavenTarget(),progress,
                        runtime.guaranteedRemaining(),runtime.forceChainBig(),runtime.countNextChainGame(),
                        runtime.bonusOrigin(),runtime.godBigCount(),runtime.godFreeze(),runtime.lastEvent());
                if(progress>=runtime.heavenTarget())
                    forced=weights.drawBonusFamily(machine.setting(),random.gameplay(machine.id()));
                else
                    forced=weights.drawNonBonus(machine.setting(),random.gameplay(machine.id()));
            }
        }

        NormalGame.Transition legacy=delegate.plan(before,action,sequence,machine.setting(),now,receivedNanos,ping,clientPressedIndex,forced);
        JugglerGodRuntime next=prepared;
        Session rawAfter=legacy.after();

        if(normalLever&&legacy.lever()&&rawAfter.text("internal_role")!=null){
            InternalRole actual=InternalRole.valueOf(rawAfter.text("internal_role"));
            if(actual==InternalRole.GOD){
                next=new JugglerGodRuntime(prepared.mode(),prepared.heavenTarget(),prepared.heavenProgress(),
                        prepared.guaranteedRemaining(),false,false,"GOD_CHAIN",prepared.godBigCount(),true,"GOD_FREEZE");
            }else if(GameRules.bonus(actual)!=null){
                String origin=prepared.mode()==JugglerGodRuntime.Mode.HEAVEN?"HEAVEN":
                        prepared.mode()==JugglerGodRuntime.Mode.GOD_CHAIN?"GOD_CHAIN":"NORMAL";
                next=new JugglerGodRuntime(prepared.mode(),prepared.heavenTarget(),prepared.heavenProgress(),
                        prepared.guaranteedRemaining(),false,false,origin,prepared.godBigCount(),false,"BONUS_DRAWN");
            }
        }

        if(legacy.finished()&&before.state()==Session.GameState.NORMAL_SPINNING
                &&"GOD".equals(before.text("internal_role"))){
            next=new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,4,false,false,
                    "GOD_CHAIN",1,false,"GOD_STARTED");
        }else if(legacy.bonusStarted()!=null&&"GOD_CHAIN".equals(next.bonusOrigin())){
            next=recordGodChainBonusStart(next);
        }

        if(legacy.bonusEnded()){
            next=afterBonus(machine,next);
        }

        Session after=withRuntime(rawAfter,next);
        int normalSpins=suppressNormalSpinCount?0:legacy.normalSpins();
        return new GameTransition(
                legacy.transaction(),before,after,legacy.bet(),legacy.payout(),normalSpins,
                legacy.finished(),legacy.lever(),legacy.bonusStarted(),legacy.bonusEnded(),
                legacy.publicDelayMs(),legacy.packets(),legacy.afterStart(),
                legacy.scheduled().stream().map(e->new GameTransition.Scheduled(e.delayMs(),e.packet())).toList(),
                next.toJsonString()
        );
    }

    static JugglerGodRuntime recordGodChainBonusStart(JugglerGodRuntime state){
        if(!"GOD_CHAIN".equals(state.bonusOrigin()))throw new IllegalArgumentException("Not GOD chain");
        return new JugglerGodRuntime(state.mode(),state.heavenTarget(),state.heavenProgress(),
                state.guaranteedRemaining(),state.forceChainBig(),state.countNextChainGame(),
                state.bonusOrigin(),state.godBigCount()+1,state.godFreeze(),"GOD_BIG_STARTED");
    }

    private JugglerGodRuntime afterBonus(Machine machine,JugglerGodRuntime state){
        var rng=random.gameplay(machine.id());
        if("GOD_CHAIN".equals(state.bonusOrigin())||state.mode()==JugglerGodRuntime.Mode.GOD_CHAIN){
            if(state.guaranteedRemaining()>0){
                return new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,
                        state.guaranteedRemaining()-1,true,false,"NONE",state.godBigCount(),false,"GOD_GUARANTEED_NEXT");
            }
            int rate=CONTINUATION_PERCENT[machine.setting()];
            if(rng.nextInt(100)<rate){
                return new JugglerGodRuntime(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,
                        0,true,true,"NONE",state.godBigCount(),false,"GOD_CONTINUE");
            }
            return enterHeaven(rng.nextInt(32)+1,state.godBigCount(),"GOD_END_HEAVEN");
        }

        if("HEAVEN".equals(state.bonusOrigin())){
            if(rng.nextLong(1_000_000)<heavenToHeavenPpm)
                return enterHeaven(rng.nextInt(32)+1,state.godBigCount(),"HEAVEN_CONTINUE");
            return new JugglerGodRuntime(JugglerGodRuntime.Mode.NORMAL,0,0,0,false,false,
                    "NONE",state.godBigCount(),false,"HEAVEN_END");
        }

        if(rng.nextLong(1_000_000)<normalToHeavenPpm)
            return enterHeaven(rng.nextInt(32)+1,state.godBigCount(),"NORMAL_TO_HEAVEN");
        return new JugglerGodRuntime(JugglerGodRuntime.Mode.NORMAL,0,0,0,false,false,
                "NONE",state.godBigCount(),false,"NORMAL");
    }

    private static JugglerGodRuntime enterHeaven(int target,int godBigCount,String event){
        return new JugglerGodRuntime(JugglerGodRuntime.Mode.HEAVEN,target,0,0,false,false,
                "NONE",godBigCount,false,event);
    }

    private static JugglerGodRuntime load(Session before,Machine machine){
        JsonObject session=before.machineState();
        if(session!=null&&session.has("jgMode"))return JugglerGodRuntime.fromJson(session.toString());
        return JugglerGodRuntime.fromJson(machine.runtimeJson());
    }

    private static Session withRuntime(Session session,JugglerGodRuntime runtime){
        var values=new LinkedHashMap<>(session.snapshot());
        values.put("machine_state_json",runtime.toJsonString());
        return new Session(values);
    }

    @Override
    public List<Envelope> committed(GameTransition action,long sentNanos){
        var packets=new ArrayList<>(delegate.committed(toLegacy(action),sentNanos));
        JugglerGodRuntime runtime=JugglerGodRuntime.fromJson(action.machineRuntimeJson());
        if(runtime.godFreeze()){
            for(int i=0;i<packets.size();i++){
                Envelope p=packets.get(i);
                if(p.packetType()==PacketType.SPIN_START){
                    JsonObject b=p.payload().deepCopy();b.addProperty("godFreeze",true);
                    packets.set(i,new Envelope(p.protocol(),p.packetType(),b));
                }
            }
        }
        return List.copyOf(packets);
    }

    @Override
    public List<GameTransition.Scheduled> scheduled(GameTransition action){
        return delegate.scheduled(toLegacy(action)).stream()
                .map(e->new GameTransition.Scheduled(e.delayMs(),e.packet())).toList();
    }

    @Override public Optional<Envelope> resume(Session saved,long sentNanos){return delegate.resume(saved,sentNanos);}
    @Override public Session capture(Session saved,long now){return delegate.capture(saved,now);}
    @Override public void forget(UUID session){delegate.forget(session);}

    private static NormalGame.Transition toLegacy(GameTransition action){
        return new NormalGame.Transition(action.transaction(),action.before(),action.after(),action.bet(),action.payout(),
                action.normalSpins(),action.finished(),action.lever(),action.bonusStarted(),action.bonusEnded(),
                action.publicDelayMs(),action.packets(),action.afterStart(),
                action.scheduled().stream().map(e->new NormalGame.Scheduled(e.delayMs(),e.packet())).toList());
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> map(Object value){return value instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();}
    private static long number(Object value,long fallback){return value instanceof Number n?n.longValue():fallback;}
}
