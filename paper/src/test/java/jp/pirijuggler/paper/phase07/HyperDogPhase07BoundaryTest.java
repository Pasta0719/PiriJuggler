package jp.pirijuggler.paper.phase07;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.reel.HyperDogReelControl;
import jp.pirijuggler.paper.game.hyperdog.HyperDogAtGame;
import jp.pirijuggler.paper.game.hyperdog.HyperDogPresentation;
import jp.pirijuggler.paper.reel.HyperDogStopSession;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Fast checkpoint acceptance: no full Paper/Fabric startup or repeated legacy phase. */
class HyperDogPhase07BoundaryTest {
    private static HyperDogAtGame upper() {
        HyperDogAtGame game=new HyperDogAtGame(3,2061109L,50000,0,0,true);
        game.confirmRed7();
        for(int i=0;i<5;i++) {
            HyperDogAtGame.Round prepared=game.lever();
            assertEquals(HyperDogAtGame.Stage.UPPER_INITIAL,prepared.stage());
            assertEquals(0,prepared.bet());
            game.finish();
        }
        assertEquals(HyperDogAtGame.Stage.UPPER_AT,game.stage());
        return game;
    }
    @Test void czBeforeCenterStopDoesNotConsumeAttemptOrLoseRngOnReconnection() {
        HyperDogAtGame game=upper();
        JsonObject checkpoint=game.snapshot();
        checkpoint.addProperty("stage","UPPER_CZ");
        checkpoint.addProperty("czGame",0);
        checkpoint.addProperty("czFromUpper",true);
        game=HyperDogAtGame.restore(checkpoint);
        var pending=game.lever();
        assertEquals(HyperDogAtGame.Stage.UPPER_CZ,pending.stage());
        assertEquals(0,pending.bet());
        UUID session=UUID.randomUUID();
        long start=10000L;
        HyperDogStopSession real=new HyperDogStopSession(session,4,UUID.randomUUID(),
                pending.czSuccess()?HyperDogReelControl.Role.CZ_WIN:HyperDogReelControl.Role.CZ_LOSE,
                start,new double[]{0,7,14});
        real.tick(start+800);
        assertEquals(2,real.snapshot().round().history().count());
        assertFalse(real.complete());
        JsonObject before=game.snapshot(),stops=real.toJson();
        long credit=game.credit(),rng=Long.parseUnsignedLong(game.rngState()),spent=game.totalIn(),paid=game.totalOut();
        HyperDogAtGame resumed=HyperDogAtGame.restore(before);
        HyperDogStopSession physical=HyperDogStopSession.fromJson(stops);
        assertEquals(stops,physical.toJson());
        assertEquals(before,resumed.snapshot());
        assertEquals(2,physical.snapshot().round().history().count());
        assertEquals(credit,resumed.credit());
        assertEquals(rng,Long.parseUnsignedLong(resumed.rngState()));
        assertEquals(spent,resumed.totalIn());
        assertEquals(paid,resumed.totalOut());
        var malformed=new JsonObject();
        malformed.addProperty("sessionId",UUID.randomUUID().toString());
        malformed.addProperty("machineId",4);
        malformed.addProperty("clientSequence",1);
        assertFalse(physical.receive(Envelope.current(PacketType.STOP_CENTER,malformed),start+1600)
                .stream().anyMatch(p->p.packetType()==PacketType.ACTION_ACCEPTED));
        JsonObject request=new JsonObject();
        request.addProperty("sessionId",session.toString());
        request.addProperty("machineId",4);
        request.addProperty("clientSequence",1);
        request.addProperty("pressedIndex",0);
        assertTrue(physical.receive(Envelope.current(PacketType.STOP_CENTER,request),start+1600)
                .stream().anyMatch(p->p.packetType()==PacketType.ACTION_ACCEPTED));
        assertTrue(physical.complete());
        var settled=resumed.finish();
        assertEquals(pending,settled);
        assertEquals(credit,resumed.credit());
        assertEquals(spent,resumed.totalIn());
        assertEquals(paid,resumed.totalOut());
        assertThrows(IllegalStateException.class,resumed::finish);
    }
    @Test void upperInitialFiveGamesSurviveLastFreeStopAndDoNotCreateSixthInitialAward() {
        HyperDogAtGame game=new HyperDogAtGame(3,10909L,50000,0,0,true);
        game.confirmRed7();
        for(int i=0;i<4;i++){game.lever();game.finish();}
        assertEquals(HyperDogAtGame.Stage.UPPER_INITIAL,game.stage());
        long before=game.upperInitialTotal();
        var fifth=game.lever();
        assertEquals(0,fifth.bet());
        JsonObject saved=game.snapshot();
        HyperDogAtGame resumed=HyperDogAtGame.restore(saved);
        assertEquals(saved,resumed.snapshot());
        assertEquals(fifth,resumed.pending());
        assertEquals(before,resumed.upperInitialTotal(),"no award before third stop");
        assertEquals(fifth,resumed.finish());
        assertEquals(before+fifth.award(),resumed.upperInitialTotal());
        assertEquals(HyperDogAtGame.Stage.UPPER_AT,resumed.stage());
        assertThrows(IllegalStateException.class,resumed::finish);
        long total=resumed.upperInitialTotal();
        var view=HyperDogPresentation.choose(201,total,12345L);
        for(int stop=0;stop<3;stop++){
            JsonObject preview=view.publicStage(stop,false,0);
            assertFalse(preview.has("newAward"));
            assertFalse(preview.has("flashAward"));
        }
        JsonObject committed=view.publicStage(3,true,2000);
        assertEquals(total,committed.get("newAward").getAsLong());
        assertEquals(committed,view.publicStage(3,true,2000));
        resumed.bet();
        var sixth=resumed.lever();
        assertEquals(HyperDogAtGame.Stage.UPPER_AT,sixth.stage());
        assertEquals(3,sixth.bet());
        assertEquals(total,resumed.upperInitialTotal(),"paid sixth game must not recompute initial TOTAL");
    }
}
