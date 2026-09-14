package jp.pirijuggler.runtime.mixin;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Mouse.class)
public interface MouseEventsInvoker {
    @Invoker("onMouseButton") void piri$button(long window,int button,int action,int modifiers);
    @Invoker("onCursorPos") void piri$cursor(long window,double x,double y);
}
