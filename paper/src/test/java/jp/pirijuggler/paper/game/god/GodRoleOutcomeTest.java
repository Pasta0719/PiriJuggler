package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodRoleOutcomeTest {
    @Test
    void publishedReplayRolesStayReplayOnly(){
        for(GodRole role:new GodRole[]{GodRole.UPPER_BLUE7,GodRole.MIDDLE_BLUE7,GodRole.RED7_FAKE}){
            var out=GodRoleOutcome.forRole(role,GodPhase.NORMAL);
            assertTrue(out.replay(),role.name());
            assertEquals(0,out.payout(),role.name());
        }
    }

    @Test
    void publishedMedalRolesUseTheirKnownPayouts(){
        assertEquals(3,GodRoleOutcome.forRole(GodRole.LOWER_YELLOW7,GodPhase.NORMAL).payout());
        assertEquals(1,GodRoleOutcome.forRole(GodRole.GAIA_BELL,GodPhase.NORMAL).payout());

        for(GodRole role:new GodRole[]{
                GodRole.RISING_YELLOW7,GodRole.MIDDLE_YELLOW7,GodRole.COMMON_YELLOW7,
                GodRole.SP,GodRole.RED7,GodRole.GOD
        }) assertEquals(15,GodRoleOutcome.forRole(role,GodPhase.NORMAL).payout(),role.name());
    }

    @Test
    void orderedYellowUsesNavigatedAtPayout(){
        assertEquals(1,GodRoleOutcome.forRole(GodRole.ORDERED_YELLOW7,GodPhase.NORMAL).payout());
        assertEquals(15,GodRoleOutcome.forRole(GodRole.ORDERED_YELLOW7,GodPhase.GG).payout());
        assertEquals(15,GodRoleOutcome.forRole(GodRole.ORDERED_YELLOW7,GodPhase.SGG).payout());
    }
}
