package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodRoleOutcomeTest {
    @Test
    void publishedRoleSettlementsStayConsistent(){
        assertReplay(GodRole.UPPER_BLUE7);
        assertReplay(GodRole.MIDDLE_BLUE7);
        assertReplay(GodRole.RED7_FAKE);

        assertEquals(3,normal(GodRole.LOWER_YELLOW7).payout());
        assertEquals(15,normal(GodRole.RISING_YELLOW7).payout());
        assertEquals(15,normal(GodRole.MIDDLE_YELLOW7).payout());
        assertEquals(15,normal(GodRole.COMMON_YELLOW7).payout());
        assertEquals(1,normal(GodRole.GAIA_BELL).payout());
        assertEquals(15,normal(GodRole.SP).payout());
        assertEquals(15,normal(GodRole.RED7).payout());
        assertEquals(15,normal(GodRole.GOD).payout());
        assertEquals(0,normal(GodRole.MISS).payout());
    }

    @Test
    void orderedYellowOnlyShowsWinningYellowWhenItActuallyPaysFifteen(){
        var miss=GodRoleOutcome.resolve(GodRole.ORDERED_YELLOW7,GodPhase.NORMAL,0.5);
        assertEquals(1,miss.payout());
        assertEquals("ORDERED_YELLOW7_ONE",miss.displayRole());

        var oneMedal=GodRoleOutcome.resolve(GodRole.ORDERED_YELLOW7,GodPhase.NORMAL,0.0);
        assertEquals(1,oneMedal.payout());
        assertEquals("ORDERED_YELLOW7_ONE",oneMedal.displayRole());

        var zeroMedal=GodRoleOutcome.resolve(GodRole.ORDERED_YELLOW7,GodPhase.NORMAL,0.999999);
        assertEquals(0,zeroMedal.payout());
        assertEquals("MISS",zeroMedal.displayRole());

        var navigated=GodRoleOutcome.resolve(GodRole.ORDERED_YELLOW7,GodPhase.GG,0.999);
        assertEquals(15,navigated.payout());
        assertEquals("COMMON_YELLOW7",navigated.displayRole());
    }

    @Test
    void normalOrderedYellowCalibrationIsExplicitlyMissSideOnly(){
        assertTrue(GodRoleOutcome.NORMAL_ORDERED_ONE_MEDAL_RATE>0.0);
        assertTrue(GodRoleOutcome.NORMAL_ORDERED_ONE_MEDAL_RATE<1.0);

        var below=GodRoleOutcome.resolve(
                GodRole.ORDERED_YELLOW7,GodPhase.NORMAL,
                Math.nextDown(GodRoleOutcome.NORMAL_ORDERED_ONE_MEDAL_RATE));
        var above=GodRoleOutcome.resolve(
                GodRole.ORDERED_YELLOW7,GodPhase.NORMAL,
                Math.min(0.999999999999,GodRoleOutcome.NORMAL_ORDERED_ONE_MEDAL_RATE+1.0e-9));

        assertEquals("ORDERED_YELLOW7_ONE",below.displayRole());
        assertEquals("MISS",above.displayRole());
        assertEquals(1,below.payout());
        assertEquals(0,above.payout());
    }

    private static GodRoleOutcome.Outcome normal(GodRole role){
        return GodRoleOutcome.resolve(role,GodPhase.NORMAL,0.5);
    }

    private static void assertReplay(GodRole role){
        var o=normal(role);
        assertTrue(o.replay());
        assertEquals(0,o.payout());
    }
}
