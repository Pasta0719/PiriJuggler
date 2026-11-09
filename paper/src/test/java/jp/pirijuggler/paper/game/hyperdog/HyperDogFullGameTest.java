package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogFullGameTest {
    private static HyperDogFullGame full(long seed){
        return new HyperDogFullGame(3,seed,100000);
    }
    private static void normal(HyperDogFullGame f,HyperDogNormalGame.Role role) {
        if(f.waitingForBet())f.bet();
        f.leverForcedNormal(role);
        f.finish();
    }
    private static void at(HyperDogFullGame f,HyperDogAtGame.Role role) {
        if(f.waitingForBet())f.bet();
        f.leverForced(role);
        f.finish();
    }
    private static void initial(HyperDogFullGame f) {
        if(f.at().stage()==HyperDogAtGame.Stage.RED7_CONFIRM)f.confirmRed7();
        while(f.at().stage()==HyperDogAtGame.Stage.LOWER_INITIAL||
                f.at().stage()==HyperDogAtGame.Stage.UPPER_INITIAL)
            at(f,HyperDogAtGame.Role.MISS);
    }
    private static void drain(HyperDogFullGame f) {
        for(int steps=0;steps<3000&&f.at()!=null;steps++){
            switch(f.at().stage()){
                case RED7_CONFIRM -> f.confirmRed7();
                case RAINBOW7_CONFIRM -> {
                    f.stopRainbowSeven(0);
                    f.stopRainbowSeven(1);
                    f.stopRainbowSeven(2);
                }
                case LOWER_INITIAL,UPPER_INITIAL,UPPER_CZ -> at(f,HyperDogAtGame.Role.MISS);
                case LOWER_AT,UPPER_AT -> at(f,HyperDogAtGame.Role.GRAPE);
                case COMPLETE -> throw new AssertionError("COMPLETE not reconciled");
            }
        }
    }
    @Test void normalBarEntersUpperWithSharedLedgerAndDeterministicNextRole(){
        var f=full(20261109);
        long initial=f.credit();
        normal(f,HyperDogNormalGame.Role.BAR);
        assertEquals("RED7_CONFIRM",f.phase());
        assertEquals(initial-3+15,f.credit());
        assertEquals(1000,f.at().remaining());
        var checkpoint=f.snapshot();
        var clone=HyperDogFullGame.restore(checkpoint);
        assertEquals(checkpoint,clone.snapshot());
        f.confirmRed7();clone.confirmRed7();
        for(int i=0;i<5;i++){
            assertEquals(f.lever(),clone.lever());
            assertEquals(f.at().pending(),clone.at().pending());
            f.finish();clone.finish();
            assertEquals(f.snapshot(),clone.snapshot());
        }
        assertEquals("UPPER_AT",f.phase());
        assertTrue(f.at().upperInitialTotal()>=500);
        assertEquals(initial+12,f.credit(),"initial 5 free games may not credit medals");
        assertEquals(3,f.totalIn());assertEquals(15,f.totalOut());
    }

    @Test void completedAtRebasesNormalModeOnlyOnceAndOpensComeback(){
        boolean success=false;
        for(long seed=0;seed<30&&!success;seed++){
            var f=full(seed);
            normal(f,HyperDogNormalGame.Role.BAR);
            var original=f.normal().savedHitMode();
            drain(f);
            if(f.at()!=null)continue;
            success=true;
            assertEquals(1,f.completedAtCount());
            assertEquals(32,f.comebackPaidLeft());
            assertTrue(f.comebackFromUpper(),"upper CZ failure must remember the last AT");
            assertFalse(f.normal().waitingForAt());
            assertEquals(f.credit(),f.normal().credits());
            assertEquals(f.totalIn(),f.normal().totalBet());
            assertEquals(f.totalOut(),f.normal().totalPayout());
            assertNotNull(original);
            // Snapshot is complete and survives another server process.
            JsonObject saved=f.snapshot();
            var restored=HyperDogFullGame.restore(saved);
            assertEquals(saved,restored.snapshot());
            assertEquals(1,restored.completedAtCount());
            normal(restored,HyperDogNormalGame.Role.REPLAY);
            int afterReplay=restored.comebackPaidLeft();
            normal(restored,HyperDogNormalGame.Role.REPLAY);
            // Replay made second spin free: only a paid game decrements the 32G window.
            if(restored.at()==null)assertEquals(afterReplay,restored.comebackPaidLeft());
        }
        assertTrue(success,"a non-guaranteed upper CZ must eventually terminate AT");
    }

    @Test void upperOriginComebackReturnsUpperViaThreeDistinctRainbowStops(){
        var f=full(0);
        normal(f,HyperDogNormalGame.Role.BAR);
        drain(f);
        assertNull(f.at());
        assertEquals(32,f.comebackPaidLeft());
        assertTrue(f.comebackFromUpper());
        assertEquals(f.snapshot(),HyperDogFullGame.restore(f.snapshot()).snapshot());
        for(int n=0;n<20&&f.at()==null;n++)
            normal(f,HyperDogNormalGame.Role.STRONG_CHERRY);
        assertNotNull(f.at());
        assertEquals(HyperDogAtGame.Stage.RAINBOW7_CONFIRM,f.at().stage());
        assertEquals(0,f.at().remaining(),"upper comeback may not invent BAR +1000");
        assertEquals(0,f.comebackPaidLeft());
        long previousIn=f.totalIn(),previousOut=f.totalOut(),credit=f.credit();
        assertEquals(1,f.stopRainbowSeven(0));
        assertThrows(IllegalStateException.class,()->f.stopRainbowSeven(0));
        var restored=HyperDogFullGame.restore(f.snapshot());
        assertEquals(1,restored.at().rainbowStopMask());
        assertEquals(3,restored.stopRainbowSeven(1));
        assertEquals(7,restored.stopRainbowSeven(2));
        assertEquals(HyperDogAtGame.Stage.UPPER_INITIAL,restored.at().stage());
        assertEquals(previousIn,restored.totalIn());
        assertEquals(previousOut,restored.totalOut());
        assertEquals(credit,restored.credit());
        for(int i=0;i<5;i++)at(restored,HyperDogAtGame.Role.MISS);
        assertEquals(HyperDogAtGame.Stage.UPPER_AT,restored.at().stage());
        assertTrue(restored.at().upperInitialTotal()>=500);
    }

    @Test void lowerOriginComebackStillUsesRedSevenAndLowerInitial(){
        var f=full(53);
        for(int n=0;n<20&&f.at()==null;n++)
            normal(f,HyperDogNormalGame.Role.STRONG_CHERRY);
        assertNotNull(f.at());
        assertEquals(HyperDogAtGame.Stage.RED7_CONFIRM,f.at().stage());
        drain(f);
        assertNull(f.at());
        assertFalse(f.comebackFromUpper());
        assertEquals(32,f.comebackPaidLeft());
        for(int n=0;n<20&&f.at()==null;n++)
            normal(f,HyperDogNormalGame.Role.STRONG_CHERRY);
        assertNotNull(f.at());
        assertEquals(HyperDogAtGame.Stage.RED7_CONFIRM,f.at().stage());
        f.confirmRed7();
        assertEquals(HyperDogAtGame.Stage.LOWER_INITIAL,f.at().stage());
    }

    @Test void upperAtBarStocksPreservedAcrossReelAndProcessRestart(){
        var f=full(31);
        normal(f,HyperDogNormalGame.Role.BAR);
        initial(f);
        at(f,HyperDogAtGame.Role.BAR);
        at(f,HyperDogAtGame.Role.BAR);
        assertEquals(2,f.at().barCharges());
        var recovered=HyperDogFullGame.restore(f.snapshot());
        assertEquals(2,recovered.at().barCharges());
        for(int i=0;i<1000&&recovered.at()!=null&&
                recovered.at().stage()==HyperDogAtGame.Stage.UPPER_AT;i++)
            at(recovered,HyperDogAtGame.Role.GRAPE);
        assertEquals(HyperDogAtGame.Stage.UPPER_CZ,recovered.at().stage());
        boolean hit=false;
        for(int i=0;i<5&&recovered.at().stage()==HyperDogAtGame.Stage.UPPER_CZ;i++){
            var before=recovered.at().czGame();
            at(recovered,HyperDogAtGame.Role.MISS);
            if(recovered.at()!=null&&recovered.at().stage()==HyperDogAtGame.Stage.UPPER_INITIAL)
                hit=true;
            assertTrue(before<5);
        }
        assertTrue(hit);
        assertEquals(HyperDogAtGame.Stage.UPPER_INITIAL,recovered.at().stage());
        var first=recovered.at().leverForced(HyperDogAtGame.Role.MISS);
        assertNotEquals(HyperDogAtGame.Role.MISS,first.role(),"bar charges force rare");
    }
    @Test void publicStateDoesNotExposeInternalRngRoleCzResultOrStock(){
        var f=full(92);
        normal(f,HyperDogNormalGame.Role.BAR);
        f.confirmRed7();
        f.at().leverForced(HyperDogAtGame.Role.STRONG_CHANCE);
        var publicView=f.publicState().toString();
        for(String key:new String[]{"rng","savedHitMode","pending","czForcedGame","tickets",
                                     "barGuaranteed","role","czSuccess","singleHit"})
            assertFalse(publicView.contains("\""+key+"\""),key);
        assertNotNull(f.snapshot().getAsJsonObject("at").get("pending"));
    }
}
