package jp.pirijuggler.paper.game.god;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GodKisekiRoleTableTest {
    @Test void publishedSettingCommonOddsAreLocked() {
        assertEquals(5.2,GodKisekiRoleTable.referenceDenominator(GodRole.MISS));
        assertEquals(7.9,GodKisekiRoleTable.referenceDenominator(GodRole.UPPER_BLUE7));
        assertEquals(1.7,GodKisekiRoleTable.referenceDenominator(GodRole.ORDERED_YELLOW7));
        assertEquals(6900.0,GodKisekiRoleTable.referenceDenominator(GodRole.RED7));
        assertEquals(16384.0,GodKisekiRoleTable.referenceDenominator(GodRole.GOD));
        assertEquals(65536.0,GodKisekiRoleTable.referenceDenominator(GodRole.SP));
    }

    @Test void onlyGodIsOverriddenByPiriProductionRule() {
        for (GodRole role:GodRole.values()) {
            if(role==GodRole.GOD) {
                assertEquals(8192.0,GodKisekiRoleTable.piriDenominator(role));
            } else if(role!=GodRole.MISS || true) {
                if(role!=GodRole.GOD)
                    assertEquals(GodKisekiRoleTable.referenceDenominator(role),
                            GodKisekiRoleTable.piriDenominator(role));
            }
        }
    }

    @Test void sixFrontModesAreRepresented() {
        assertEquals(6,GodFrontMode.values().length);
        assertEquals(GodFrontMode.HEAVEN,
                GodFrontMode.valueOf("HEAVEN"));
    }
}
