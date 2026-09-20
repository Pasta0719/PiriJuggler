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
        assertEquals("MISS",miss.displayRole());

        var coincidentalHit=GodRoleOutcome.resolve(GodRole.ORDERED_YELLOW7,GodPhase.NORMAL,0.0);
        assertEquals(15,coincidentalHit.payout());
        assertEquals("ORDERED_YELLOW7",coincidentalHit.displayRole());

        var navigated=GodRoleOutcome.resolve(GodRole.ORDERED_YELLOW7,GodPhase.GG,0.999);
        assertEquals(15,navigated.payout());
        assertEquals("ORDERED_YELLOW7",navigated.displayRole());
    }

    @Test
    void normalSettlementCalibrationMatchesPublishedBase(){
        GodRole[] ordinary={
                GodRole.MISS,GodRole.UPPER_BLUE7,GodRole.MIDDLE_BLUE7,GodRole.ORDERED_YELLOW7,
                GodRole.LOWER_YELLOW7,GodRole.RISING_YELLOW7,GodRole.MIDDLE_YELLOW7,
                GodRole.COMMON_YELLOW7,GodRole.GAIA_BELL,GodRole.RED7_FAKE
        };
        double sum=0;
        for(GodRole r:ordinary)sum+=GodKisekiRoleTable.referenceProbability(r);

        double ordinaryGross=0;
        for(GodRole r:ordinary){
            double value;
            if(r==GodRole.ORDERED_YELLOW7){
                double q=GodRoleOutcome.NORMAL_ORDERED_15_SUCCESS_RATE;
                value=(1.0-q)+15.0*q;
            }else{
                var o=normal(r);
                value=o.replay()?3.0:o.payout();
            }
            ordinaryGross+=GodKisekiRoleTable.referenceProbability(r)/sum*value;
        }

        double pGod=1.0/GodProductionSpec.GOD_DENOMINATOR;
        double pRed=(1-pGod)/GodProductionSpec.RED7_DENOMINATOR;
        double pSp=(1-pGod)*(1.0-1.0/GodProductionSpec.RED7_DENOMINATOR)/GodProductionSpec.SP_DENOMINATOR;
        double pOrdinary=(1-pGod)*(1.0-1.0/GodProductionSpec.RED7_DENOMINATOR)*(1.0-1.0/GodProductionSpec.SP_DENOMINATOR);
        double gross=pOrdinary*ordinaryGross+15.0*(pGod+pRed+pSp);
        double gamesPer50=50.0/(3.0-gross);

        assertEquals(30.8,gamesPer50,0.2);
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
