package jp.pirijuggler.fabric.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CabinetPlacementTest {
    @Test void allSixFacingsHaveLockedBasisAndCenter() {
        for (String facing : new String[]{"NORTH","SOUTH","EAST","WEST","UP","DOWN"}) {
            CabinetPlacement.Basis b = CabinetPlacement.basis(0, 64, 0, facing);
            assertEquals(1.0, length(b.front()), 1e-12);
            assertEquals(1.0, length(b.right()), 1e-12);
            assertEquals(1.0, length(b.up()), 1e-12);
            assertEquals(0.0, dot(b.front(), b.right()), 1e-12);
            assertEquals(0.0, dot(b.front(), b.up()), 1e-12);
            assertEquals(0.0, dot(b.right(), b.up()), 1e-12);
        }
        var up = CabinetPlacement.basis(0,64,0,"UP");
        var down = CabinetPlacement.basis(0,64,0,"DOWN");
        assertEquals(1.0, up.right().x(), 1e-12);
        assertEquals(1.0, down.right().x(), 1e-12);
        assertEquals(-1.0, up.up().z(), 1e-12);
        assertEquals(-1.0, down.up().z(), 1e-12);
    }

    @Test void horizontalCenterHasLockedVerticalOffsetButVerticalFacesDoNot() {
        var north = CabinetPlacement.basis(4, 70, 8, "NORTH");
        assertEquals(71.55, north.center().y(), 1e-12);
        assertEquals(8.5 - CabinetPlacement.FACE_OFFSET - CabinetPlacement.DEPTH_BIAS, north.center().z(), 1e-12);

        var up = CabinetPlacement.basis(4, 70, 8, "UP");
        assertEquals(70.5 + CabinetPlacement.FACE_OFFSET + CabinetPlacement.DEPTH_BIAS, up.center().y(), 1e-12);
        assertEquals(8.5, up.center().z(), 1e-12);
    }

    private static double length(CabinetPlacement.Vec v) { return Math.sqrt(dot(v,v)); }
    private static double dot(CabinetPlacement.Vec a, CabinetPlacement.Vec b) {
        return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();
    }
}
