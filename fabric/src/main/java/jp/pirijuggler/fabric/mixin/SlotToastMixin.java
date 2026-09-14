package jp.pirijuggler.fabric.mixin;
import jp.pirijuggler.fabric.ui.SlotUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ToastManager.class)
public abstract class SlotToastMixin {
    @Inject(method="draw",at=@At("HEAD"),cancellable=true)
    private void piri$keepSlotUnobscured(DrawContext context,CallbackInfo ci){if(SlotUi.hidesHud())ci.cancel();}
}
