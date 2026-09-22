package jp.pirijuggler.runtime.mixin;
import jp.pirijuggler.fabric.ui.GodScreen;
import jp.pirijuggler.fabric.ui.SlotViewState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=GodScreen.class,remap=false)
public interface GodViewAccessor { @Accessor("view") SlotViewState piri$view(); }
