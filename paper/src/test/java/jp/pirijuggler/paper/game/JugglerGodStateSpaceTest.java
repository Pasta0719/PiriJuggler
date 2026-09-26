package jp.pirijuggler.paper.game;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive finite-state invariants for the hidden JUGGLER_GOD successor runtime.
 * This deliberately covers both standard and EXTREME because they share this runtime/state machine.
 */
class JugglerGodStateSpaceTest {

    @Test void everyBigRegAcquisitionConsumesPendingExactlyOnceAcrossReachableContexts() {
        for (var mode : JugglerGodRuntime.Mode.values()) {
            for (boolean releasing : new boolean[]{false,true}) {
                for (int big=0; big<=2; big++) {
                    for (int reg=0; reg<=2; reg++) {
                        for (String hit : new String[]{"BIG","REG"}) {
                            for (String suspended : new String[]{"BIG","REG"}) {
                                for (boolean ended : new boolean[]{false,true}) {
                                    String origin = switch (mode) {
                                        case NORMAL -> suspended;
                                        case HEAVEN -> "HEAVEN";
                                        case GOD_CHAIN -> "GOD_CHAIN";
                                    };
                                    var before=new JugglerGodRuntime(
                                            mode,
                                            mode==JugglerGodRuntime.Mode.HEAVEN?7:0,
                                            mode==JugglerGodRuntime.Mode.HEAVEN?7:0,
                                            mode==JugglerGodRuntime.Mode.GOD_CHAIN?2:0,
                                            false,false,origin,mode==JugglerGodRuntime.Mode.GOD_CHAIN?3:0,
                                            false,"BONUS_STOCK_CONFIRM_READY",
                                            big,reg,hit,suspended,14,ended,releasing);
                                    var after=JugglerGodGameEngine.confirmAcquiredStock(before);

                                    assertEquals("NONE",after.pendingBonusHit());
                                    assertEquals("NONE",after.suspendedBonusType());
                                    assertEquals(0,after.suspendedBonusPayoutCount());
                                    assertFalse(after.suspendedBonusEnded());
                                    assertEquals(releasing,after.releasingStock());
                                    assertEquals(mode,after.mode());
                                    assertEquals(origin,after.bonusOrigin());
                                    assertEquals(big+("BIG".equals(hit)?1:0),after.additionalBigStock());
                                    assertEquals(reg+("REG".equals(hit)?1:0),after.additionalRegStock());

                                    // Reconfirmation is forbidden: this is what prevents a latched
                                    // REG/BIG from reproducing itself forever.
                                    assertThrows(IllegalArgumentException.class,
                                            ()->JugglerGodGameEngine.confirmAcquiredStock(after));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test void acquisitionFinishPredicateAcceptsPendingBigRegDuringReleaseAndNothingElse() {
        for (boolean releasing : new boolean[]{false,true}) {
            for (String hit : new String[]{"NONE","BIG","REG","GOD"}) {
                var runtime=new JugglerGodRuntime(
                        JugglerGodRuntime.Mode.GOD_CHAIN,0,0,0,false,false,"GOD_CHAIN",5,
                        false,"TEST",0,0,hit,"REG",14,false,releasing);
                for (var state : jp.pirijuggler.paper.session.Session.GameState.values()) {
                    boolean expected=(state==jp.pirijuggler.paper.session.Session.GameState.BONUS_ENTRY_SPINNING_BIG
                            ||state==jp.pirijuggler.paper.session.Session.GameState.BONUS_ENTRY_SPINNING_REG)
                            &&("BIG".equals(hit)||"REG".equals(hit));
                    assertEquals(expected,JugglerGodGameEngine.isAcquisitionEntryFinish(true,state,runtime),
                            ()->"state="+state+" hit="+hit+" releasing="+releasing);
                    assertFalse(JugglerGodGameEngine.isAcquisitionEntryFinish(false,state,runtime));
                }
            }
        }
    }

    @Test void runtimeJsonRoundTripPreservesAllReachableHiddenStateAxes() {
        for (var mode : JugglerGodRuntime.Mode.values()) {
            for (boolean force : new boolean[]{false,true}) {
                for (boolean count : new boolean[]{false,true}) {
                    for (boolean freeze : new boolean[]{false,true}) {
                        for (boolean releasing : new boolean[]{false,true}) {
                            String origin=switch(mode){
                                case NORMAL -> "NONE";
                                case HEAVEN -> "HEAVEN";
                                case GOD_CHAIN -> "GOD_CHAIN";
                            };
                            var state=new JugglerGodRuntime(
                                    mode,
                                    mode==JugglerGodRuntime.Mode.HEAVEN?32:0,
                                    mode==JugglerGodRuntime.Mode.HEAVEN?17:0,
                                    mode==JugglerGodRuntime.Mode.GOD_CHAIN?4:0,
                                    force,count,origin,9,freeze,"STATE_SPACE",
                                    2,1,"NONE","NONE",0,false,releasing,"NONE",12345L);
                            assertEquals(state,JugglerGodRuntime.fromJson(state.toJsonString()));
                        }
                    }
                }
            }
        }
    }

    @Test void postBonusTransitionNeverLeavesGodChainInAnInvalidIntermediateState() {
        int[] continuation={0,75,78,80,82,85,90};
        for (int setting=1; setting<=6; setting++) {
            for (int guaranteed=0; guaranteed<=5; guaranteed++) {
                for (int roll=0; roll<100; roll++) {
                    final int fixedRoll=roll;
                    var rng=new Random(1L){
                        @Override public int nextInt(int bound){
                            if(bound==100)return fixedRoll;
                            return Math.floorMod(fixedRoll,bound);
                        }
                    };
                    var before=new JugglerGodRuntime(
                            JugglerGodRuntime.Mode.GOD_CHAIN,0,0,guaranteed,false,false,
                            "GOD_CHAIN",5,false,"BONUS_END");
                    var after=JugglerGodTransitions.afterBonus(
                            before,setting,rng,125000,62500,500000,continuation);

                    assertTrue(after.guaranteedRemaining()>=0);
                    if(guaranteed>0){
                        assertEquals(JugglerGodRuntime.Mode.GOD_CHAIN,after.mode());
                        assertEquals(guaranteed-1,after.guaranteedRemaining());
                        assertTrue(after.forceChainBig());
                        assertFalse(after.countNextChainGame());
                    }else if(fixedRoll<continuation[setting]){
                        assertEquals(JugglerGodRuntime.Mode.GOD_CHAIN,after.mode());
                        assertEquals(0,after.guaranteedRemaining());
                        assertTrue(after.forceChainBig());
                        assertTrue(after.countNextChainGame());
                    }else{
                        assertEquals(JugglerGodRuntime.Mode.HEAVEN,after.mode());
                        assertTrue(after.heavenTarget()>=1&&after.heavenTarget()<=32);
                        assertEquals(0,after.heavenProgress());
                        assertFalse(after.forceChainBig());
                        assertFalse(after.countNextChainGame());
                    }
                }
            }
        }
    }

    @Test void stockCountersNeverGoNegativeThroughRuntimeReleaseOperations() {
        for (int big=0; big<=3; big++) {
            for (int reg=0; reg<=3; reg++) {
                var state=new JugglerGodRuntime(
                        JugglerGodRuntime.Mode.GOD_CHAIN,0,0,0,false,false,"GOD_CHAIN",5,
                        false,"TEST",big,reg,"NONE","NONE",0,false,false);
                if(big>0){
                    var release=state.release(big-1,reg,"RELEASE_BIG");
                    assertEquals(big-1,release.additionalBigStock());
                    assertEquals(reg,release.additionalRegStock());
                    assertTrue(release.releasingStock());
                }
                if(reg>0){
                    var release=state.release(big,reg-1,"RELEASE_REG");
                    assertEquals(big,release.additionalBigStock());
                    assertEquals(reg-1,release.additionalRegStock());
                    assertTrue(release.releasingStock());
                }
            }
        }
    }
}
