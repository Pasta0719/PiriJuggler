package jp.pirijuggler.fabric.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Geometry contract for both the seated HYPER_DOG GUI and its shared world-view
 * lamp coordinates. These are real production Rects, not duplicate test fixtures.
 * A change that puts a flower onto the existing data lamp/reels/side display
 * must fail before it can be accepted.
 */
class HyperDogLayoutRegressionTest {
    private static boolean overlaps(SlotLayout.Rect a, SlotLayout.Rect b) {
        return a.x() < b.x() + b.w() && b.x() < a.x() + a.w()
                && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
    }

    private static void clear(String aName, SlotLayout.Rect a,
                              String bName, SlotLayout.Rect b) {
        assertFalse(overlaps(a, b), aName + " overlaps " + bName
                + " (" + a + " versus " + b + ")");
    }

    private static void inside(String name, SlotLayout.Rect inner, SlotLayout.Rect outer) {
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y()
                        && inner.x() + inner.w() <= outer.x() + outer.w()
                        && inner.y() + inner.h() <= outer.y() + outer.h(),
                name + " must be contained by " + outer + ": " + inner);
    }

    @Test void bothLargeAnnouncementLampsKeepDataAndReelClearances() {
        var body = HyperDogCabinetState.cabinetRect();
        var rightData = HyperDogCabinetState.rightDataRect();
        var left = HyperDogCabinetState.announcementRect(0);
        var right = HyperDogCabinetState.announcementRect(1);

        assertEquals(1340, SlotLayout.CABINET.w(),
                "original JUGGLER cabinet width must remain unmodified");
        assertTrue(body.w() > SlotLayout.CABINET.w(),
                "HYPER_DOG's dedicated GUI geometry must not resize the shared cabinet");
        assertTrue(left.w() >= 100 && left.h() >= 100, "do not shrink the flower back to a tiny indicator");
        assertEquals(left.w(), right.w());
        assertEquals(left.h(), right.h());
        clear("left flower", left, "right flower", right);

        for (int i = 0; i < 2; i++) {
            var flower = HyperDogCabinetState.announcementRect(i);
            inside("flower " + i, flower, body);
            clear("flower " + i, flower, "top data lamp", SlotLayout.DATA);
            clear("flower " + i, flower, "existing reel window", SlotLayout.REELS);
            clear("flower " + i, flower, "left data panel", SlotLayout.DATA_LEFT);
            clear("flower " + i, flower, "HYPER_DOG right data panel", rightData);
            assertTrue(flower.y() - (SlotLayout.DATA.y() + SlotLayout.DATA.h()) >= 16,
                    "flower " + i + " needs a visible gap below the data lamp");
        }
        assertTrue(SlotLayout.REELS.x() - (left.x() + left.w()) >= 16,
                "left flower must leave a gap beside the reels");
        assertTrue(right.x() - (SlotLayout.REELS.x() + SlotLayout.REELS.w()) >= 16,
                "right flower must leave a gap beside the reels");
        assertTrue(rightData.x() - (right.x() + right.w()) >= 16,
                "right flower must not collide with the moved data sidebar");
    }

    @Test void everyPhysicalLampHasOpaqueBezelAndAllLightsRemainSeparated() {
        var cabinet = HyperDogCabinetState.cabinetRect();
        for (int i = 0; i < 2; i++) {
            var l = HyperDogLampGeometry.announcement(i);
            inside("flower gasket " + i, l.gasket(), l.frame());
            inside("flower lens " + i, l.lens(), l.gasket());
            inside("flower emitter " + i, l.emitter(), l.lens());
            assertTrue(l.emitter().w() <= l.frame().w() - 30);
            assertTrue(l.emitter().h() <= l.frame().h() - 30);
            assertEquals(HyperDogCabinetState.announcementRect(i), l.frame());
        }
        for (int i = 0; i < 3; i++) {
            var left = HyperDogLampGeometry.left(i);
            var right = HyperDogLampGeometry.right(i);
            inside("left lamp " + i, left.frame(), cabinet);
            inside("right lamp " + i, right.frame(), cabinet);
            inside("left lamp emitter " + i, left.emitter(), left.lens());
            inside("right lamp emitter " + i, right.emitter(), right.lens());
            for (int j = 0; j < 2; j++) {
                clear("left status " + i, left.frame(), "flower " + j,
                        HyperDogLampGeometry.announcement(j).frame());
                clear("right status " + i, right.frame(), "flower " + j,
                        HyperDogLampGeometry.announcement(j).frame());
            }
            clear("right status " + i, right.frame(), "right data",
                    HyperDogCabinetState.rightDataRect());
            for (int j = i + 1; j < 3; j++) {
                clear("left status " + i, left.frame(), "left status " + j,
                        HyperDogLampGeometry.left(j).frame());
                clear("right status " + i, right.frame(), "right status " + j,
                        HyperDogLampGeometry.right(j).frame());
            }
        }
    }

    @Test void fivePetalFlowersFitWithinTheirEmitterAndCannotPaintTheDataPanel() {
        // Mirrors the five 32x36 petals at radius 15 drawn by
        // HyperDogCabinetScreen.drawFlower; changing the renderer's petal
        // dimensions must be accompanied by updating this envelope check.
        for (int j = 0; j < 2; j++) {
            var lamp = HyperDogLampGeometry.announcement(j);
            var e = lamp.emitter();
            int cx = e.x() + e.w() / 2, cy = e.y() + e.h() / 2;
            for (int i = 0; i < 5; i++) {
                double a = Math.toRadians(-90 + i * 72);
                int px = cx + (int) Math.round(Math.cos(a) * 15);
                int py = cy + (int) Math.round(Math.sin(a) * 15);
                inside("flower " + j + " petal " + i,
                        new SlotLayout.Rect(px - 16, py - 18, 32, 36), e);
            }
        }
    }

    @Test void overlapDetectionItselfRejectsTheOriginalReportedFailureShape() {
        var flower = HyperDogCabinetState.announcementRect(1);
        var badSidebar = new SlotLayout.Rect(flower.x() + flower.w() - 1,
                flower.y(), 186, 430);
        assertTrue(overlaps(flower, badSidebar),
                "a one-pixel flower/sidebar collision must fail");
        var touchingOnly = new SlotLayout.Rect(flower.x() + flower.w(),
                flower.y(), 186, 430);
        assertFalse(overlaps(flower, touchingOnly),
                "touching edges without painted overlap are allowed");
    }
}
