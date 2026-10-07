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
import java.util.function.Predicate;

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
    private static SoundInstance loop;private static String loopName;private static final List<SoundInstance> ONE_SHOTS=new ArrayList<>();private static final Map<Integer,PositionalLoopSound> REMOTE_LOOPS=new HashMap<>();private static final Map<Integer,List<SoundInstance>> REMOTE_ONE_SHOTS=new HashMap<>();
    private record Pending(String name,long at){}
    private static final class PositionalLoopSound extends AbstractSoundInstance {
        private PositionalLoopSound(SoundEvent event,double x,double y,double z,float volume){super(event,SoundCategory.MASTER,SoundInstance.createRandom());repeat=true;repeatDelay=0;relative=false;attenuationType=SoundInstance.AttenuationType.NONE;this.volume=volume;pitch=1.0f;this.x=x;this.y=y;this.z=z;}
        private void volume(float value){this.volume=Math.max(0f,value);}
    }
    private static final class PositionalOneShotSound extends AbstractSoundInstance {
        private PositionalOneShotSound(SoundEvent event,double x,double y,double z,float volume){super(event,SoundCategory.MASTER,SoundInstance.createRandom());repeat=false;repeatDelay=0;relative=false;attenuationType=SoundInstance.AttenuationType.NONE;this.volume=volume;pitch=1.0f;this.x=x;this.y=y;this.z=z;}
    }
    private static final class LoopSound extends AbstractSoundInstance {
        private LoopSound(SoundEvent event){super(event,SoundCategory.MASTER,SoundInstance.createRandom());repeat=true;repeatDelay=0;relative=true;attenuationType=SoundInstance.AttenuationType.NONE;volume=0.45f;pitch=1.0f;}
    }
    public static void register(){for(String name:NAMES){var id=Identifier.of("piri",name);EVENTS.put(name,Registry.register(Registries.SOUND_EVENT,id,SoundEvent.of(id)));}}
    public static void queue(String name,int count,long spacingNanos){queueAfter(name,count,spacingNanos,0);}
    public static void queueAfter(String name,int count,long spacingNanos,long delayNanos){if(!EVENTS.containsKey(name))throw new IllegalArgumentException(name);long now=System.nanoTime()+Math.max(0L,delayNanos);for(int i=0;i<count;i++)QUEUE.add(new Pending(name,now+spacingNanos*i));}
    public static void tick(){long now=System.nanoTime();while(!QUEUE.isEmpty()&&QUEUE.peek().at<=now)play(QUEUE.remove().name);}
    public static boolean available(String name){return NAMES.contains(name)&&MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of("piri","sounds/"+name+".ogg")).isPresent();}
    public static String forMachine(String machineType,String base){return resolveForMachine(machineType,base,PiriSounds::available);}
    static String resolveForMachine(String machineType,String base,Predicate<String> available){
        if("JUGGLER_GOD".equals(machineType)||"JUGGLER_GOD_EXTREME".equals(machineType)){
            String dedicated="juggler_god_"+base;
            if(available.test(dedicated))return dedicated;
        }
        return base;
    }
    public static void play(String name){
        if(!available(name))return;
        SoundInstance sound=PositionedSoundInstance.master(EVENTS.get(name),1);
        ONE_SHOTS.add(sound);
        MinecraftClient.getInstance().getSoundManager().play(sound);
    }
    public static void playAt(String name,double x,double y,double z){playAt(name,x,y,z,0.35f);}
    public static void playAt(String name,double x,double y,double z,float volume){
        if(!available(name))return;
        SoundInstance sound=new PositionedSoundInstance(EVENTS.get(name),SoundCategory.MASTER,volume,1,SoundInstance.createRandom(),x,y,z);
        ONE_SHOTS.add(sound);MinecraftClient.getInstance().getSoundManager().play(sound);
    }
    public static void playRemoteAt(int machineId,String name,double x,double y,double z,float volume){
        if(!available(name)||volume<=0f)return;
        SoundInstance sound=new PositionalOneShotSound(EVENTS.get(name),x,y,z,volume);
        REMOTE_ONE_SHOTS.computeIfAbsent(machineId,ignored->new ArrayList<>()).add(sound);
        MinecraftClient.getInstance().getSoundManager().play(sound);
    }
    public static void startLoop(String name){
        if(!name.equals("big_bgm")&&!name.equals("reg_bgm")&&!name.equals("juggler_god_big_bgm")&&!name.equals("juggler_god_reg_bgm")&&!name.equals("juggler_god_god_big_bgm"))throw new IllegalArgumentException(name);if(name.equals(loopName)&&loop!=null)return;stopLoop();
        if(!available(name))return;loopName=name;loop=new LoopSound(EVENTS.get(name));MinecraftClient.getInstance().getSoundManager().play(loop);
    }
    public static void stopLoop(){if(loop!=null)MinecraftClient.getInstance().getSoundManager().stop(loop);loop=null;loopName=null;}
    public static void startRemoteLoop(int machineId,String name,double x,double y,double z){startRemoteLoop(machineId,name,x,y,z,0.18f);}
    public static void startRemoteLoop(int machineId,String name,double x,double y,double z,float volume){
        stopRemoteLoop(machineId);if(!available(name))return;
        SoundInstance sound=new PositionalLoopSound(EVENTS.get(name),x,y,z,volume);REMOTE_LOOPS.put(machineId,sound);MinecraftClient.getInstance().getSoundManager().play(sound);
    }
    public static void updateRemoteLoopVolume(int machineId,float volume){PositionalLoopSound sound=REMOTE_LOOPS.get(machineId);if(sound!=null)sound.volume(volume);}
    public static void stopRemoteLoop(int machineId){SoundInstance sound=REMOTE_LOOPS.remove(machineId);if(sound!=null)MinecraftClient.getInstance().getSoundManager().stop(sound);}
    public static void stopRemoteOneShots(int machineId){
        var manager=MinecraftClient.getInstance().getSoundManager();var sounds=REMOTE_ONE_SHOTS.remove(machineId);
        if(sounds!=null)for(SoundInstance sound:sounds)manager.stop(sound);
    }
    public static void stopRemoteAudio(int machineId){stopRemoteOneShots(machineId);stopRemoteLoop(machineId);}
    public static void stopRemoteLoops(){var manager=MinecraftClient.getInstance().getSoundManager();for(SoundInstance sound:REMOTE_LOOPS.values())manager.stop(sound);REMOTE_LOOPS.clear();}
    public static void stopRemoteAudioAll(){
        var manager=MinecraftClient.getInstance().getSoundManager();
        for(var sounds:REMOTE_ONE_SHOTS.values())for(SoundInstance sound:sounds)manager.stop(sound);
        REMOTE_ONE_SHOTS.clear();stopRemoteLoops();
    }
    public static int remoteLoopCount(){return REMOTE_LOOPS.size();}
    public static void stopAll(){
        var manager=MinecraftClient.getInstance().getSoundManager();
        for(SoundInstance sound:ONE_SHOTS)manager.stop(sound);
        ONE_SHOTS.clear();QUEUE.clear();stopLoop();
    }
    public static void reset(){stopAll();}
    private PiriSounds(){}
}
