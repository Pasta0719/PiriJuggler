package jp.pirijuggler.paper.game.god;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.GodReelStrip;
import jp.pirijuggler.common.reel.GodStopControl;
import jp.pirijuggler.common.reel.ReelMotion;
import jp.pirijuggler.paper.game.*;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.session.Session;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Playable server-authoritative Piri GOD engine.
 *
 * One SPACE_ACTION consumes and resolves one 3-medal game. This first playable
 * implementation keeps all machine-owned progression durable while the dedicated
 * reel-stop presentation is built independently on Fabric.
 */
public final class GodGameEngine implements GameEngine {
    private static final double[] TARGET_NORMAL_GG_DENOM = {0,573.7,466.8,507.8,334.2,439.6,266.5};
    private final RandomStreams random;

    public GodGameEngine(RandomStreams random) {
        this.random=Objects.requireNonNull(random);
    }

    @Override
    public GameTransition plan(Session before, Machine machine, PacketType action, long sequence,
                               long now, long receivedNanos, int ping, Integer clientPressedIndex) {
        if(before.lifecycle()!=Session.Lifecycle.ACTIVE)throw new DomainException("SESSION_MISMATCH");
        if(sequence<=before.sequence())throw new DomainException("SEQUENCE_OLD");
        return (before.state()==Session.GameState.SEATED_READY||before.state()==Session.GameState.REPLAY_READY)
                ? beginSpin(before,machine,action,sequence,now)
                : before.state()==Session.GameState.NORMAL_SPINNING
                    ? stopSpin(before,action,sequence,now,clientPressedIndex)
                    : rejected(before,action,sequence,now,ErrorCode.INVALID_STATE);
    }

    private GameTransition beginSpin(Session before,Machine machine,PacketType action,long sequence,long now){
        if(action!=PacketType.SPACE_ACTION)return rejected(before,action,sequence,now,ErrorCode.INVALID_STATE);
        var values=new LinkedHashMap<>(before.snapshot());
        values.put("last_client_sequence",sequence);values.put("last_activity",now);

        boolean replayStart=before.state()==Session.GameState.REPLAY_READY;
        var balance=new GameRules.Balance(Math.toIntExact(before.number("credit")),before.number("held_medals"));
        if(!replayStart){
            var bet=balance.bet(3);
            if(!bet.accepted())return rejected(before,action,sequence,now,ErrorCode.NOT_ENOUGH_CREDIT);
            putBalance(values,bet.balance());
        }

        GodMachineRuntime runtime=GodMachineRuntime.fromJson(machine.runtimeJson());
        RandomGenerator rng=random.gameplay(machine.id());
        Step step=step(runtime,machine.setting(),rng);
        String role=step.runtime().gameplay().lastRole();
        final GodRole internalRole;
        try { internalRole=GodRole.valueOf(role); }
        catch(RuntimeException invalidRole){ throw new DomainException("INVALID_STATE"); }
        double outcomeUnit=runtime.forcedRole()!=null?0.0:rng.nextDouble();
        GodRoleOutcome.Outcome outcome=GodRoleOutcome.resolve(internalRole,runtime.gameplay().phase(),outcomeUnit);

        JsonObject sessionState=runtime.gameplay().toJson();
        sessionState.add("_pendingRuntime",step.runtime().toJson());
        sessionState.addProperty("_pendingPayout");
        sessionState.addProperty("_pendingReplay",outcome.replay());
        sessionState.addProperty("_pendingRole",internalRole.name());
        sessionState.addProperty("_pendingDisplayRole",outcome.displayRole());
        GodPhase sourcePhase=runtime.gameplay().phase();
        if(internalRole==GodRole.GAIA_BELL){
            sessionState.addProperty("_pendingFirstReel",2);
            sessionState.addProperty("_pendingNavText","R");
        } else if(internalRole==GodRole.ORDERED_YELLOW7&&isAtLike(sourcePhase)&&outcome.payout()==15){
            int[][] orders={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
            int[] order=orders[rng.nextInt(orders.length)];
            var jsonOrder=new com.google.gson.JsonArray();
            for(int reel:order)jsonOrder.add(reel);
            sessionState.add("_pendingStopOrder",jsonOrder);
            sessionState.addProperty("_pendingNavText",navText(order));
        }

        String spin=UUID.randomUUID().toString();
        values.put("game_state","NORMAL_SPINNING");
        values.put("spin_id",spin);
        values.put("internal_role",role==null?"NONE":role);
        values.put("motion_profile","NORMAL");
        values.put("current_bet",3);
        values.put("pay_display",0);
        values.put("stopped_mask",0);
        values.put("phase_left",(double)before.number("display_left_stop"));
        values.put("phase_center",(double)before.number("display_center_stop"));
        values.put("phase_right",(double)before.number("display_right_stop"));
        values.put("machine_state_json",sessionState.toString());

        return transition(before,new Session(values),replayStart?0:3,0,0,false,true,0,
                List.of(accepted(action,sequence)),List.of(),null);
    }

    private GameTransition stopSpin(Session before,PacketType action,long sequence,long now,Integer clientPressedIndex){
        if(!Set.of(PacketType.SPACE_ACTION,PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT).contains(action))
            return rejected(before,action,sequence,now,ErrorCode.INVALID_STATE);
        JsonObject state=before.machineState();
        if(state==null||!state.has("_pendingRuntime"))throw new DomainException("SPIN_MISMATCH");

        int mask=(int)before.number("stopped_mask");
        int reel=switch(action){
            case STOP_LEFT -> 0;
            case STOP_CENTER -> 1;
            case STOP_RIGHT -> 2;
            case SPACE_ACTION -> spaceReel(state,mask);
            default -> -1;
        };
        if(reel<0)return rejected(before,action,sequence,now,ErrorCode.INVALID_STATE);
        int bit=1<<reel;
        if((mask&bit)!=0)return rejected(before,action,sequence,now,ErrorCode.ALREADY_STOPPED);
        int expected=expectedNextReel(state,mask);
        if(expected>=0&&reel!=expected)return rejected(before,action,sequence,now,ErrorCode.INVALID_STATE);

        int pressed=clientPressedIndex==null
                ? Math.floorMod((int)before.number("display_"+reelName(reel)+"_stop"),GodReelStrip.STOPS)
                : clientPressedIndex;
        if(pressed<0||pressed>=GodReelStrip.STOPS)return rejected(before,action,sequence,now,ErrorCode.SESSION_MISMATCH);

        String role=state.has("_pendingRole")?state.get("_pendingRole").getAsString():before.text("internal_role");
        String displayRole=state.has("_pendingDisplayRole")?state.get("_pendingDisplayRole").getAsString():role;
        int target=GodStopControl.targetFor(displayRole,reel,pressed);
        int slip=GodReelStrip.slip(pressed,target);
        int duration=ReelMotion.durationMs(slip);

        var values=new LinkedHashMap<>(before.snapshot());
        values.put("last_client_sequence",sequence);values.put("last_activity",now);
        values.put("display_"+reelName(reel)+"_stop",target);
        int nextMask=mask|bit;values.put("stopped_mask",nextMask);

        JsonObject stop=new JsonObject();
        stop.addProperty("spinId",before.text("spin_id"));
        stop.addProperty("reel",reelEnumName(reel));
        stop.addProperty("pressedIndex",pressed);
        stop.addProperty("stopIndex",target);
        stop.addProperty("slip",slip);
        stop.addProperty("durationMs",duration);
        stop.add("nextStopHints",stopHints(displayRole,nextMask,state));

        var packets=new ArrayList<Envelope>();
        packets.add(accepted(action,sequence));
        packets.add(Envelope.current(PacketType.REEL_STOP,stop));

        if(nextMask!=7)
            return transition(before,new Session(values),0,0,0,false,false,0,packets,List.of(),null);

        GodMachineRuntime finalRuntime=GodMachineRuntime.fromJson(state.getAsJsonObject("_pendingRuntime").toString());
        int payout=state.get("_pendingPayout").getAsInt();
        boolean replay=state.has("_pendingReplay")&&state.get("_pendingReplay").getAsBoolean();
        var current=new GameRules.Balance(Math.toIntExact(((Number)values.get("credit")).longValue()),((Number)values.get("held_medals")).longValue());
        putBalance(values,current.payout(payout));
        values.put("game_state",replay?"REPLAY_READY":"SEATED_READY");
        values.put("spin_id",null);values.put("internal_role",null);values.put("motion_profile",null);
        values.put("current_bet",replay?3:0);values.put("pay_display",payout);
        values.put("machine_state_json",finalRuntime.gameplay().toJsonString());

        var scheduled=new ArrayList<GameTransition.Scheduled>();
        if(payout>0)scheduled.add(new GameTransition.Scheduled(duration,Envelope.current(PacketType.PAYOUT,new JsonObject())));
        return transition(before,new Session(values),0,payout,1,true,false,duration,packets,scheduled,finalRuntime.toJsonString());
    }

    private GameTransition rejected(Session before,PacketType action,long sequence,long now,ErrorCode code){
        var values=new LinkedHashMap<>(before.snapshot());
        values.put("last_client_sequence",sequence);values.put("last_activity",now);
        return transition(before,new Session(values),0,0,0,false,false,0,
                List.of(ErrorPackets.rejected(sequence,code)),List.of(),null);
    }

    private static int nextReel(int mask){for(int i=0;i<3;i++)if((mask&(1<<i))==0)return i;return -1;}
    private static int spaceReel(JsonObject state,int mask){
        int expected=expectedNextReel(state,mask);
        return expected>=0?expected:nextReel(mask);
    }
    private static boolean isAtLike(GodPhase phase){
        return phase==GodPhase.GG||phase==GodPhase.SGG||phase==GodPhase.SGG_COMEBACK||
                phase==GodPhase.Z_ZONE||phase==GodPhase.Z_GAME;
    }
    private static int expectedNextReel(JsonObject state,int mask){
        if(state!=null&&state.has("_pendingStopOrder")){
            var order=state.getAsJsonArray("_pendingStopOrder");
            int step=Integer.bitCount(mask);
            return step<order.size()?order.get(step).getAsInt():-1;
        }
        if(mask==0){
            if(state!=null&&state.has("_pendingFirstReel"))return state.get("_pendingFirstReel").getAsInt();
            return 0;
        }
        return -1; // after a non-navigated first stop, either remaining reel is legal
    }
    private static String navText(int[] order){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<order.length;i++){
            if(i>0)b.append('-');
            b.append(new String[]{"L","C","R"}[order[i]]);
        }
        return b.toString();
    }
    private static String reelName(int reel){return new String[]{"left","center","right"}[reel];}
    private static String reelEnumName(int reel){return new String[]{"LEFT","CENTER","RIGHT"}[reel];}


    private record Step(GodMachineRuntime runtime) {}

    private Step step(GodMachineRuntime r,int setting,RandomGenerator rng){
        GodSessionState s=r.gameplay();
        return switch(s.phase()){
            case NORMAL -> normal(r,s,setting,rng);
            case GG -> gg(r,s,setting,rng);
            case G_ZONE -> gZone(r,s,rng);
            case SGG -> sgg(r,s,rng);
            case SGG_COMEBACK -> sggComeback(r,s,rng);
            case Z_ZONE -> zZone(r,s,rng);
            case Z_GAME -> zGame(r,s,rng);
        };
    }

    private Step normal(GodMachineRuntime r,GodSessionState s,int setting,RandomGenerator rng){
        GodRole role=drawRole(r,rng);
        int normalGames=r.normalGamesSinceGg()+1;
        int ceilingTarget=r.ceilingTarget()==0
                ? GodProductionSpec.chooseResetCeiling(rng.nextDouble())
                : r.ceilingTarget();
        int blue=isBlue(role)?r.blue7History()+1:role==GodRole.GAIA_BELL?r.blue7History()+1:0;
        int yellow=isYellow(role)?r.yellow7History()+1:role==GodRole.GAIA_BELL?r.yellow7History()+1:0;
        GodFrontMode mode=transitionMode(r.frontMode(),role,setting,rng);
        GodGaiaMode gaiaMode=r.gaiaMode();
        int gaiaTarget=r.gaiaTarget()==0?GodGaiaRules.chooseTarget(gaiaMode,rng):r.gaiaTarget();
        int gaiaCount=r.gaiaBellCount();
        boolean gaiaActive=r.gaiaStageActive();
        int gaiaGuarantee=r.gaiaStageGuarantee();

        if(role==GodRole.GAIA_BELL&&!gaiaActive){
            gaiaCount++;
            if(gaiaCount>=gaiaTarget){
                if(rng.nextDouble()<GodGaiaRules.stageEntryRate(gaiaMode)){gaiaActive=true;gaiaGuarantee=9;}
                gaiaMode=GodGaiaRules.nextMode(gaiaMode,rng);
                gaiaTarget=GodGaiaRules.chooseTarget(gaiaMode,rng);
                gaiaCount=0;
            }
        }
        if(gaiaActive){
            gaiaGuarantee=Math.max(0,gaiaGuarantee-1);
            gaiaGuarantee=GodGaiaRules.maybeExtendGuarantee(gaiaGuarantee,role,rng);
        }
        GodMachineRuntime nextBase=new GodMachineRuntime(mode,normalGames,blue,yellow,gaiaMode,gaiaCount,gaiaTarget,gaiaActive,gaiaGuarantee,ceilingTarget,r.totalNormalGames()+1,s);

        if(role==GodRole.GOD)return enterGod(nextBase,s,role,rng);
        if(role==GodRole.RED7)return enterSgg(nextBase,s,role,rng);
        if(role==GodRole.SP && rng.nextDouble()<GodProductionSpec.SP_STOCK_HIT_RATE_NORMAL_OR_GG)
            return enterGg(nextBase,s,role,GodLoopType.D,1,rng);

        boolean ceiling=normalGames>=ceilingTarget;
        if(ceiling)return ceiling(nextBase,s,role,rng);

        boolean history=gaiaActive?GodGaiaRules.historyHit(blue,yellow,rng):historyHit(blue,yellow,setting,rng);
        boolean roleHit=gaiaActive
                ? (GodGaiaRules.ggChance(role)>0&&rng.nextDouble()<GodGaiaRules.ggChance(role))
                : normalRoleHit(mode,role,setting,rng);
        boolean fitted=!gaiaActive&&!history&&!roleHit&&rng.nextDouble()<(1.0/TARGET_NORMAL_GG_DENOM[setting])*0.72;
        if(history||roleHit||fitted){
            GodLoopType loop=loopForMode(mode,rng);
            if(gaiaActive&&rng.nextDouble()<.15){
                GodSessionState zs=copy(s,GodPhase.Z_ZONE,0,1,loop,0,0,0,s.sggSetNumber(),GodProductionSpec.Z_ZONE_BASE_GAMES,0,0,s.totalGodGames()+1,"GAIA_Z",role.name());
                return new Step(resetNormal(new GodMachineRuntime(nextBase.frontMode(),nextBase.normalGamesSinceGg(),nextBase.blue7History(),nextBase.yellow7History(),nextBase.gaiaMode(),nextBase.gaiaBellCount(),nextBase.gaiaTarget(),false,0,nextBase.ceilingTarget(),nextBase.totalNormalGames(),s),zs));
            }
            GodMachineRuntime hitBase=new GodMachineRuntime(nextBase.frontMode(),nextBase.normalGamesSinceGg(),nextBase.blue7History(),nextBase.yellow7History(),nextBase.gaiaMode(),nextBase.gaiaBellCount(),nextBase.gaiaTarget(),false,0,nextBase.totalNormalGames(),s);
            return enterGg(hitBase,s,role,loop,1,rng);
        }
        if(gaiaActive&&gaiaGuarantee==0&&GodGaiaRules.exitsAfterGuarantee(role,rng)){
            nextBase=new GodMachineRuntime(nextBase.frontMode(),nextBase.normalGamesSinceGg(),nextBase.blue7History(),nextBase.yellow7History(),nextBase.gaiaMode(),nextBase.gaiaBellCount(),nextBase.gaiaTarget(),false,0,nextBase.totalNormalGames(),s);
        }
        GodSessionState ns=copy(s,GodPhase.NORMAL,0,0,null,0,0,0,0,0,0,0,s.totalGodGames()+1,"NORMAL",role.name());
        return new Step(nextBase.withGameplay(ns));
    }

    private Step gg(GodMachineRuntime r,GodSessionState s,int setting,RandomGenerator rng){
        GodRole role=drawRole(r,rng);
        int remaining=Math.max(0,s.ggGamesRemaining()-1),stocks=s.queuedGgStocks();
        GodLoopType loop=s.loopType();

        if(role==GodRole.GOD){
            stocks+=GodProductionSpec.GOD_GUARANTEED_GG_SETS;
            stocks+=rollLoop(GodLoopType.D,rng);
        }else if(role==GodRole.RED7){
            GodSessionState ns=copy(s,GodPhase.SGG,remaining,stocks,loop,0,sggLength(false,role,rng),0,s.sggSetNumber()+1,0,0,0,s.totalGodGames()+1,"RED7_SGG",role.name());
            return new Step(r.withGameplay(ns));
        }else{
            if(role==GodRole.SP&&rng.nextDouble()<0.50){stocks+=1+rollLoop(GodLoopType.D,rng);}
            double stockChance=ggStockChance(r.frontMode(),role);
            if(stockChance>0&&rng.nextDouble()<stockChance)stocks++;
        }

        if(remaining>0){
            String event=role==GodRole.GOD?"GOD_IN_GG":"GG";
            GodSessionState ns=copy(s,GodPhase.GG,remaining,stocks,loop,0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,event,role.name());
            return new Step(r.withGameplay(ns));
        }
        int delay=stocks>0?gZoneAnnouncementDelay(rng):GodProductionSpec.G_ZONE_MAX_GAMES;
        GodSessionState ns=copy(s,GodPhase.G_ZONE,0,stocks,loop,delay,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"G_ZONE",role.name());
        return new Step(r.withGameplay(ns));
    }

    private Step gZone(GodMachineRuntime r,GodSessionState s,RandomGenerator rng){
        int stocks=s.queuedGgStocks();
        GodRole role=drawRole(r,rng);

        if(role==GodRole.GOD){
            stocks+=GodProductionSpec.GOD_GUARANTEED_GG_SETS+rollLoop(GodLoopType.D,rng);
            GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,stocks-1,GodLoopType.D,0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"GOD","GOD");
            return new Step(r.withGameplay(ns));
        }
        if(role==GodRole.RED7){
            GodSessionState ns=copy(s,GodPhase.SGG,0,stocks+1+rollLoop(GodLoopType.C,rng),GodLoopType.C,0,sggLength(false,role,rng),0,1,0,0,0,s.totalGodGames()+1,"RED7_SGG",role.name());
            return new Step(r.withGameplay(ns));
        }
        if(role==GodRole.SP&&rng.nextDouble()<GodProductionSpec.SP_STOCK_HIT_RATE_NORMAL_OR_GG)
            stocks+=1+rollLoop(GodLoopType.D,rng);

        if(stocks>0){
            double z=gZoneZChance(role);
            if(z>0&&rng.nextDouble()<z){
                GodSessionState ns=copy(s,GodPhase.Z_ZONE,0,stocks,s.loopType(),0,0,0,s.sggSetNumber(),GodProductionSpec.Z_ZONE_BASE_GAMES,0,0,s.totalGodGames()+1,"G_ZONE_TO_Z",role.name());
                return new Step(r.withGameplay(ns));
            }
        }

        int left=Math.max(0,s.gZoneGamesRemaining()-1);
        if(left>0){
            GodSessionState ns=copy(s,GodPhase.G_ZONE,0,stocks,s.loopType(),left,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"G_ZONE",role.name());
            return new Step(r.withGameplay(ns));
        }
        if(stocks>0){
            stocks--;
            GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,stocks,s.loopType(),0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"GG_CONTINUE",role.name());
            return new Step(r.withGameplay(ns));
        }
        GodSessionState ns=copy(s,GodPhase.NORMAL,0,0,null,0,0,0,0,0,0,0,s.totalGodGames()+1,"NORMAL_RETURN",role.name());
        GodMachineRuntime nr=new GodMachineRuntime(r.frontMode(),0,0,0,r.gaiaMode(),r.gaiaBellCount(),r.gaiaTarget(),false,0,r.ceilingTarget(),r.totalNormalGames(),ns);
        return new Step(nr);
    }

    private Step sgg(GodMachineRuntime r,GodSessionState s,RandomGenerator rng){
        GodRole role=drawRole(r,rng);
        int rem=Math.max(0,s.sggGamesRemaining()-1),cont=s.sggContinuationStocks();
        if(role==GodRole.SP)cont+=3;
        else if((role==GodRole.MIDDLE_BLUE7||role==GodRole.RISING_YELLOW7)&&rng.nextDouble()<0.102)cont++;
        else if(role==GodRole.MIDDLE_YELLOW7&&rng.nextDouble()<0.50)cont++;

        if(rem>0){
            GodSessionState ns=copy(s,GodPhase.SGG,s.ggGamesRemaining(),s.queuedGgStocks(),s.loopType(),0,rem,cont,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"SGG",role.name());
            return new Step(r.withGameplay(ns));
        }
        if(cont>0){
            int nextCont=cont-1,set=s.sggSetNumber()+1;
            int len=sggLength(set%5==0,GodRole.MISS,rng);
            GodSessionState ns=copy(s,GodPhase.SGG,s.ggGamesRemaining(),s.queuedGgStocks(),s.loopType(),0,len,nextCont,set,0,0,0,s.totalGodGames()+1,"SGG_STOCK_CONTINUE",role.name());
            return new Step(r.withGameplay(ns));
        }
        GodSessionState ns=copy(s,GodPhase.SGG_COMEBACK,s.ggGamesRemaining(),s.queuedGgStocks(),s.loopType(),0,3,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"SGG_COMEBACK",role.name());
        return new Step(r.withGameplay(ns));
    }

    private Step sggComeback(GodMachineRuntime r,GodSessionState s,RandomGenerator rng){
        GodRole role=drawRole(r,rng);
        boolean rare=role==GodRole.MIDDLE_BLUE7||role==GodRole.RISING_YELLOW7||role==GodRole.MIDDLE_YELLOW7||
                role==GodRole.COMMON_YELLOW7||role==GodRole.SP||role==GodRole.RED7||role==GodRole.GOD;
        boolean success=rare||rng.nextDouble()<0.344;
        if(success){
            int set=s.sggSetNumber()+1;
            int len=sggLength(set%5==0,role,rng);
            int cont=role==GodRole.SP?3:0;
            GodSessionState ns=copy(s,GodPhase.SGG,s.ggGamesRemaining(),s.queuedGgStocks(),s.loopType(),0,len,cont,set,0,0,0,s.totalGodGames()+1,"SGG_COMEBACK_HIT",role.name());
            return new Step(r.withGameplay(ns));
        }
        int left=Math.max(0,s.sggGamesRemaining()-1);
        if(left>0){
            GodSessionState ns=copy(s,GodPhase.SGG_COMEBACK,s.ggGamesRemaining(),s.queuedGgStocks(),s.loopType(),0,left,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"SGG_COMEBACK",role.name());
            return new Step(r.withGameplay(ns));
        }
        if(s.ggGamesRemaining()>0){
            GodSessionState ns=copy(s,GodPhase.GG,s.ggGamesRemaining(),s.queuedGgStocks(),s.loopType(),0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"SGG_END_GG",role.name());
            return new Step(r.withGameplay(ns));
        }
        int stocks=Math.max(1,s.queuedGgStocks());
        GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,stocks-1,s.loopType(),0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"SGG_END_GG",role.name());
        return new Step(r.withGameplay(ns));
    }

    private Step zZone(GodMachineRuntime r,GodSessionState s,RandomGenerator rng){
        boolean yellow=rng.nextDouble()<1.0/GodProductionSpec.Z_ZONE_YELLOW7_DENOMINATOR;
        int left=s.zZoneGamesRemaining(),streak=s.zYellowStreak();
        if(yellow){
            streak++;
            if(streak>=GodProductionSpec.Z_ZONE_REQUIRED_YELLOW7_STREAK){
                GodSessionState ns=copy(s,GodPhase.Z_GAME,0,s.queuedGgStocks()+1,s.loopType(),0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"Z_GAME","ORDERED_YELLOW7");
                return new Step(r.withGameplay(ns));
            }
        }else{
            left=Math.max(0,left-1);streak=0;
        }
        if(left==0){
            int stocks=s.queuedGgStocks();
            GodLoopType loop=s.loopType();
            if(s.zYellowCount()==0&&rng.nextDouble()<GodProductionSpec.Z_ZONE_ZERO_YELLOW_SPECIAL_D_RATE)loop=GodLoopType.D;
            if(loop!=null)stocks+=rollLoop(loop,rng);
            stocks=Math.max(1,stocks);
            GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,stocks-1,null,0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"Z_FAIL_GG","MISS");
            return new Step(r.withGameplay(ns));
        }
        GodSessionState ns=copy(s,GodPhase.Z_ZONE,0,s.queuedGgStocks(),s.loopType(),0,0,0,s.sggSetNumber(),left,streak,0,s.totalGodGames()+1,"Z_ZONE",yellow?"ORDERED_YELLOW7":"MISS");
        return new Step(r.withGameplay(ns));
    }

    private Step zGame(GodMachineRuntime r,GodSessionState s,RandomGenerator rng){
        boolean yellow=rng.nextDouble()<1.0/GodProductionSpec.Z_ZONE_YELLOW7_DENOMINATOR;
        if(yellow){
            GodSessionState ns=copy(s,GodPhase.Z_GAME,0,s.queuedGgStocks()+1,s.loopType(),0,0,0,s.sggSetNumber(),0,0,s.zGameStocks()+1,s.totalGodGames()+1,"Z_STOCK","ORDERED_YELLOW7");
            return new Step(r.withGameplay(ns));
        }
        int stocks=Math.max(1,s.queuedGgStocks());
        GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,stocks-1,s.loopType(),0,0,0,s.sggSetNumber(),0,0,s.zGameStocks(),s.totalGodGames()+1,"Z_END_GG","MISS");
        return new Step(r.withGameplay(ns));
    }

    private Step enterGod(GodMachineRuntime r,GodSessionState s,GodRole role,RandomGenerator rng){
        int queued=GodProductionSpec.GOD_GUARANTEED_GG_SETS-1+rollLoop(GodLoopType.D,rng);
        GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,queued,GodLoopType.D,0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"GOD","GOD");
        return new Step(resetNormal(r,ns));
    }

    private Step enterSgg(GodMachineRuntime r,GodSessionState s,GodRole role,RandomGenerator rng){
        GodSessionState ns=copy(s,GodPhase.SGG,0,1+rollLoop(GodLoopType.C,rng),GodLoopType.C,0,sggLength(false,role,rng),0,1,0,0,0,s.totalGodGames()+1,"RED7_SGG",role.name());
        return new Step(resetNormal(r,ns));
    }

    private Step enterGg(GodMachineRuntime r,GodSessionState s,GodRole role,GodLoopType loop,int guaranteed,RandomGenerator rng){
        int queued=Math.max(0,guaranteed-1)+rollLoop(loop,rng);
        GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,queued,loop,0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"GG_HIT",role.name());
        return new Step(resetNormal(r,ns));
    }

    private Step ceiling(GodMachineRuntime r,GodSessionState s,GodRole role,RandomGenerator rng){
        double x=rng.nextDouble();GodLoopType loop;boolean z=false;
        if(x<0.167)loop=GodLoopType.A;else if(x<0.334)loop=GodLoopType.B;else if(x<0.501)loop=GodLoopType.C;else if(x<0.668)loop=GodLoopType.D;else{loop=GodLoopType.A;z=true;}
        if(z){
            GodSessionState ns=copy(s,GodPhase.Z_ZONE,0,1,loop,0,0,0,s.sggSetNumber(),GodProductionSpec.Z_ZONE_BASE_GAMES,0,0,s.totalGodGames()+1,"CEILING_Z",role.name());
            return new Step(resetNormal(r,ns));
        }
        return enterGg(r,s,role,loop,1,rng);
    }

    private static GodMachineRuntime resetNormal(GodMachineRuntime r,GodSessionState s){
        return new GodMachineRuntime(r.frontMode(),0,0,0,r.gaiaMode(),r.gaiaBellCount(),r.gaiaTarget(),false,0,r.totalNormalGames(),s);
    }

    private static GodSessionState copy(GodSessionState old,GodPhase phase,int gg,int stocks,GodLoopType loop,int gz,int sgg,int cont,int set,int zz,int zstreak,int zstocks,long total,String event,String role){
        int zYellowCount=0;
        if(phase==GodPhase.Z_ZONE){
            zYellowCount=old.phase()==GodPhase.Z_ZONE?old.zYellowCount():0;
            if("ORDERED_YELLOW7".equals(role))zYellowCount++;
        }
        return new GodSessionState(phase,gg,stocks,loop,gz,sgg,cont,set,zz,zstreak,zYellowCount,zstocks,total,event,role);
    }

    private static boolean isBlue(GodRole r){return r==GodRole.UPPER_BLUE7||r==GodRole.MIDDLE_BLUE7;}
    private static boolean isYellow(GodRole r){return r==GodRole.ORDERED_YELLOW7||r==GodRole.LOWER_YELLOW7||r==GodRole.RISING_YELLOW7||r==GodRole.MIDDLE_YELLOW7||r==GodRole.COMMON_YELLOW7;}

    private static boolean historyHit(int blue,int yellow,int setting,RandomGenerator rng){
        if(yellow>=5||blue>=5)return true;
        if(yellow==4&&rng.nextDouble()<0.203)return true;
        if(yellow==3&&rng.nextDouble()<0.004)return true;
        boolean even=setting%2==0;
        if(blue==4&&rng.nextDouble()<(even?0.50:0.332))return true;
        return blue==3&&rng.nextDouble()<(even?0.102:0.012);
    }

    private static boolean normalRoleHit(GodFrontMode mode,GodRole role,int setting,RandomGenerator rng){
        double p=0;
        boolean common=role==GodRole.MISS||role==GodRole.UPPER_BLUE7||role==GodRole.RED7_FAKE||role==GodRole.ORDERED_YELLOW7||role==GodRole.GAIA_BELL;
        if(mode==GodFrontMode.HEAVEN){
            if(common)p=0.034;else if(role==GodRole.MIDDLE_BLUE7)p=0.40;else if(role==GodRole.RISING_YELLOW7)p=0.60;else if(role==GodRole.MIDDLE_YELLOW7)p=0.80;
        }else if(mode==GodFrontMode.SUPER_HEAVEN){
            if(common)p=0.054;else if(role==GodRole.MIDDLE_BLUE7)p=0.75;else if(role==GodRole.RISING_YELLOW7)p=0.80;else if(role==GodRole.MIDDLE_YELLOW7)p=0.90;
        }else{
            boolean normal=mode==GodFrontMode.NORMAL;
            if(common){
                double[] low={0,.0001,.0001,.0001,.0002,.0001,.0004};
                double[] mid={0,.0001,.0002,.0001,.0003,.0001,.0004};
                p=(normal?mid:low)[setting];
            } else if(role==GodRole.MIDDLE_BLUE7)p=normal?0.003:0.002;
            else if(role==GodRole.RISING_YELLOW7)p=normal?0.035:0.007;
            else if(role==GodRole.MIDDLE_YELLOW7)p=normal?0.15:0.075;
        }
        return p>0&&rng.nextDouble()<p;
    }

    private static double ggStockChance(GodFrontMode mode,GodRole role){
        int m=switch(mode){case LOW_A,LOW_B->0;case NORMAL->1;case HEAVEN_PREP->2;case HEAVEN->3;case SUPER_HEAVEN->4;};
        if(role==GodRole.MISS)return new double[]{0,.002,.002,.004,.07}[m];
        if(role==GodRole.MIDDLE_BLUE7)return new double[]{0,0,0,.012,.332}[m];
        if(role==GodRole.RISING_YELLOW7)return new double[]{0,.012,.031,.152,.50}[m];
        if(role==GodRole.MIDDLE_YELLOW7)return new double[]{.102,.188,.25,.50,.801}[m];
        return 0;
    }

    private static GodLoopType loopForMode(GodFrontMode mode,RandomGenerator rng){
        double x=rng.nextDouble();
        if(mode==GodFrontMode.SUPER_HEAVEN)return x<.75?GodLoopType.C:GodLoopType.D;
        if(mode==GodFrontMode.HEAVEN){
            if(x<.25)return GodLoopType.A;if(x<.50)return GodLoopType.B;if(x<.969)return GodLoopType.C;return GodLoopType.D;
        }
        return GodLoopType.A;
    }

    private static int rollLoop(GodLoopType loop,RandomGenerator rng){
        if(loop==null)return 0;int n=0;while(n<200&&rng.nextDouble()<loop.continuationRate())n++;return n;
    }

    private static GodFrontMode transitionMode(GodFrontMode mode,GodRole role,int setting,RandomGenerator rng){
        double up=switch(role){
            case MIDDLE_YELLOW7->.70;case RISING_YELLOW7->.30;case MIDDLE_BLUE7->.22;case COMMON_YELLOW7->.12;case RED7_FAKE->.06;case SP,RED7,GOD->.90;default->.004*setting;
        };
        int idx=mode.ordinal();
        if(rng.nextDouble()<up)return GodFrontMode.values()[Math.min(GodFrontMode.values().length-1,idx+(role==GodRole.MIDDLE_YELLOW7?2:1))];
        if(idx>0&&rng.nextDouble()<.012)return GodFrontMode.values()[idx-1];
        return mode;
    }

    private static int gZoneAnnouncementDelay(RandomGenerator rng){
        double x=rng.nextDouble();
        if(x<.078)return 1;if(x<.156)return 2;if(x<.234)return 3;if(x<.312)return 4;return 5;
    }

    private static double gZoneZChance(GodRole role){
        return switch(role){
            case UPPER_BLUE7,RED7_FAKE -> .001;
            case MIDDLE_BLUE7 -> .040;
            case RISING_YELLOW7 -> .180;
            case MIDDLE_YELLOW7 -> .250;
            case SP -> .300;
            default -> 0.0;
        };
    }

    private static int sggLength(boolean fifth,GodRole trigger,RandomGenerator rng){
        double x=rng.nextDouble();
        if(trigger==GodRole.SP)return x<.5?50:100;
        if(fifth){if(x<.795)return 20;if(x<.931)return 30;if(x<.965)return 50;return 100;}
        if(x<.830)return 10;if(x<.944)return 20;if(x<.978)return 30;if(x<.989)return 50;return 100;
    }

    private static GodRole drawRole(GodMachineRuntime runtime,RandomGenerator rng){
        if(runtime.forcedRole()!=null){
            try{return GodRole.valueOf(runtime.forcedRole());}
            catch(IllegalArgumentException invalid){throw new DomainException("INVALID_STATE");}
        }

        // Piri lock: GOD is an independent exact 1/8192 draw.
        // RED7 and SP are separate draws; simultaneous hits use GOD > RED7 > SP precedence.
        boolean god=rng.nextInt(GodProductionSpec.GOD_DENOMINATOR)==0;
        boolean red7=rng.nextInt(GodProductionSpec.RED7_DENOMINATOR)==0;
        boolean sp=rng.nextInt(GodProductionSpec.SP_DENOMINATOR)==0;
        if(god)return GodRole.GOD;if(red7)return GodRole.RED7;if(sp)return GodRole.SP;
        GodRole[] roles={GodRole.MISS,GodRole.UPPER_BLUE7,GodRole.MIDDLE_BLUE7,GodRole.ORDERED_YELLOW7,GodRole.LOWER_YELLOW7,GodRole.RISING_YELLOW7,GodRole.MIDDLE_YELLOW7,GodRole.COMMON_YELLOW7,GodRole.GAIA_BELL,GodRole.RED7_FAKE};
        double sum=0;for(GodRole role:roles)sum+=GodKisekiRoleTable.referenceProbability(role);
        double x=rng.nextDouble()*sum,c=0;for(GodRole role:roles){c+=GodKisekiRoleTable.referenceProbability(role);if(x<c)return role;}return GodRole.MISS;
    }

    private static Envelope accepted(PacketType action,long sequence){
        JsonObject b=new JsonObject();b.addProperty("clientSequence",sequence);b.addProperty("action",action.name());
        return Envelope.current(PacketType.ACTION_ACCEPTED,b);
    }
    private static void putBalance(Map<String,Object> values,GameRules.Balance b){values.put("credit",b.credit());values.put("held_medals",b.held());}

    private static JsonObject stopHints(String role,int mask,JsonObject state){
        JsonObject all=new JsonObject();
        int expected=expectedNextReel(state,mask);
        for(int reel=0;reel<3;reel++){
            if((mask&(1<<reel))!=0)continue;
            if(expected>=0&&reel!=expected)continue;
            var choices=new com.google.gson.JsonArray();
            for(int pressed=0;pressed<GodReelStrip.STOPS;pressed++){
                int target=GodStopControl.targetFor(role,reel,pressed);
                int slip=GodReelStrip.slip(pressed,target);
                JsonObject item=new JsonObject();item.addProperty("stopIndex",target);item.addProperty("slip",slip);item.addProperty("durationMs",ReelMotion.durationMs(slip));
                choices.add(item);
            }
            all.add(reelName(reel),choices);
        }
        return all;
    }

    private static Envelope start(Session saved,String animation){
        JsonObject b=saved.identity();
        b.addProperty("spinId",saved.text("spin_id"));
        b.addProperty("mode","GOD");
        b.addProperty("animation",animation);
        JsonObject phases=new JsonObject();
        phases.addProperty("left",((Number)saved.snapshot().get("phase_left")).doubleValue());
        phases.addProperty("center",((Number)saved.snapshot().get("phase_center")).doubleValue());
        phases.addProperty("right",((Number)saved.snapshot().get("phase_right")).doubleValue());
        b.add("startPhase",phases);
        b.addProperty("stopEnableAfterMs","RESUME_NORMAL".equals(animation)?200:700);
        String role=saved.machineState()!=null&&saved.machineState().has("_pendingDisplayRole")
                ? saved.machineState().get("_pendingDisplayRole").getAsString()
                : saved.machineState()!=null&&saved.machineState().has("_pendingRole")
                    ? saved.machineState().get("_pendingRole").getAsString()
                    : saved.text("internal_role");
        JsonObject machineState=saved.machineState();
        b.add("stopHints",stopHints(role,(int)saved.number("stopped_mask"),machineState));
        if(machineState!=null&&machineState.has("_pendingNavText"))
            b.addProperty("godNav",machineState.get("_pendingNavText").getAsString());
        return Envelope.current(PacketType.SPIN_START,b);
    }

    private static GameTransition transition(Session before,Session after,int bet,int payout,int spins,boolean finished,
                                             boolean lever,long publicDelay,List<Envelope> packets,
                                             List<GameTransition.Scheduled> scheduled,String runtime){
        return new GameTransition(UUID.randomUUID(),before,after,bet,payout,spins,finished,lever,null,false,publicDelay,packets,List.of(),scheduled,runtime);
    }

    @Override public List<Envelope> committed(GameTransition a,long sentNanos){
        var out=new ArrayList<>(a.packets());
        if(a.publicDelayMs()==0)out.add(Envelope.current(PacketType.PUBLIC_STATE,a.after().publicState()));
        if(a.lever())out.add(start(a.after(),"NORMAL"));
        return List.copyOf(out);
    }
    @Override public List<GameTransition.Scheduled> scheduled(GameTransition action){
        var out=new ArrayList<GameTransition.Scheduled>();
        if(action.publicDelayMs()>0)out.add(new GameTransition.Scheduled(action.publicDelayMs(),Envelope.current(PacketType.PUBLIC_STATE,action.after().publicState())));
        out.addAll(action.scheduled());
        return List.copyOf(out);
    }
    @Override public Optional<Envelope> resume(Session saved,long sentNanos){
        return saved.state()==Session.GameState.NORMAL_SPINNING?Optional.of(start(saved,"RESUME_NORMAL")):Optional.empty();
    }
    @Override public Session capture(Session saved,long now){return saved;}
    @Override public void forget(UUID session){}
}
