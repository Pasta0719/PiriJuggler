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
        assertEquals(70.5 + CabinetPlacement.HORIZONTAL_UP_OFFSET, north.center().y(), 1e-12);
        assertEquals(8.5 - CabinetPlacement.FACE_OFFSET - CabinetPlacement.DEPTH_BIAS, north.center().z(), 1e-12);

        var up = CabinetPlacement.basis(4, 70, 8, "UP");
        assertEquals(70.5 + CabinetPlacement.FACE_OFFSET + CabinetPlacement.DEPTH_BIAS, up.center().y(), 1e-12);
        assertEquals(8.5, up.center().z(), 1e-12);
    }

    @Test void hyperDogExteriorExactlyFitsOneBlockWithFullSizeGuiFlowers(){
        // Previous exterior mapped a 1415px GUI face onto a 1340px physical
        // scale: 1.05597 blocks. Every facing must now fit 1.00000 block.
        for(String facing:new String[]{"NORTH","SOUTH","EAST","WEST","UP","DOWN"}){
            CabinetPlacement.Basis legacy=CabinetPlacement.basis(12,70,34,facing);
            CabinetPlacement.Basis fitted=CabinetPlacement.fitWideFaceToBlock(legacy,1415.0,1340.0);
            assertEquals(1340.0/1415.0,length(fitted.right()),1e-12,facing);
            // Equivalent to mapping the original (290..1705) GUI rectangle
            // with CAB_X=290 and CAB_W=1340, before the fitted basis.
            double left=-.5;
            double right=1415.0/1340.0-.5;
            var worldLeft=CabinetPlacement.point(fitted,left,0.0);
            var worldRight=CabinetPlacement.point(fitted,right,0.0);
            assertEquals(1.0,length(new CabinetPlacement.Vec(
                    worldRight.x()-worldLeft.x(),
                    worldRight.y()-worldLeft.y(),
                    worldRight.z()-worldLeft.z())),1e-12,facing);
            var mid=new CabinetPlacement.Vec(
                    (worldLeft.x()+worldRight.x())/2,
                    (worldLeft.y()+worldRight.y())/2,
                    (worldLeft.z()+worldRight.z())/2);
            assertEquals(0.0,length(new CabinetPlacement.Vec(
                    mid.x()-legacy.center().x(),
                    mid.y()-legacy.center().y(),
                    mid.z()-legacy.center().z())),1e-12,facing);
            assertEquals(1.0,length(legacy.right()),1e-12,
                    "ordinary machines must not be resized");
        }
        assertThrows(IllegalArgumentException.class,()->
                CabinetPlacement.fitWideFaceToBlock(
                        CabinetPlacement.basis(0,64,0,"NORTH"),0,1340));
    }

    private static double length(CabinetPlacement.Vec v) { return Math.sqrt(dot(v,v)); }
    private static double dot(CabinetPlacement.Vec a, CabinetPlacement.Vec b) {
        return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();
    }
}
