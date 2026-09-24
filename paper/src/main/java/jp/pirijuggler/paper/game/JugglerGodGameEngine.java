package jp.pirijuggler.paper.game;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.ErrorCode;
import jp.pirijuggler.common.protocol.ErrorPackets;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.reel.InternalRole;
import jp.pirijuggler.paper.session.Session;

import java.util.*;

/**
 * Successor engine: current Juggler core plus 1/8192 GOD, 32G heaven,
 * GOD BIG chain and visible bonus-in-bonus stock.
 */
public final class JugglerGodGameEngine implements GameEngine {
    private static final int GOD_DENOMINATOR=8192;
    private static final int GOD_IN_GOD_BIG_STOCK=7;
    private static final long GOD_PRESENTATION_LOCK_MS=15_000L;
    private static final int GOD_PRESENTATION_SEVEN_STOP=3;
    private static final Set<PacketType> GOD_PRESENTATION_INPUTS=Set.of(
            PacketType.SPACE_ACTION,PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT);

    private final NormalGame delegate;
    private final RandomStreams random;
    private final RoleWeights weights;
    private final long normalBigToHeavenPpm;
    private final long normalRegToHeavenPpm;
    private final long heavenToHeavenPpm;
    private final int[] bonusScalePpm=new int[7];
    private final int[] smallRoleScalePpm=new int[7];

    public JugglerGodGameEngine(NormalGame delegate,RandomStreams random,RoleWeights weights,Map<String,Object> config) {
        this.delegate=Objects.requireNonNull(delegate);
        this.random=Objects.requireNonNull(random);
        this.weights=Objects.requireNonNull(weights);
        Map<String,Object> tuning=map(config==null?null:config.get("juggler_god"));
        normalBigToHeavenPpm=number(tuning.get("normal_big_to_heaven_ppm"),0);
        normalRegToHeavenPpm=number(tuning.get("normal_reg_to_heaven_ppm"),0);
        heavenToHeavenPpm=number(tuning.get("heaven_to_heaven_ppm"),0);
        Map<String,Object> settings=map(tuning.get("settings"));
        for(int setting=1;setting<=6;setting++){
            Map<String,Object> row=map(settings.get(Integer.toString(setting)));
            bonusScalePpm[setting]=(int)number(row.get("bonus_scale_ppm"),1_000_000);
            smallRoleScalePpm[setting]=(int)number(row.get("small_role_scale_ppm"),1_000_000);
        }
        if(normalBigToHeavenPpm<0||normalBigToHeavenPpm>1_000_000
                ||normalRegToHeavenPpm<0||normalRegToHeavenPpm>1_000_000
                ||heavenToHeavenPpm<0||heavenToHeavenPpm>1_000_000)
            throw new IllegalArgumentException("JUGGLER_GOD heaven tuning");
        for(int setting=1;setting<=6;setting++)if(bonusScalePpm[setting]<0||bonusScalePpm[setting]>1_000_000
                ||smallRoleScalePpm[setting]<0||smallRoleScalePpm[setting]>1_000_000)
            throw new IllegalArgumentException("JUGGLER_GOD role scale");
    }

    @Override
    public GameTransition plan(Session before, Machine machine, PacketType action, long sequence,
                               long now, long receivedNanos, int ping, Integer clientPressedIndex) {
        JugglerGodRuntime runtime=load(before,machine);
        JugglerGodRuntime prepared=runtime;
        InternalRole forced=null;

        long presentationStart=runtime.godPresentationStartMs();
        if(presentationStart>0&&now<presentationStart+GOD_PRESENTATION_LOCK_MS
                &&GOD_PRESENTATION_INPUTS.contains(action))
            return rejectDuringGodPresentation(before,runtime,sequence,now);

        Session delegateBefore=before;
        if(presentationStart>0&&now>=presentationStart+GOD_PRESENTATION_LOCK_MS
                &&before.state()==Session.GameState.BIG_READY&&action==PacketType.SPACE_ACTION){
            delegateBefore=rewrite(before,Map.of(
                    "display_left_stop",GOD_PRESENTATION_SEVEN_STOP,
                    "display_center_stop",GOD_PRESENTATION_SEVEN_STOP,
                    "display_right_stop",GOD_PRESENTATION_SEVEN_STOP,
                    "phase_left",(double)GOD_PRESENTATION_SEVEN_STOP,
                    "phase_center",(double)GOD_PRESENTATION_SEVEN_STOP,
                    "phase_right",(double)GOD_PRESENTATION_SEVEN_STOP));
            prepared=runtime.clearPresentation("GOD_PRESENTATION_DONE");
        }
        boolean suppressNormalSpinCount=false;

        boolean normalLever=action==PacketType.SPACE_ACTION
                &&(before.state()==Session.GameState.NORMAL_BETTED||before.state()==Session.GameState.REPLAY_READY);
        boolean bonusLever=action==PacketType.SPACE_ACTION
                &&(before.state()==Session.GameState.BIG_BETTED||before.state()==Session.GameState.REG_BETTED);
        boolean godInGodConfirm=normalLever&&"GOD".equals(runtime.pendingBonusHit());

        if(bonusLever&&runtime.godFreeze()&&"GOD_CHAIN".equals(runtime.bonusOrigin())){
            prepared=runtime.core(runtime.mode(),runtime.heavenTarget(),runtime.heavenProgress(),
                    runtime.guaranteedRemaining(),runtime.forceChainBig(),runtime.countNextChainGame(),
                    runtime.bonusOrigin(),runtime.godBigCount(),false,"GOD_PRESENTATION_DONE");
        }

        if(bonusLever&&"NONE".equals(runtime.pendingBonusHit())){
            String current=before.state()==Session.GameState.BIG_BETTED?"BIG":"REG";
            String hit=drawBonusOverlay(machine);
            if(!"NONE".equals(hit)){
                prepared=prepared.interrupt(hit,current,(int)before.number("bonus_payout_count"),false,
                        "GOD".equals(hit)?"GOD_IN_GOD_DRAWN":"BONUS_STOCK_DRAWN");
            }
        }

        if(normalLever){
            if(!"NONE".equals(runtime.forcedRole())){
                try { forced=InternalRole.valueOf(runtime.forcedRole()); }
                catch(IllegalArgumentException ignored) { forced=null; }
                prepared=runtime.forceRole("NONE");
            }
            if(forced!=null){
                // Explicit development force wins over production GOD/heaven/chain draws for this spin only.
            }else if(godInGodConfirm){
                forced=InternalRole.GOD;
                suppressNormalSpinCount=true;
                prepared=runtime.core(runtime.mode(),runtime.heavenTarget(),runtime.heavenProgress(),
                        runtime.guaranteedRemaining(),runtime.forceChainBig(),runtime.countNextChainGame(),
                        runtime.bonusOrigin(),runtime.godBigCount(),true,"GOD_IN_GOD_FREEZE");
            }else if(runtime.mode()==JugglerGodRuntime.Mode.GOD_CHAIN&&runtime.forceChainBig()){
                forced=InternalRole.BIG;
                suppressNormalSpinCount=!runtime.countNextChainGame();
                // A queued GOD-chain BIG is a one-shot reservation. Consume the reservation
                // at lever-on so it cannot survive into the started BIG and accidentally
                // retrigger itself forever. A following 0G/1G BIG can only be armed by the
                // fresh post-bonus continuation decision.
                prepared=runtime.core(runtime.mode(),runtime.heavenTarget(),runtime.heavenProgress(),
                        runtime.guaranteedRemaining(),false,false,runtime.bonusOrigin(),
                        runtime.godBigCount(),runtime.godFreeze(),"GOD_CHAIN_BIG_CONSUMED");
            }else if(runtime.mode()!=JugglerGodRuntime.Mode.GOD_CHAIN
                    &&random.gameplay(machine.id()).nextInt(GOD_DENOMINATOR)==0){
                forced=InternalRole.GOD;
            }else if(runtime.mode()==JugglerGodRuntime.Mode.HEAVEN){
                int progress=Math.min(32,runtime.heavenProgress()+1);
                prepared=runtime.core(runtime.mode(),runtime.heavenTarget(),progress,
                        runtime.guaranteedRemaining(),runtime.forceChainBig(),runtime.countNextChainGame(),
                        runtime.bonusOrigin(),runtime.godBigCount(),runtime.godFreeze(),runtime.lastEvent());
                if(progress>=runtime.heavenTarget())
                    forced=weights.drawBonusFamily(machine.setting(),random.gameplay(machine.id()));
                else
                    forced=weights.drawJugglerGodNonBonus(machine.setting(),random.gameplay(machine.id()),bonusScalePpm[machine.setting()],smallRoleScalePpm[machine.setting()]);
            }else{
                forced=weights.drawJugglerGod(machine.setting(),random.gameplay(machine.id()),bonusScalePpm[machine.setting()],smallRoleScalePpm[machine.setting()]);
            }
        }

        NormalGame.Transition legacy=delegate.plan(delegateBefore,action,sequence,machine.setting(),now,receivedNanos,ping,clientPressedIndex,forced);
        JugglerGodRuntime next=prepared;
        Session rawAfter=legacy.after();
        boolean suppressBonusStart=false;
        boolean suppressBonusEnd=false;
        String bonusStarted=legacy.bonusStarted();
        boolean bonusEnded=legacy.bonusEnded();

        if(bonusLever&&legacy.lever()&&!"NONE".equals(prepared.pendingBonusHit())){
            if(!"GOD".equals(prepared.pendingBonusHit()))
                rawAfter=rewrite(rawAfter,Map.of("lamp_on",1,"notice_state","ON"));
        }

        if(normalLever&&legacy.lever()&&rawAfter.text("internal_role")!=null){
            InternalRole actual=InternalRole.valueOf(rawAfter.text("internal_role"));
            if(actual==InternalRole.GOD){
                if(godInGodConfirm){
                    next=prepared.core(prepared.mode(),prepared.heavenTarget(),prepared.heavenProgress(),
                            prepared.guaranteedRemaining(),prepared.forceChainBig(),prepared.countNextChainGame(),
                            prepared.bonusOrigin(),prepared.godBigCount(),true,"GOD_IN_GOD_FREEZE");
                }else{
                    next=prepared.core(prepared.mode(),prepared.heavenTarget(),prepared.heavenProgress(),
                            prepared.guaranteedRemaining(),false,false,"GOD_CHAIN",
                            prepared.godBigCount(),true,"GOD_FREEZE");
                }
            }else if(GameRules.bonus(actual)!=null){
                String origin=prepared.mode()==JugglerGodRuntime.Mode.HEAVEN?"HEAVEN":
                        prepared.mode()==JugglerGodRuntime.Mode.GOD_CHAIN?"GOD_CHAIN":GameRules.bonus(actual);
                next=prepared.core(prepared.mode(),prepared.heavenTarget(),prepared.heavenProgress(),
                        prepared.guaranteedRemaining(),false,false,origin,
                        prepared.godBigCount(),false,"BONUS_DRAWN");
            }
        }

        // A bonus-game spin has completed while an overlay bonus/GOD was waiting.
        if(legacy.finished()&&(before.state()==Session.GameState.BIG_SPINNING||before.state()==Session.GameState.REG_SPINNING)
                &&!"NONE".equals(runtime.pendingBonusHit())){
            String current=before.state()==Session.GameState.BIG_SPINNING?"BIG":"REG";
            int count=Math.addExact((int)before.number("bonus_payout_count"),14);
            boolean ended="BIG".equals(current)?count>266:count>98;
            next=runtime.interrupt(runtime.pendingBonusHit(),current,count,ended,
                    "GOD".equals(runtime.pendingBonusHit())?"GOD_IN_GOD_CONFIRM_READY":"BONUS_STOCK_CONFIRM_READY");
            if("GOD".equals(runtime.pendingBonusHit())){
                rawAfter=rewrite(rawAfter,Map.of(
                        "game_state","NORMAL_BETTED","current_bet",0,"bonus_payout_count",0,
                        "lamp_on",0,"notice_state","NONE"));
            }else{
                rawAfter=rewrite(rawAfter,Map.of(
                        "game_state","BONUS_ENTRY_BETTED_"+runtime.pendingBonusHit(),"current_bet",0,
                        "bonus_type",runtime.pendingBonusHit(),"bonus_payout_count",0,
                        "lamp_on",1,"notice_state","ON"));
            }
            suppressBonusEnd=ended;
            bonusEnded=ended; // accounting/history sees the underlying bonus end at the real final 14-medal round.
        }

        // Visible 7 alignment confirms a newly acquired BIG/REG stock, then resumes the suspended bonus.
        boolean acquisitionEntryFinish=legacy.finished()
                &&(before.state()==Session.GameState.BONUS_ENTRY_SPINNING_BIG||before.state()==Session.GameState.BONUS_ENTRY_SPINNING_REG)
                &&!runtime.releasingStock()&&Set.of("BIG","REG").contains(runtime.pendingBonusHit());
        if(acquisitionEntryFinish){
            int big=runtime.additionalBigStock()+("BIG".equals(runtime.pendingBonusHit())?1:0);
            int reg=runtime.additionalRegStock()+("REG".equals(runtime.pendingBonusHit())?1:0);
            next=new JugglerGodRuntime(runtime.mode(),runtime.heavenTarget(),runtime.heavenProgress(),
                    runtime.guaranteedRemaining(),runtime.forceChainBig(),runtime.countNextChainGame(),
                    runtime.bonusOrigin(),runtime.godBigCount(),false,"BONUS_STOCK_CONFIRMED",
                    big,reg,"NONE","NONE",0,false,runtime.releasingStock());
            rawAfter=restoreOrRelease(rawAfter,machine,next,runtime.suspendedBonusType(),
                    runtime.suspendedBonusPayoutCount(),runtime.suspendedBonusEnded());
            next=loadRuntime(rawAfter,next);
            suppressBonusStart=true;
            bonusStarted=null;
        }

        // GOD-in-GOD BAR alignment confirms +7 BIG stock, without replacing the outer GOD.
        boolean godInGodFinish=legacy.finished()&&before.state()==Session.GameState.NORMAL_SPINNING
                &&"GOD".equals(before.text("internal_role"))&&"GOD".equals(runtime.pendingBonusHit());
        if(godInGodFinish){
            int big=Math.addExact(runtime.additionalBigStock(),GOD_IN_GOD_BIG_STOCK);
            next=new JugglerGodRuntime(runtime.mode(),runtime.heavenTarget(),runtime.heavenProgress(),
                    runtime.guaranteedRemaining(),runtime.forceChainBig(),runtime.countNextChainGame(),
                    runtime.bonusOrigin(),runtime.godBigCount(),false,"GOD_IN_GOD_CONFIRMED",
                    big,runtime.additionalRegStock(),"NONE","NONE",0,false,runtime.releasingStock());
            rawAfter=restoreOrRelease(rawAfter,machine,next,runtime.suspendedBonusType(),
                    runtime.suspendedBonusPayoutCount(),runtime.suspendedBonusEnded());
            next=loadRuntime(rawAfter,next);
            suppressBonusStart=true;
            bonusStarted=null;
        }else if(legacy.finished()&&before.state()==Session.GameState.NORMAL_SPINNING
                &&"GOD".equals(before.text("internal_role"))){
            next=runtime.core(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,4,false,false,
                    "GOD_CHAIN",1,false,"GOD_STARTED")
                    .startPresentation(Math.addExact(now,legacy.publicDelayMs()),"GOD_STARTED");
        }else if(legacy.bonusStarted()!=null&&"GOD_CHAIN".equals(next.bonusOrigin())&&!next.releasingStock()){
            next=recordGodChainBonusStart(next);
        }

        // Stock-release alignment is not a new hit: keep Piri Chance dark.
        if(legacy.bonusStarted()!=null&&runtime.releasingStock()){
            rawAfter=rewrite(rawAfter,Map.of("lamp_on",0,"notice_state","NONE"));
        }

        if(legacy.bonusEnded()&&!suppressBonusEnd){
            if(runtime.releasingStock()||next.releasingStock()){
                JugglerGodRuntime active=next;
                if(active.stockLampOn()){
                    Release release=beginRelease(rawAfter,active);
                    rawAfter=release.session();
                    next=release.runtime();
                }else{
                    next=afterBonus(machine,active.stopReleasing("STOCK_RELEASES_DONE"));
                }
            }else if(next.stockLampOn()){
                Release release=beginRelease(rawAfter,next);
                rawAfter=release.session();
                next=release.runtime();
            }else{
                next=afterBonus(machine,next);
            }
        }

        Session after=withRuntime(rawAfter,next);
        int normalSpins=suppressNormalSpinCount?0:legacy.normalSpins();

        final boolean dropBonusStart=suppressBonusStart;
        final boolean dropBonusEnd=suppressBonusEnd;
        var scheduled=legacy.scheduled().stream()
                .filter(e->!(dropBonusStart&&e.packet().packetType()==PacketType.BONUS_START))
                .filter(e->!(dropBonusEnd&&e.packet().packetType()==PacketType.BONUS_END))
                .map(e->new GameTransition.Scheduled(e.delayMs(),e.packet())).toList();

        return new GameTransition(
                legacy.transaction(),before,after,legacy.bet(),legacy.payout(),normalSpins,
                legacy.finished(),legacy.lever(),bonusStarted,bonusEnded,
                legacy.publicDelayMs(),legacy.packets(),legacy.afterStart(),scheduled,next.toJsonString()
        );
    }

    private static GameTransition rejectDuringGodPresentation(Session before,JugglerGodRuntime runtime,long sequence,long now){
        var values=new LinkedHashMap<>(before.snapshot());
        values.put("last_client_sequence",sequence);
        values.put("last_activity",now);
        Session after=new Session(values);
        return new GameTransition(UUID.randomUUID(),before,after,0,0,0,false,false,null,false,0,
                List.of(ErrorPackets.rejected(sequence,ErrorCode.INVALID_STATE)),List.of(),List.of(),runtime.toJsonString());
    }

    private String drawBonusOverlay(Machine machine){
        var rng=random.gameplay(machine.id());
        if(rng.nextInt(GOD_DENOMINATOR)==0)return "GOD";
        InternalRole role=weights.drawJugglerGod(machine.setting(),rng,bonusScalePpm[machine.setting()],smallRoleScalePpm[machine.setting()]);
        String bonus=GameRules.bonus(role);
        return bonus==null?"NONE":bonus;
    }

    static JugglerGodRuntime recordGodChainBonusStart(JugglerGodRuntime state){
        if(!"GOD_CHAIN".equals(state.bonusOrigin()))throw new IllegalArgumentException("Not GOD chain");
        return state.core(state.mode(),state.heavenTarget(),state.heavenProgress(),state.guaranteedRemaining(),
                state.forceChainBig(),state.countNextChainGame(),state.bonusOrigin(),
                state.godBigCount()+1,state.godFreeze(),"GOD_BIG_STARTED");
    }

    private record Release(Session session,JugglerGodRuntime runtime){}

    private Release beginRelease(Session raw,JugglerGodRuntime state){
        boolean big=state.additionalBigStock()>0;
        int nextBig=state.additionalBigStock()-(big?1:0);
        int nextReg=state.additionalRegStock()-(big?0:1);
        String type=big?"BIG":"REG";
        Session session=rewrite(raw,Map.of(
                "game_state","BONUS_PENDING_"+type,"current_bet",0,"bonus_type",type,
                "bonus_payout_count",0,"lamp_on",0,"notice_state","NONE"));
        return new Release(session,state.release(nextBig,nextReg,"STOCK_RELEASE_"+type));
    }

    private Session restoreOrRelease(Session raw,Machine machine,JugglerGodRuntime state,
                                     String suspendedType,int suspendedCount,boolean suspendedEnded){
        if(!suspendedEnded){
            return withRuntime(rewrite(raw,Map.of(
                    "game_state",suspendedType+"_READY","current_bet",0,"bonus_type",suspendedType,
                    "bonus_payout_count",suspendedCount,"lamp_on",0,"notice_state","NONE")),
                    state.clearInterrupt("BONUS_RESUMED"));
        }
        JugglerGodRuntime cleared=state.clearInterrupt("BONUS_FINISHED_WITH_STOCK");
        Release release=beginRelease(raw,cleared);
        return withRuntime(release.session(),release.runtime());
    }

    private static JugglerGodRuntime loadRuntime(Session session,JugglerGodRuntime fallback){
        JsonObject ms=session.machineState();
        return ms!=null&&ms.has("jgMode")?JugglerGodRuntime.fromJson(ms.toString()):fallback;
    }

    private JugglerGodRuntime afterBonus(Machine machine,JugglerGodRuntime state){
        return JugglerGodTransitions.afterBonus(
                state,machine.setting(),random.gameplay(machine.id()),
                normalBigToHeavenPpm,normalRegToHeavenPpm,heavenToHeavenPpm);
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

    private static Session rewrite(Session session,Map<String,Object> changes){
        var values=new LinkedHashMap<>(session.snapshot());
        values.putAll(changes);
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

    @Override public Optional<Envelope> resume(Session saved,long sentNanos){
        Optional<Envelope> resumed=delegate.resume(saved,sentNanos);
        if(resumed.isEmpty())return resumed;
        JsonObject machineState=saved.machineState();
        if(machineState==null)return resumed;
        JugglerGodRuntime runtime=JugglerGodRuntime.fromJson(machineState.toString());
        if(!runtime.godFreeze())return resumed;
        Envelope packet=resumed.get();
        JsonObject body=packet.payload().deepCopy();body.addProperty("godFreeze",true);
        return Optional.of(new Envelope(packet.protocol(),packet.packetType(),body));
    }
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
