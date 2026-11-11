package jp.pirijuggler.fabric.ui;

/**
 * Shared lamp geometry for the seated HYPER_DOG cabinet and world renderer.
 * The single chamfered CHANCE emblem leaves the status lamps, reel and
 * data-panel geometries intact.
 */
public final class HyperDogLampGeometry {
    public record Layers(SlotLayout.Rect frame, SlotLayout.Rect gasket,
                         SlotLayout.Rect lens, SlotLayout.Rect emitter) {}

    public static Layers announcement(int index) {
        SlotLayout.Rect r = HyperDogCabinetState.announcementRect(index);
        // Three tight nested borders maximize the illuminated/logo area.
        return new Layers(r, inset(r, 3), inset(r, 7), inset(r, 11));
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
