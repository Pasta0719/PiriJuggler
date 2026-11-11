package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogOneShotLifecycleTest {
    private static HyperDogFullGame initialBar(){
        var f=new HyperDogFullGame(3,12L,100000L);
        f.bet();
        f.leverForcedNormal(HyperDogNormalGame.Role.BAR);
        f.finish();
        assertNotNull(f.at());
        assertEquals(1,f.shotSerial());
        return f;
    }
    private static JsonObject simulatedComebackSnapshot(HyperDogFullGame f,long net){
        JsonObject s=f.snapshot();
        s.remove("at");
        s.getAsJsonObject("normal").addProperty("waitingForAt",false);
        s.addProperty("comebackPaidLeft",1);
        s.addProperty("comebackFromUpper",false);
        s.addProperty("shotCompletedNet",net);
        s.addProperty("shotFinal",false);
        return s;
    }
    @Test void comebackBarDirectHyperKeepsTheSameShotIdAndTotal(){
        HyperDogFullGame before=initialBar();
        var saved=simulatedComebackSnapshot(before,4280);
        var f=HyperDogFullGame.restore(saved);
        long serial=f.shotSerial();
        assertEquals(4280,f.shotGain());
        f.bet();
        f.leverForcedNormal(HyperDogNormalGame.Role.BAR);
        f.finish();
        assertNotNull(f.at());
        assertEquals(serial,f.shotSerial(),"BAR during comeback must not create a new shot");
        assertEquals(4280,f.shotGain(),"normal BAR win is not new AT net");
        assertFalse(f.shotFinal());
        var restored=HyperDogFullGame.restore(f.snapshot());
        assertEquals(serial,restored.shotSerial());
        assertEquals(f.shotGain(),restored.shotGain());
    }
    @Test void lastFailedComebackClosesTheShotAndKeepsFinalTotal(){
        var original=initialBar();
        boolean finished=false;
        for(long seed=1;seed<=256&&!finished;seed++){
            var saved=simulatedComebackSnapshot(original,3280);
            saved.getAsJsonObject("normal").addProperty("rngState",Long.toUnsignedString(seed));
            var f=HyperDogFullGame.restore(saved);
            f.bet();
            f.leverForcedNormal(HyperDogNormalGame.Role.MISS);
            f.finish();
            if(f.at()!=null)continue; // A deterministic rare comeback roll can succeed.
            assertEquals(0,f.comebackPaidLeft());
            assertTrue(f.shotFinal());
            assertEquals(3280,f.shotGain());
            assertTrue(HyperDogFullGame.restore(f.snapshot()).shotFinal());
            finished=true;
        }
        assertTrue(finished,"at least one fixed seed must fail the final comeback draw");
    }
    @Test void currentRunningShotIsPartOfThePersistedPublicProjection(){
        var f=initialBar();
        assertTrue(f.publicState().get("shotExists").getAsBoolean());
        assertFalse(f.publicState().get("shotFinal").getAsBoolean());
        assertEquals(f.shotGain(),f.publicState().get("shotNet").getAsLong());
        var clone=HyperDogFullGame.restore(f.snapshot());
        assertEquals(f.publicState().get("shotNet"),clone.publicState().get("shotNet"));
    }
}
