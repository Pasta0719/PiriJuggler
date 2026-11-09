package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.reel.HyperDogReelControl;
import jp.pirijuggler.common.reel.Reel;
import jp.pirijuggler.paper.game.GameEngine;
import jp.pirijuggler.paper.game.GameTransition;
import jp.pirijuggler.paper.machine.DomainException;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.reel.HyperDogStopSession;
import jp.pirijuggler.paper.session.Session;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Additive Phase08 native Paper adapter. Production machine creation stays
 * MACHINE_NOT_RELEASED until owner reviews the real cabinet; test-only
 * HYPER_DOG placements can use the same GameEngine / durable SQLite lane.
 * No Fabric-side lottery and no changes to existing machines' adapters.
 */
public final class HyperDogGameEngine implements GameEngine {
    private final SecureRandom seed = new SecureRandom();

    @Override public GameTransition plan(Session before,Machine machine,PacketType action,
                                         long sequence,long now,long nanos,int ping,Integer pressed) {
        if(machine.type()!=jp.pirijuggler.paper.machine.MachineType.HYPER_DOG)
            throw new DomainException("INVALID_STATE");
        if(before.lifecycle()!=Session.Lifecycle.ACTIVE || before.machine()!=machine.id())
            throw new DomainException("SESSION_MISMATCH");
        if(sequence!=before.sequence()+1)throw new DomainException("SEQUENCE_OLD");
        if(action!=PacketType.SPACE_ACTION && action!=PacketType.STOP_LEFT &&
           action!=PacketType.STOP_CENTER && action!=PacketType.STOP_RIGHT)
            throw new DomainException("INVALID_STATE");

        HyperDogFullGame game=load(before,machine);
        JsonObject machineJson=before.machineState();
        HyperDogStopSession physical=machineJson!=null&&machineJson.has("reel")
                ?HyperDogStopSession.fromJson(machineJson.getAsJsonObject("reel")):null;
        List<Envelope> packets=new ArrayList<>();
        List<GameTransition.Scheduled> delayed=new ArrayList<>();
        long award=0;int payout=0;boolean finished=false;
        if(action==PacketType.SPACE_ACTION && physical==null){
            if(game.at()!=null &&
               (game.at().stage()==HyperDogAtGame.Stage.RED7_CONFIRM ||
                game.at().stage()==HyperDogAtGame.Stage.RAINBOW7_CONFIRM)){
                HyperDogReelControl.Role role=game.at().stage()==HyperDogAtGame.Stage.RED7_CONFIRM
                        ?HyperDogReelControl.Role.AT_ENTRY_RED7
                        :HyperDogReelControl.Role.AT_ENTRY_RAINBOW7;
                physical=beginPhysical(before,now,role);
                packets.add(accept(action,sequence));
                packets.add(physical.begin());
                scheduleAutomatic(physical,delayed);
            }else if(game.waitingForBet()){
                if(game.credit()<3)throw new DomainException("NOT_ENOUGH_CREDIT");
                game.bet();
                packets.add(accept(action,sequence));
            }else if(game.canLever()){
                String role=game.lever();
                HyperDogReelControl.Role physicalRole;
                if(game.at()!=null && game.at().pending()!=null &&
                   game.at().pending().stage()==HyperDogAtGame.Stage.UPPER_CZ){
                    physicalRole=game.at().pending().czSuccess()
                            ?HyperDogReelControl.Role.CZ_WIN:HyperDogReelControl.Role.CZ_LOSE;
                }else physicalRole=HyperDogReelControl.Role.valueOf(role);
                physical=beginPhysical(before,now,physicalRole);
                packets.add(accept(action,sequence));
                packets.add(physical.begin());
                scheduleAutomatic(physical,delayed);
            }else throw new DomainException("INVALID_STATE");
        }else if(physical!=null) {
            if(physical.snapshot().round().role()==HyperDogReelControl.Role.AT_ENTRY_RED7)
                throw new DomainException("INVALID_STATE"); // only scheduled Paper confirmation
            if(physical.complete())throw new DomainException("ALREADY_STOPPED");
            JsonObject request=before.identity();
            request.addProperty("clientSequence",sequence);
            if(pressed!=null)request.addProperty("pressedIndex",pressed);
            List<Envelope> changes=physical.receive(Envelope.current(action,request),now);
            if(changes.stream().noneMatch(e->e.packetType()==PacketType.ACTION_ACCEPTED)){
                Envelope rejected=changes.stream().filter(e->e.packetType()==PacketType.ACTION_REJECTED)
                        .findFirst().orElse(null);
                String code=rejected==null||!rejected.payload().has("errorCode")
                        ?"INVALID_STATE":rejected.payload().get("errorCode").getAsString();
                throw new DomainException(code);
            }
            packets.addAll(changes);
            if(physical.snapshot().round().role()==HyperDogReelControl.Role.AT_ENTRY_RAINBOW7){
                for(Envelope e:changes)if(e.packetType()==PacketType.REEL_STOP){
                    var reel=Reel.valueOf(e.payload().get("reel").getAsString());
                    game.stopRainbowSeven(reel.ordinal());
                }
            }
            if(physical.complete()){
                if(game.at()!=null&&game.at().stage()==HyperDogAtGame.Stage.RAINBOW7_CONFIRM)
                    throw new IllegalStateException("rainbow confirmation not settled");
                if(game.at()!=null &&
                   game.at().stage()==HyperDogAtGame.Stage.UPPER_INITIAL &&
                   physical.snapshot().round().role()==HyperDogReelControl.Role.AT_ENTRY_RAINBOW7) {
                    // Three physical rainbow stops are already consumed by stopRainbowSeven.
                }else{
                    if(game.at()!=null){
                        var current=game.at().pending();
                        if(current==null)throw new IllegalStateException("missing AT prepared round");
                        award=current.award();payout=current.payout();
                    }else{
                        var current=game.normal().pending();
                        if(current==null)throw new IllegalStateException("missing normal prepared round");
                        payout=current.payout();
                    }
                    game.finish();
                    finished=true;
                    if(payout>0)packets.add(Envelope.current(PacketType.PAYOUT,new JsonObject()));
                }
                physical=null;
            }
        }else throw new DomainException("INVALID_STATE");

        Session after=updated(before,game,physical,now,sequence);
        if(award>0){
            JsonObject publicView=publicState(after,game);
            long id=eventId(game);
            JsonArray queue=new JsonArray();
            JsonObject event=new JsonObject();event.addProperty("id",id);
            event.addProperty("kind","ADD");event.addProperty("amount",award);queue.add(event);
            publicView.add("displayEvents",queue);
            HyperDogPresentation presentation=HyperDogPresentation.choose(id,award,id*0x9e3779b97f4a7c15L);
            publicView.addProperty("flashEventId",id);
            publicView.addProperty("flashStartedEpochMs",now);
            publicView.addProperty("flashAward",award);
            publicView.addProperty("flashColor",presentation.finalColor().name());
            publicView.addProperty("newAward",award);
            packets.add(Envelope.current(PacketType.PUBLIC_STATE,publicView));
        }else packets.add(Envelope.current(PacketType.PUBLIC_STATE,publicState(after,game)));

        return new GameTransition(UUID.randomUUID(),before,after,0,0,0,finished,
                action==PacketType.SPACE_ACTION,null,false,0,
                packets,List.of(),delayed,null);
    }

    private static HyperDogStopSession beginPhysical(Session session,long now,HyperDogReelControl.Role role){
        return new HyperDogStopSession(session.id(),session.machine(),UUID.randomUUID(),
                role,now,new double[]{0,7,14});
    }
    private static void scheduleAutomatic(HyperDogStopSession session,
                                           List<GameTransition.Scheduled> scheduled){
        var role=session.snapshot().round().role();
        if(role!=HyperDogReelControl.Role.AT_ENTRY_RED7 &&
           role!=HyperDogReelControl.Role.CZ_WIN &&
           role!=HyperDogReelControl.Role.CZ_LOSE)return;
        var copy=HyperDogStopSession.fromJson(session.toJson());
        for(long delay:role==HyperDogReelControl.Role.AT_ENTRY_RED7
                ?new long[]{720}:new long[]{500,700}){
            for(var p:copy.tick(session.snapshot().startedEpochMs()+delay))
                scheduled.add(new GameTransition.Scheduled(delay,p));
        }
    }

    /** Paper scheduler calls this only for an untouched, mature RED7 confirmation. */
    public Optional<GameTransition> finalizeAutomaticRed7(Session before,Machine machine,long now){
        if(machine.type()!=jp.pirijuggler.paper.machine.MachineType.HYPER_DOG)return Optional.empty();
        JsonObject stored=before.machineState();
        if(stored==null||!stored.has("reel")||!stored.has("hyperDog"))return Optional.empty();
        HyperDogStopSession physical=HyperDogStopSession.fromJson(stored.getAsJsonObject("reel"));
        if(physical.snapshot().round().role()!=HyperDogReelControl.Role.AT_ENTRY_RED7||
           now<physical.presentationEndsAtMs())return Optional.empty();
        HyperDogFullGame game=load(before,machine);
        if(game.at()==null||game.at().stage()!=HyperDogAtGame.Stage.RED7_CONFIRM)
            return Optional.empty();
        var automatic=physical.tick(now);
        if(!physical.complete())return Optional.empty();
        game.confirmRed7();
        long next=before.sequence()+1;
        Session after=updated(before,game,null,now,next);
        List<Envelope> out=new ArrayList<>(automatic);
        out.add(Envelope.current(PacketType.PUBLIC_STATE,publicState(after,game)));
        return Optional.of(new GameTransition(UUID.randomUUID(),before,after,0,0,0,false,
                false,null,false,0,out,List.of(),List.of(),null));
    }

    public List<Envelope> resumePackets(Session session,long now){
        JsonObject stored=session.machineState();
        if(stored==null||!stored.has("hyperDog"))return List.of();
        var game=HyperDogFullGame.restore(stored.getAsJsonObject("hyperDog"));
        var out=new ArrayList<Envelope>();
        out.add(Envelope.current(PacketType.PUBLIC_STATE,publicState(session,game)));
        if(stored.has("reel")){
            var physical=HyperDogStopSession.fromJson(stored.getAsJsonObject("reel"));
            out.addAll(physical.replayOnReconnect());
            if(!physical.complete()){
                for(var e:physical.tick(now))
                    if(e.packetType()==PacketType.REEL_STOP)out.add(e);
            }
        }
        return List.copyOf(out);
    }

    private HyperDogFullGame load(Session before,Machine machine){
        long balance=Math.addExact(before.number("credit"),before.number("held_medals"));
        JsonObject stored=before.machineState();
        if(stored==null||!stored.has("hyperDog"))
            return new HyperDogFullGame(machine.setting(),seed.nextLong(),balance);
        HyperDogFullGame game=HyperDogFullGame.restore(stored.getAsJsonObject("hyperDog"));
        if(game.credit()==balance)return game;
        // A successful server-side loan/insert/cashout updates the existing
        // wallet independently. Align ONLY the public authoritative wallet,
        // never RNG, winnings, paid games, or a pending physical stop.
        if((game.at()!=null&&game.at().pending()!=null) ||
           (game.at()==null&&game.normal().pending()!=null))
            throw new DomainException("RECOVERY_REQUIRED");
        JsonObject adjusted=game.snapshot();
        if(game.at()!=null)adjusted.getAsJsonObject("at").addProperty("credit",balance);
        else adjusted.getAsJsonObject("normal").addProperty("credits",balance);
        return HyperDogFullGame.restore(adjusted);
    }

    private static Session updated(Session before,HyperDogFullGame game,
                                   HyperDogStopSession physical,long now,long sequence){
        var values=new LinkedHashMap<>(before.snapshot());
        values.put("last_client_sequence",sequence);values.put("last_activity",now);
        long wallet=game.credit();
        values.put("credit",Math.min(50,wallet));
        values.put("held_medals",Math.subtractExact(wallet,Math.min(50,wallet)));
        values.put("game_state",game.at()!=null?"NORMAL_SPINNING":
                game.normal().pending()!=null?"NORMAL_SPINNING":
                game.normal().betPosted()?"NORMAL_BETTED":
                game.normal().replayFree()?"REPLAY_READY":"SEATED_READY");
        int currentBet=game.at()!=null
                ?game.at().pending()!=null?game.at().pending().bet():game.at().snapshot().get("postedBet").getAsInt()
                :game.normal().pending()!=null?game.normal().pending().bet():game.normal().betPosted()?3:0;
        values.put("current_bet",currentBet);values.put("pay_display",0);
        JsonObject snapshot=new JsonObject();snapshot.addProperty("schema",1);
        snapshot.add("hyperDog",game.snapshot());
        if(physical!=null){
            snapshot.add("reel",physical.toJson());
            var history=physical.snapshot().round().history();
            int[] stops=history.stops();
            values.put("stopped_mask",history.mask());
            for(var reel:Reel.values()){
                int index=reel.ordinal();
                if(stops[index]>=0)
                    values.put("display_"+reel.name().toLowerCase(Locale.ROOT)+"_stop",stops[index]);
            }
        }else values.put("stopped_mask",7);
        values.put("machine_state_json",snapshot.toString());
        return new Session(values);
    }

    public static JsonObject publicState(Session session,HyperDogFullGame game){
        JsonObject result=session.openPacket();
        JsonObject fields=game.publicState();
        for(var e:fields.entrySet())result.add(e.getKey(),e.getValue().deepCopy());
        result.addProperty("machineType","HYPER_DOG");
        result.addProperty("lifecycle",session.lifecycle().name());
        result.addProperty("heldMedals",session.number("held_medals"));
        result.addProperty("stoppedMask",session.number("stopped_mask"));
        JsonObject stops=new JsonObject();
        for(var reel:Reel.values())stops.addProperty(reel.name().toLowerCase(Locale.ROOT),
                session.number("display_"+reel.name().toLowerCase(Locale.ROOT)+"_stop"));
        result.add("displayStops",stops);
        return result;
    }
    private static long eventId(HyperDogFullGame game){
        long atSerial=game.at()!=null?game.at().serial():0;
        return Math.max(1,game.normal().games()*1000000L+atSerial+1);
    }
    private static Envelope accept(PacketType action,long sequence){
        JsonObject b=new JsonObject();
        b.addProperty("clientSequence",sequence);b.addProperty("action",action.name());
        return Envelope.current(PacketType.ACTION_ACCEPTED,b);
    }

    @Override public List<Envelope> committed(GameTransition action,long sentNanos){return action.packets();}
    @Override public List<GameTransition.Scheduled> scheduled(GameTransition action){return action.scheduled();}
    @Override public Optional<Envelope> resume(Session saved,long sentNanos){
        JsonObject stored=saved.machineState();
        if(stored==null||!stored.has("hyperDog"))return Optional.empty();
        return Optional.of(Envelope.current(PacketType.PUBLIC_STATE,
                publicState(saved,HyperDogFullGame.restore(stored.getAsJsonObject("hyperDog")))));
    }
    @Override public Session capture(Session saved,long now){return saved;}
    @Override public void forget(UUID session){}
}
