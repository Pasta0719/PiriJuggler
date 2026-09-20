package jp.pirijuggler.paper.game.god;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.*;
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
    private static final double NORMAL_THREE_MEDAL_PAYOUT_RATE = (3.0 - 50.0 / 30.8) / 3.0;
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
        if(action!=PacketType.SPACE_ACTION)throw new DomainException("INVALID_STATE");

        var values=new LinkedHashMap<>(before.snapshot());
        values.put("last_client_sequence",sequence);values.put("last_activity",now);
        var packets=new ArrayList<Envelope>();
        var rng=random.gameplay(machine.id());
        var balance=new GameRules.Balance(Math.toIntExact(before.number("credit")),before.number("held_medals"));
        var bet=balance.bet(3);
        if(!bet.accepted()){
            packets.add(ErrorPackets.rejected(sequence,ErrorCode.NOT_ENOUGH_CREDIT));
            return transition(before,new Session(values),0,0,0,false,packets,null);
        }
        putBalance(values,bet.balance());

        GodMachineRuntime runtime=GodMachineRuntime.fromJson(machine.runtimeJson());
        Step step=step(runtime,machine.setting(),rng);
        GameRules.Balance paid=new GameRules.Balance(((Number)values.get("credit")).intValue(),((Number)values.get("held_medals")).longValue()).payout(step.payout);
        putBalance(values,paid);
        values.put("game_state","SEATED_READY");
        values.put("current_bet",0);
        values.put("pay_display",step.payout);
        values.put("machine_state_json",step.runtime.gameplay().toJsonString());
        values.put("display_left_stop",rng.nextInt(21));
        values.put("display_center_stop",rng.nextInt(21));
        values.put("display_right_stop",rng.nextInt(21));
        packets.add(accepted(sequence));
        if(step.payout>0)packets.add(Envelope.current(PacketType.PAYOUT,new JsonObject()));
        return transition(before,new Session(values),3,step.payout,1,true,packets,step.runtime.toJsonString());
    }

    private record Step(GodMachineRuntime runtime,int payout) {}

    private Step step(GodMachineRuntime r,int setting,RandomGenerator rng){
        GodSessionState s=r.gameplay();
        return switch(s.phase()){
            case NORMAL -> normal(r,s,setting,rng);
            case GG -> gg(r,s,setting,rng);
            case G_ZONE -> gZone(r,s,rng);
            case SGG -> sgg(r,s,rng);
            case Z_ZONE -> zZone(r,s,rng);
            case Z_GAME -> zGame(r,s,rng);
        };
    }

    private Step normal(GodMachineRuntime r,GodSessionState s,int setting,RandomGenerator rng){
        GodRole role=drawRole(rng);
        int payout=rng.nextDouble()<NORMAL_THREE_MEDAL_PAYOUT_RATE?3:0;
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
            return enterGg(nextBase,s,role,GodLoopType.D,1,0,rng);

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
                GodSessionState zs=copy(s,GodPhase.Z_ZONE,0,1,loop,0,0,0,s.sggSetNumber(),GodProductionSpec.Z_ZONE_BASE_GAMES,0,0,s.totalGodGames()+1,"GAIA_Z","0");
                return new Step(resetNormal(new GodMachineRuntime(nextBase.frontMode(),nextBase.normalGamesSinceGg(),nextBase.blue7History(),nextBase.yellow7History(),nextBase.gaiaMode(),nextBase.gaiaBellCount(),nextBase.gaiaTarget(),false,0,nextBase.ceilingTarget(),nextBase.totalNormalGames(),s),zs),payout);
            }
            GodMachineRuntime hitBase=new GodMachineRuntime(nextBase.frontMode(),nextBase.normalGamesSinceGg(),nextBase.blue7History(),nextBase.yellow7History(),nextBase.gaiaMode(),nextBase.gaiaBellCount(),nextBase.gaiaTarget(),false,0,nextBase.totalNormalGames(),s);
            return enterGg(hitBase,s,role,loop,1,payout,rng);
        }
        if(gaiaActive&&gaiaGuarantee==0&&GodGaiaRules.exitsAfterGuarantee(role,rng)){
            nextBase=new GodMachineRuntime(nextBase.frontMode(),nextBase.normalGamesSinceGg(),nextBase.blue7History(),nextBase.yellow7History(),nextBase.gaiaMode(),nextBase.gaiaBellCount(),nextBase.gaiaTarget(),false,0,nextBase.totalNormalGames(),s);
        }
        GodSessionState ns=copy(s,GodPhase.NORMAL,0,0,null,0,0,0,0,0,0,0,s.totalGodGames()+1,"NORMAL",role.name());
        return new Step(nextBase.withGameplay(ns),payout);
    }

    private Step gg(GodMachineRuntime r,GodSessionState s,int setting,RandomGenerator rng){
        GodRole role=drawRole(rng);int payout=10;
        int remaining=Math.max(0,s.ggGamesRemaining()-1),stocks=s.queuedGgStocks();
        GodLoopType loop=s.loopType();

        if(role==GodRole.GOD){
            stocks+=GodProductionSpec.GOD_GUARANTEED_GG_SETS;
            stocks+=rollLoop(GodLoopType.D,rng);
        }else if(role==GodRole.RED7){
            GodSessionState ns=copy(s,GodPhase.SGG,remaining,stocks,loop,0,sggLength(false,role,rng),0,s.sggSetNumber()+1,0,0,0,s.totalGodGames()+1,"RED7_SGG",role.name());
            return new Step(r.withGameplay(ns),payout);
        }else{
            if(role==GodRole.SP&&rng.nextDouble()<0.50){stocks+=1+rollLoop(GodLoopType.D,rng);}
            double stockChance=ggStockChance(r.frontMode(),role);
            if(stockChance>0&&rng.nextDouble()<stockChance)stocks++;
        }

        if(remaining>0){
            String event=role==GodRole.GOD?"GOD_IN_GG":"GG";
            GodSessionState ns=copy(s,GodPhase.GG,remaining,stocks,loop,0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,event,role.name());
            return new Step(r.withGameplay(ns),payout);
        }
        GodSessionState ns=copy(s,GodPhase.G_ZONE,0,stocks,loop,GodProductionSpec.G_ZONE_MAX_GAMES,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"G_ZONE",role.name());
        return new Step(r.withGameplay(ns),payout);
    }

    private Step gZone(GodMachineRuntime r,GodSessionState s,RandomGenerator rng){
        int payout=3;
        int stocks=s.queuedGgStocks();
        if(stocks>0){
            stocks--;
            GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,stocks,s.loopType(),0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"GG_CONTINUE","STOCK");
            return new Step(r.withGameplay(ns),payout);
        }
        int left=Math.max(0,s.gZoneGamesRemaining()-1);
        if(left>0){
            GodSessionState ns=copy(s,GodPhase.G_ZONE,0,0,s.loopType(),left,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"G_ZONE","NONE");
            return new Step(r.withGameplay(ns),payout);
        }
        GodSessionState ns=copy(s,GodPhase.NORMAL,0,0,null,0,0,0,0,0,0,0,s.totalGodGames()+1,"NORMAL_RETURN","NONE");
        GodMachineRuntime nr=new GodMachineRuntime(r.frontMode(),0,0,0,r.gaiaMode(),r.gaiaBellCount(),r.gaiaTarget(),false,0,r.totalNormalGames(),ns);
        return new Step(nr,payout);
    }

    private Step sgg(GodMachineRuntime r,GodSessionState s,RandomGenerator rng){
        GodRole role=drawRole(rng);int payout=10;
        int rem=Math.max(0,s.sggGamesRemaining()-1),cont=s.sggContinuationStocks();
        if(role==GodRole.SP)cont+=3;
        else if((role==GodRole.MIDDLE_BLUE7||role==GodRole.RISING_YELLOW7)&&rng.nextDouble()<0.102)cont++;
        else if(role==GodRole.MIDDLE_YELLOW7&&rng.nextDouble()<0.50)cont++;

        if(rem>0){
            GodSessionState ns=copy(s,GodPhase.SGG,s.ggGamesRemaining(),s.queuedGgStocks(),s.loopType(),0,rem,cont,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"SGG",role.name());
            return new Step(r.withGameplay(ns),payout);
        }
        boolean keep=cont>0||rng.nextDouble()<0.753;
        if(keep){
            int nextCont=Math.max(0,cont-1),set=s.sggSetNumber()+1;
            int len=sggLength(set%5==0,GodRole.MISS,rng);
            GodSessionState ns=copy(s,GodPhase.SGG,s.ggGamesRemaining(),s.queuedGgStocks(),s.loopType(),0,len,nextCont,set,0,0,0,s.totalGodGames()+1,"SGG_CONTINUE","S");
            return new Step(r.withGameplay(ns),payout);
        }
        if(s.ggGamesRemaining()>0){
            GodSessionState ns=copy(s,GodPhase.GG,s.ggGamesRemaining(),s.queuedGgStocks(),s.loopType(),0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"SGG_END_GG","NONE");
            return new Step(r.withGameplay(ns),payout);
        }
        int stocks=Math.max(1,s.queuedGgStocks());
        GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,stocks-1,s.loopType(),0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"SGG_END_GG","NONE");
        return new Step(r.withGameplay(ns),payout);
    }

    private Step zZone(GodMachineRuntime r,GodSessionState s,RandomGenerator rng){
        int payout=10;
        boolean yellow=rng.nextDouble()<1.0/GodProductionSpec.Z_ZONE_YELLOW7_DENOMINATOR;
        int left=s.zZoneGamesRemaining(),streak=s.zYellowStreak();
        if(yellow){
            streak++;
            if(streak>=GodProductionSpec.Z_ZONE_REQUIRED_YELLOW7_STREAK){
                GodSessionState ns=copy(s,GodPhase.Z_GAME,0,s.queuedGgStocks()+1,s.loopType(),0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"Z_GAME","YELLOW7");
                return new Step(r.withGameplay(ns),payout);
            }
        }else{
            left=Math.max(0,left-1);streak=0;
        }
        if(left==0){
            int stocks=s.queuedGgStocks();
            GodLoopType loop=s.loopType();
            if(s.zYellowStreak()==0&&rng.nextDouble()<GodProductionSpec.Z_ZONE_ZERO_YELLOW_SPECIAL_D_RATE)loop=GodLoopType.D;
            if(loop!=null)stocks+=rollLoop(loop,rng);
            stocks=Math.max(1,stocks);
            GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,stocks-1,null,0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"Z_FAIL_GG","NONE");
            return new Step(r.withGameplay(ns),payout);
        }
        GodSessionState ns=copy(s,GodPhase.Z_ZONE,0,s.queuedGgStocks(),s.loopType(),0,0,0,s.sggSetNumber(),left,streak,0,s.totalGodGames()+1,"Z_ZONE",yellow?"YELLOW7":"MISS");
        return new Step(r.withGameplay(ns),payout);
    }

    private Step zGame(GodMachineRuntime r,GodSessionState s,RandomGenerator rng){
        int payout=10;
        boolean yellow=rng.nextDouble()<1.0/GodProductionSpec.Z_ZONE_YELLOW7_DENOMINATOR;
        if(yellow){
            GodSessionState ns=copy(s,GodPhase.Z_GAME,0,s.queuedGgStocks()+1,s.loopType(),0,0,0,s.sggSetNumber(),0,0,s.zGameStocks()+1,s.totalGodGames()+1,"Z_STOCK","YELLOW7");
            return new Step(r.withGameplay(ns),payout);
        }
        int stocks=Math.max(1,s.queuedGgStocks());
        GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,stocks-1,s.loopType(),0,0,0,s.sggSetNumber(),0,0,s.zGameStocks(),s.totalGodGames()+1,"Z_END_GG","NONE");
        return new Step(r.withGameplay(ns),payout);
    }

    private Step enterGod(GodMachineRuntime r,GodSessionState s,GodRole role,RandomGenerator rng){
        int queued=GodProductionSpec.GOD_GUARANTEED_GG_SETS-1+rollLoop(GodLoopType.D,rng);
        GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,queued,GodLoopType.D,0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"GOD","GOD");
        return new Step(resetNormal(r,ns),0);
    }

    private Step enterSgg(GodMachineRuntime r,GodSessionState s,GodRole role,RandomGenerator rng){
        GodSessionState ns=copy(s,GodPhase.SGG,0,1,GodLoopType.C,0,sggLength(false,role,rng),0,1,0,0,0,s.totalGodGames()+1,"RED7_SGG",role.name());
        return new Step(resetNormal(r,ns),0);
    }

    private Step enterGg(GodMachineRuntime r,GodSessionState s,GodRole role,GodLoopType loop,int guaranteed,int payout,RandomGenerator rng){
        int queued=Math.max(0,guaranteed-1)+rollLoop(loop,rng);
        GodSessionState ns=copy(s,GodPhase.GG,GodProductionSpec.GG_GAMES,queued,loop,0,0,0,s.sggSetNumber(),0,0,0,s.totalGodGames()+1,"GG_HIT",role.name());
        return new Step(resetNormal(r,ns),payout);
    }

    private Step ceiling(GodMachineRuntime r,GodSessionState s,GodRole role,RandomGenerator rng){
        double x=rng.nextDouble();GodLoopType loop;boolean z=false;
        if(x<0.167)loop=GodLoopType.A;else if(x<0.334)loop=GodLoopType.B;else if(x<0.501)loop=GodLoopType.C;else if(x<0.668)loop=GodLoopType.D;else{loop=GodLoopType.A;z=true;}
        if(z){
            GodSessionState ns=copy(s,GodPhase.Z_ZONE,0,1,loop,0,0,0,s.sggSetNumber(),GodProductionSpec.Z_ZONE_BASE_GAMES,0,0,s.totalGodGames()+1,"CEILING_Z","NONE");
            return new Step(resetNormal(r,ns),0);
        }
        return enterGg(r,s,role,loop,1,0,rng);
    }

    private static GodMachineRuntime resetNormal(GodMachineRuntime r,GodSessionState s){
        return new GodMachineRuntime(r.frontMode(),0,0,0,r.gaiaMode(),r.gaiaBellCount(),r.gaiaTarget(),false,0,r.totalNormalGames(),s);
    }

    private static GodSessionState copy(GodSessionState old,GodPhase phase,int gg,int stocks,GodLoopType loop,int gz,int sgg,int cont,int set,int zz,int zstreak,int zstocks,long total,String event,String role){
        return new GodSessionState(phase,gg,stocks,loop,gz,sgg,cont,set,zz,zstreak,zstocks,total,event,role);
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

    private static int sggLength(boolean fifth,GodRole trigger,RandomGenerator rng){
        double x=rng.nextDouble();
        if(trigger==GodRole.SP)return x<.5?50:100;
        if(fifth){if(x<.795)return 20;if(x<.931)return 30;if(x<.965)return 50;return 100;}
        if(x<.830)return 10;if(x<.944)return 20;if(x<.978)return 30;if(x<.989)return 50;return 100;
    }

    private static GodRole drawRole(RandomGenerator rng){
        double premium=rng.nextDouble();
        double pg=1.0/GodProductionSpec.GOD_DENOMINATOR, pr=1.0/GodProductionSpec.RED7_DENOMINATOR, ps=1.0/GodProductionSpec.SP_DENOMINATOR;
        if(premium<pg)return GodRole.GOD;if(premium<pg+pr)return GodRole.RED7;if(premium<pg+pr+ps)return GodRole.SP;
        GodRole[] roles={GodRole.MISS,GodRole.UPPER_BLUE7,GodRole.MIDDLE_BLUE7,GodRole.ORDERED_YELLOW7,GodRole.LOWER_YELLOW7,GodRole.RISING_YELLOW7,GodRole.MIDDLE_YELLOW7,GodRole.COMMON_YELLOW7,GodRole.GAIA_BELL,GodRole.RED7_FAKE};
        double sum=0;for(GodRole role:roles)sum+=GodKisekiRoleTable.referenceProbability(role);
        double x=rng.nextDouble()*sum,c=0;for(GodRole role:roles){c+=GodKisekiRoleTable.referenceProbability(role);if(x<c)return role;}return GodRole.MISS;
    }

    private static Envelope accepted(long sequence){JsonObject b=new JsonObject();b.addProperty("clientSequence",sequence);b.addProperty("action",PacketType.SPACE_ACTION.name());return Envelope.current(PacketType.ACTION_ACCEPTED,b);}
    private static void putBalance(Map<String,Object> values,GameRules.Balance b){values.put("credit",b.credit());values.put("held_medals",b.held());}

    private static GameTransition transition(Session before,Session after,int bet,int payout,int spins,boolean finished,List<Envelope> packets,String runtime){
        return new GameTransition(UUID.randomUUID(),before,after,bet,payout,spins,finished,false,null,false,0,packets,List.of(),List.of(),runtime);
    }

    @Override public List<Envelope> committed(GameTransition a,long sentNanos){
        var out=new ArrayList<>(a.packets());out.add(Envelope.current(PacketType.PUBLIC_STATE,a.after().publicState()));return List.copyOf(out);
    }
    @Override public List<GameTransition.Scheduled> scheduled(GameTransition action){return List.of();}
    @Override public Optional<Envelope> resume(Session saved,long sentNanos){return Optional.empty();}
    @Override public Session capture(Session saved,long now){return saved;}
    @Override public void forget(UUID session){}
}
