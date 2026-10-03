package jp.pirijuggler.runtime.client;

import com.google.gson.*;
import net.minecraft.client.sound.SoundInstance;

/** Test-only observation of the actual Minecraft SoundManager playback boundary. */
public final class SkillAudioAudit {
    private static final JsonArray events=new JsonArray();
    public static void played(SoundInstance sound){
        if(!sound.getId().getNamespace().equals("piri"))return;
        var event=new JsonObject();event.addProperty("sound",sound.getId().toString());event.addProperty("relative",sound.isRelative());event.addProperty("atNanos",System.nanoTime());events.add(event);
    }
    public static JsonArray events(){return events.deepCopy();}
    private SkillAudioAudit(){}
}
