package jp.pirijuggler.fabric.ui;

/**
 * The HYPER_DOG lamps are recessed illuminated parts, not solid color panels.
 * One set of exact geometry is shared by the clickable cabinet GUI and the
 * world-view cabinet; both views keep visible dark margins even while lit.
 *
 * The twin announcement frames use the post-playtest reel-side rectangles;
 * their data-lamp and reel clearances are verified in geometry tests.
 */
public final class HyperDogLampGeometry {
    public record Layers(SlotLayout.Rect frame, SlotLayout.Rect gasket,
                         SlotLayout.Rect lens, SlotLayout.Rect emitter) {}

    public static Layers announcement(int index) {
        SlotLayout.Rect r = HyperDogCabinetState.announcementRect(index);
        // 104x104 frame: 4px metal rim / 6px gasket / 7px dark lens.
        // The 70x70 light keeps 17px of hardware around every edge.
        return new Layers(r, inset(r, 4), inset(r, 10), inset(r, 17));
    }

    public static Layers right(int index) {
        SlotLayout.Rect r = HyperDogCabinetState.rightLampRect(index);
        // 34x34: the 14px middle emitter still has 10px of visible surround.
        return new Layers(r, inset(r, 2), inset(r, 5), inset(r, 10));
    }

    public static Layers left(int index) {
        SlotLayout.Rect r = HyperDogCabinetState.leftStatusRect(index);
        // 146x36: preserve the low, wide silhouette of the established lamps.
        return new Layers(r, inset(r, 2), inset(r, 4), inset(r, 7));
    }

    private static SlotLayout.Rect inset(SlotLayout.Rect r, int amount) {
        if(amount < 0 || r.w() <= amount * 2 || r.h() <= amount * 2)
            throw new IllegalArgumentException("lamp inset exceeds frame");
        return new SlotLayout.Rect(r.x() + amount, r.y() + amount,
                r.w() - amount * 2, r.h() - amount * 2);
    }

    private HyperDogLampGeometry() {}
}
