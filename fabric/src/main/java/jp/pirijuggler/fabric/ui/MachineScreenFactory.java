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

    /** HYPER_DOG runs a fully isolated screen and never changes SlotScreen or GodScreen. */
    public static Screen createHyperDog(HyperDogClientReels reels,HyperDogCabinetState cabinet){
        return new HyperDogCabinetScreen(reels,cabinet);
    }

    public static Screen create(String machineType, SlotViewState view, SlotInput input) {
        String type = machineType == null ? "JUGGLER" : machineType;
        return switch (type) {
            case "SKILL_STOP", "JUGGLER", "JUGGLER_GOD", "JUGGLER_GOD_EXTREME" -> new SlotScreen(view, input);
            case "GOD" -> new GodScreen(view,input);
            case "PACHINKO" -> new PachinkoScreen(input);
            case "OKIDOKI", "DISC" -> new MachineUnavailableScreen(type, input);
            default -> new MachineUnavailableScreen(type, input);
        };
    }
}
