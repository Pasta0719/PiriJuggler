package jp.pirijuggler.runtime.mixin;

import jp.pirijuggler.runtime.client.RuntimeProbe;

import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.fabric.network.ClientHandshake;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only mutation of the outgoing HELLO, isolated from all production jars. */
@Mixin(value = ClientHandshake.class, remap = false)
public abstract class HandshakeObserverMixin {
    @Inject(method = "begin", at = @At("RETURN"), cancellable = true)
    private void alterTestHello(CallbackInfoReturnable<Envelope> callback) {
        if (RuntimeProbe.mismatch()) {
            Envelope original = callback.getReturnValue();
            var payload = original.payload();
            payload.addProperty("protocol", 2);
            callback.setReturnValue(new Envelope(2, original.packetType(), payload));
        }
        RuntimeProbe.sent(callback.getReturnValue());
    }

    @Inject(method = "receive", at = @At("RETURN"))
    private void observeServerReply(Envelope packet, CallbackInfo callback) {
        RuntimeProbe.received(packet, ((ClientHandshake) (Object) this).canUseSlot());
    }
}
