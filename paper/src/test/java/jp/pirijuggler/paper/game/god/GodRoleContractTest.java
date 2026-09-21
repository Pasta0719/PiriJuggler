package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodRoleContractTest {
    @Test
    void categoricalRoleTableSumsToOneAndPreservesLockedPremiumMarginals(){
        double sum=0.0;
        for(GodRole role:GodRole.values())sum+=GodKisekiRoleTable.categoricalProbability(role);
        assertEquals(1.0,sum,1.0e-12);

        assertEquals(1.0/8192.0,GodKisekiRoleTable.categoricalProbability(GodRole.GOD),1.0e-15);
        assertEquals(1.0/6900.0,GodKisekiRoleTable.categoricalProbability(GodRole.RED7),1.0e-15);
        assertEquals(1.0/65536.0,GodKisekiRoleTable.categoricalProbability(GodRole.SP),1.0e-15);
        assertTrue(GodKisekiRoleTable.categoricalProbability(GodRole.MISS)>0.0);
    }

    @Test
    void normalOrderedYellowNeverBecomesTheNavigatedFifteenMedalResult(){
        var one=GodRoleOutcome.resolve(GodRole.ORDERED_YELLOW7,GodPhase.NORMAL,0.0);
        assertEquals("MISS",one.displayRole());
        assertEquals(1,one.payout());
        assertFalse(one.replay());

        var zero=GodRoleOutcome.resolve(GodRole.ORDERED_YELLOW7,GodPhase.NORMAL,0.999999);
        assertEquals("MISS",zero.displayRole());
        assertEquals(0,zero.payout());
        assertFalse(zero.replay());
    }

    @Test
    void atLikeStatesNavigateOrderedYellowForFifteenMedals(){
        for(GodPhase phase:new GodPhase[]{GodPhase.GG,GodPhase.G_ZONE,GodPhase.SGG,GodPhase.SGG_COMEBACK,GodPhase.Z_ZONE,GodPhase.Z_GAME}){
            var outcome=GodRoleOutcome.resolve(GodRole.ORDERED_YELLOW7,phase,0.999999);
            assertEquals("COMMON_YELLOW7",outcome.displayRole(),phase.name());
            assertEquals(15,outcome.payout(),phase.name());
        }
    }

    @Test
    void replayRolesAreTrueReplayOutcomes(){
        for(GodRole role:new GodRole[]{GodRole.UPPER_BLUE7,GodRole.MIDDLE_BLUE7,GodRole.RED7_FAKE}){
            var outcome=GodRoleOutcome.resolve(role,GodPhase.NORMAL,0.5);
            assertTrue(outcome.replay(),role.name());
            assertEquals(0,outcome.payout(),role.name());
        }
    }
}
