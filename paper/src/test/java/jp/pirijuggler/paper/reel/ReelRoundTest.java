package jp.pirijuggler.paper.reel;
import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.threading.MainThread;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReelRoundTest {
    private static final StopSolver SOLVER=new StopSolver(new StopCatalogue());
    private static final UUID OWNER=UUID.randomUUID(),SESSION=UUID.randomUUID(),SPIN=UUID.randomUUID();
    private final Thread thread=Thread.currentThread();private final MainThread main=new MainThread(){public void execute(Runnable r){r.run();}public boolean isMainThread(){return Thread.currentThread()==thread;}};
    private ReelRound round(ReelMotion.Profile profile,DisplayRole role){return new ReelRound(SOLVER,new ReelRound.Identity(OWNER,SESSION,1,SPIN),role,false,profile,"NORMAL",new double[]{8,3,12},new StopTriplet(0,0,0),0,0,main);}
    private Envelope request(PacketType type,long seq){var b=new JsonObject();b.addProperty("sessionId",SESSION.toString());b.addProperty("machineId",1);b.addProperty("clientSequence",seq);return Envelope.current(type,b);}
    private static String error(ReelRound.Result r){assertFalse(r.accepted());return r.packets().getFirst().payload().get("errorCode").getAsString();}
    @Test void earlyStopConsumesSequenceAndUsesServerTimeWithPing(){
        var r=round(ReelMotion.Profile.NORMAL,DisplayRole.BELL);assertEquals(400,r.begin(0).payload().get("stopEnableAfterMs").getAsInt());
        assertEquals("STOP_TOO_EARLY",error(r.receive(OWNER,request(PacketType.STOP_LEFT,1),449_999_999,100)));assertEquals(1,r.lastSequence());
        assertEquals("SEQUENCE_OLD",error(r.receive(OWNER,request(PacketType.STOP_LEFT,1),600_000_000,100)));
        var valid=r.receive(OWNER,request(PacketType.STOP_LEFT,2),600_000_000,100);assertTrue(valid.accepted());assertEquals(4,valid.choice().pressedIndex());assertEquals(1,r.stoppedMask());
        assertEquals(Set.of("spinId","reel","pressedIndex","stopIndex","slip","durationMs"),valid.packets().get(1).payload().keySet());assertEquals("ALREADY_STOPPED",error(r.receive(OWNER,request(PacketType.STOP_LEFT,3),900_000_000,100)));
    }
    @Test void profileThresholdsAndClientDelaysAreFixed(){for(var profile:ReelMotion.Profile.values()){
        var r=round(profile,DisplayRole.GRAPE);assertEquals(profile.clientDelayMs(),r.begin(0).payload().get("stopEnableAfterMs").getAsInt());long boundary=profile.serverThresholdMs()*1_000_000L;
        assertEquals("STOP_TOO_EARLY",error(r.receive(OWNER,request(PacketType.STOP_LEFT,1),boundary-1,0)));assertTrue(r.receive(OWNER,request(PacketType.STOP_LEFT,2),boundary,0).accepted());}}
    @Test void sessionOwnerAndForbiddenStopFieldsAreRejectedWithoutMovingReels(){
        var r=round(ReelMotion.Profile.NORMAL,DisplayRole.MISS);r.begin(0);assertEquals("SESSION_MISMATCH",error(r.receive(UUID.randomUUID(),request(PacketType.STOP_LEFT,1),1_000_000_000,0)));
        for(String key:List.of("pressedIndex","spinId","phase","targetStopIndex")){var b=request(PacketType.STOP_LEFT,1).payload();b.addProperty(key,9);assertEquals("SESSION_MISMATCH",error(r.receive(OWNER,Envelope.current(PacketType.STOP_LEFT,b),1_000_000_000,0)));}
        assertEquals(0,r.lastSequence());assertEquals(0,r.stoppedMask());
    }
    @Test void spaceSelectsNextUnstoppedAndNeverReturnsInternalOutcome(){
        var r=round(ReelMotion.Profile.NORMAL,DisplayRole.PREMIUM_B);r.begin(0);r.receive(OWNER,request(PacketType.STOP_CENTER,1),1_000_000_000,0);
        var left=r.receive(OWNER,request(PacketType.SPACE_ACTION,2),1_000_000_000,0);assertEquals("LEFT",left.packets().get(1).payload().get("reel").getAsString());
        var right=r.receive(OWNER,request(PacketType.SPACE_ACTION,3),1_000_000_000,0);assertEquals("RIGHT",right.packets().get(1).payload().get("reel").getAsString());assertEquals(7,r.stoppedMask());assertTrue(CATALOGUE_VALID(r));
        assertEquals("INVALID_STATE",error(r.receive(OWNER,request(PacketType.SPACE_ACTION,4),1_000_000_000,0)));
    }
    private boolean CATALOGUE_VALID(ReelRound r){return SOLVER.catalogue().evaluation(r.display()).valid(DisplayRole.PREMIUM_B);}
}
