package jp.pirijuggler.fabric.ui;

import net.minecraft.client.gui.screen.Screen;

/**
 * Single dispatch point for machine-family full-screen UIs.
 *
 * JUGGLER keeps the existing SlotScreen. Additional machine families get their own
 * screen classes here instead of branching inside SlotScreen.
 */
public final class MachineScreenFactory {
    private MachineScreenFactory() {}

    public static Screen create(String machineType, SlotViewState view, SlotInput input) {
        String type = machineType == null ? "JUGGLER" : machineType;
        return switch (type) {
            case "JUGGLER" -> new SlotScreen(view, input);
            case "OKIDOKI", "GOD", "DISC" -> new MachineUnavailableScreen(type, input);
            default -> new MachineUnavailableScreen(type, input);
        };
    }
}
