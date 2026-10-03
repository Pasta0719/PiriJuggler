package jp.pirijuggler.runtime.mixin;

import jp.pirijuggler.runtime.client.SkillAudioAudit;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class SkillSoundObserverMixin {
    @Inject(method="play(Lnet/minecraft/client/sound/SoundInstance;)V",at=@At("HEAD"))
    private void observe(SoundInstance sound,CallbackInfo callback){SkillAudioAudit.played(sound);}
}
