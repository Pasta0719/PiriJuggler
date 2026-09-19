package jp.pirijuggler.runtime.mixin;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.fabric.network.RemoteMachineRegistry;
import jp.pirijuggler.runtime.client.Phase12RemoteProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only observation of Phase12 remote packets before production cache handling. */
@Mixin(value = RemoteMachineRegistry.class, remap = false)
public abstract class RemoteMachineObserverMixin {
    @Inject(method = "receive", at = @At("HEAD"))
    private void observeRemotePacket(Envelope envelope, CallbackInfo callback) {
        Phase12RemoteProbe.received(envelope);
    }
}
