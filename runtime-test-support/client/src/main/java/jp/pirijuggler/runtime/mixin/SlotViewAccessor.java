package jp.pirijuggler.runtime.mixin;
import jp.pirijuggler.fabric.ui.SlotScreen;
import jp.pirijuggler.fabric.ui.SlotViewState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=SlotScreen.class,remap=false)
public interface SlotViewAccessor { @Accessor("view") SlotViewState piri$view(); }
