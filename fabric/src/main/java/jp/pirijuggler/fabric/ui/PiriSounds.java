package jp.pirijuggler.fabric.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.*;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import java.util.*;

/** Accepts user-supplied OGG resources. No synthesis, encoding or replacement audio. */
public final class PiriSounds {
    public static final List<String> NAMES=List.of("notice","notice_strong","tenpai","bet","lever","stop","payout","error","bonus_start","bonus_end");
    private static final Map<String,SoundEvent> EVENTS=new HashMap<>();
    private static final PriorityQueue<Pending> QUEUE=new PriorityQueue<>(Comparator.comparingLong(Pending::at));
    private record Pending(String name,long at){}
    public static void register(){for(String name:NAMES){var id=Identifier.of("piri",name);EVENTS.put(name,Registry.register(Registries.SOUND_EVENT,id,SoundEvent.of(id)));}}
    public static void queue(String name,int count,long spacingNanos){if(!EVENTS.containsKey(name))throw new IllegalArgumentException(name);long now=System.nanoTime();for(int i=0;i<count;i++)QUEUE.add(new Pending(name,now+spacingNanos*i));}
    public static void tick(){long now=System.nanoTime();while(!QUEUE.isEmpty()&&QUEUE.peek().at<=now)play(QUEUE.remove().name);}
    public static boolean available(String name){return NAMES.contains(name)&&MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of("piri","sounds/"+name+".ogg")).isPresent();}
    public static void play(String name){if(available(name))MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(EVENTS.get(name),1));}
    public static void reset(){QUEUE.clear();}
    private PiriSounds(){}
}
