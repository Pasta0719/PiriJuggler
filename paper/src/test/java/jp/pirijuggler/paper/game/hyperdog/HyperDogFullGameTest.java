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
