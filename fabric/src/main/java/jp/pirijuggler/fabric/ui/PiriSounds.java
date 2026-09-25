package jp.pirijuggler.fabric.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import java.util.*;

/** Accepts user-supplied OGG resources. No synthesis, encoding or replacement audio. */
public final class PiriSounds {
    private static final List<String> BASE=List.of("notice","notice_strong","tenpai","bet","lever","stop","payout","error","bonus_start","bonus_end","big_bgm","reg_bgm");
    public static final List<String> NAMES;
    static {
        var names=new ArrayList<String>(BASE);
        for(String base:BASE)names.add("juggler_god_"+base);
        names.add("god_freeze");
        names.addAll(List.of(
                "juggler_god_god_freeze",
                "juggler_god_god_stop_1",
                "juggler_god_god_stop_2",
                "juggler_god_god_stop_3",
                "juggler_god_god_bonus_start",
                "juggler_god_god_bonus_end",
                "juggler_god_god_big_bgm"
        ));
        NAMES=List.copyOf(names);
    }
    private static final Map<String,SoundEvent> EVENTS=new HashMap<>();
    private static final PriorityQueue<Pending> QUEUE=new PriorityQueue<>(Comparator.comparingLong(Pending::at));
    private static SoundInstance loop;private static String loopName;private static final List<SoundInstance> ONE_SHOTS=new ArrayList<>();
    private record Pending(String name,long at){}
    private static final class LoopSound extends AbstractSoundInstance {
        private LoopSound(SoundEvent event){super(event,SoundCategory.MASTER,SoundInstance.createRandom());repeat=true;repeatDelay=0;relative=true;attenuationType=SoundInstance.AttenuationType.NONE;volume=0.45f;pitch=1.0f;}
    }
    public static void register(){for(String name:NAMES){var id=Identifier.of("piri",name);EVENTS.put(name,Registry.register(Registries.SOUND_EVENT,id,SoundEvent.of(id)));}}
    public static void queue(String name,int count,long spacingNanos){queueAfter(name,count,spacingNanos,0);}
    public static void queueAfter(String name,int count,long spacingNanos,long delayNanos){if(!EVENTS.containsKey(name))throw new IllegalArgumentException(name);long now=System.nanoTime()+Math.max(0L,delayNanos);for(int i=0;i<count;i++)QUEUE.add(new Pending(name,now+spacingNanos*i));}
    public static void tick(){long now=System.nanoTime();while(!QUEUE.isEmpty()&&QUEUE.peek().at<=now)play(QUEUE.remove().name);}
    public static boolean available(String name){return NAMES.contains(name)&&MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of("piri","sounds/"+name+".ogg")).isPresent();}
    public static String forMachine(String machineType,String base){
        if("JUGGLER_GOD".equals(machineType)||"JUGGLER_GOD_EXTREME".equals(machineType)){
            String dedicated="juggler_god_"+base;
            if(available(dedicated))return dedicated;
        }
        return base;
    }
    public static void play(String name){
        if(!available(name))return;
        SoundInstance sound=PositionedSoundInstance.master(EVENTS.get(name),1);
        ONE_SHOTS.add(sound);
        MinecraftClient.getInstance().getSoundManager().play(sound);
    }
    public static void startLoop(String name){
        if(!name.equals("big_bgm")&&!name.equals("reg_bgm")&&!name.equals("juggler_god_big_bgm")&&!name.equals("juggler_god_reg_bgm")&&!name.equals("juggler_god_god_big_bgm"))throw new IllegalArgumentException(name);if(name.equals(loopName)&&loop!=null)return;stopLoop();
        if(!available(name))return;loopName=name;loop=new LoopSound(EVENTS.get(name));MinecraftClient.getInstance().getSoundManager().play(loop);
    }
    public static void stopLoop(){if(loop!=null)MinecraftClient.getInstance().getSoundManager().stop(loop);loop=null;loopName=null;}
    public static void reset(){QUEUE.clear();stopLoop();}
    private PiriSounds(){}
}
