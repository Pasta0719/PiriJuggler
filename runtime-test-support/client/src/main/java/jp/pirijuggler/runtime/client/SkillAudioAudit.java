package jp.pirijuggler.runtime.client;

import com.google.gson.*;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

/** Test-only observation of the actual Minecraft SoundManager playback boundary. */
public final class SkillAudioAudit {
    private static final JsonArray events=new JsonArray();
    private static final JsonArray hyperDogEvents=new JsonArray();
    public static void played(SoundInstance sound){
        // A test-only mirror of the REAL SoundManager.play boundary. HYPER_DOG
        // currently resolves many missing user OGGs to vanilla Minecraft IDs.
        // The original piri-only Skill audit must not suppress those events.
        if("hyperdog05-main".equals(System.getProperty("piri.runtime.scenario",""))){
            var event=new JsonObject();
            event.addProperty("sound",sound.getId().toString());
            event.addProperty("category",sound.getCategory().name());
            // SoundManager.play is intercepted before SoundInstance resolves its Sound.\n            // AbstractSoundInstance.getVolume() dereferences an unset sound here;\n            // reading it would CRASH Minecraft. Test master volume and decoded PCM separately.
            event.addProperty("relative",sound.isRelative());
            hyperDogEvents.add(event);
        }
        if(!sound.getId().getNamespace().equals("piri"))return;
        var event=new JsonObject();event.addProperty("sound",sound.getId().toString());event.addProperty("relative",sound.isRelative());event.addProperty("atNanos",System.nanoTime());events.add(event);
    }
    public static JsonArray events(){return events.deepCopy();}
    public static JsonArray hyperDogEvents(){return hyperDogEvents.deepCopy();}
    private SkillAudioAudit(){}
}
