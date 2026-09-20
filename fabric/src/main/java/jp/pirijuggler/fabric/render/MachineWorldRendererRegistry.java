package jp.pirijuggler.fabric.render;

/**
 * World-render dispatch boundary for machine families.
 *
 * Only the existing JUGGLER renderer is registered today. Unknown/new families are
 * intentionally not drawn as a Juggler cabinet.
 */
public final class MachineWorldRendererRegistry {
    private MachineWorldRendererRegistry() {}

    public static boolean hasRenderer(String machineType) {
        return "JUGGLER".equals(machineType);
    }
}
