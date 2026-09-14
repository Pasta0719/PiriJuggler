package jp.pirijuggler.runtime.mixin;
import jp.pirijuggler.runtime.client.HudAudit;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameHud.class)
public abstract class HudAuditMixin {
    @Inject(method={"renderHotbar","renderCrosshair","renderStatusBars","renderExperienceBar","renderExperienceLevel","renderScoreboardSidebar"},at=@At("HEAD"))
    private void piri$auditHud(CallbackInfo ci){HudAudit.rendered();}
}
