package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.fabric.network.RemoteMachineRegistry;
import jp.pirijuggler.fabric.network.RemoteMachineViewState;
import net.minecraft.client.MinecraftClient;
import java.util.*;

public final class HallAudio {
    static final double SE_RADIUS=16.0, BGM_RADIUS=12.0;
    static final float NORMAL_VOLUME=.35f, NOTICE_VOLUME=.45f, BGM_VOLUME=.18f;
    private record LoopState(String sound,int x,int y,int z,String dimension) {}
    private static final Map<Integer,LoopState> bonusLoops=new HashMap<>();

    public static void publicNotice(RemoteMachineViewState machine,String sound){
        if(machine==null||!inRange(machine,SE_RADIUS))return;
        float volume=("notice".equals(sound)||"notice_strong".equals(sound)||"tenpai".equals(sound))?NOTICE_VOLUME:NORMAL_VOLUME;
        PiriSounds.playAt(sound,machine.x()+.5,machine.y()+1.5,machine.z()+.5,volume);
    }

    public static void drain(RemoteMachineRegistry registry){
        RemoteMachineRegistry.AudioEvent event;
        while((event=registry.pollAudioEvent())!=null){
            if("REMOVE".equals(event.kind())||"BONUS_END".equals(event.kind())){stop(event.machineId());continue;}
            if("SOUND".equals(event.kind())){
                RemoteMachineViewState machine=registry.view(event.machineId());
                if(machine!=null)publicNotice(machine,event.bonusType().toLowerCase(Locale.ROOT));
                continue;
            }
            if(!"BONUS_START".equals(event.kind()))continue;
            RemoteMachineViewState machine=registry.view(event.machineId());
            if(machine!=null&&inRange(machine,BGM_RADIUS))start(event.machineId(),machine,event.bonusType());
        }

        for(var entry:registry.viewsSnapshot().entrySet()){
            int id=entry.getKey();
            RemoteMachineViewState machine=entry.getValue();
            String mode=machine.bonusMode();
            if("NONE".equals(mode)||!inRange(machine,BGM_RADIUS)){
                stop(id);
                continue;
            }
            LoopState wanted=loopState(machine,mode);
            if(!wanted.equals(bonusLoops.get(id)))start(id,machine,mode);
        }

        for(int id:new ArrayList<>(bonusLoops.keySet())){
            RemoteMachineViewState machine=registry.view(id);
            if(machine==null||"NONE".equals(machine.bonusMode())||!inRange(machine,BGM_RADIUS))stop(id);
        }
    }

    private static void start(int machineId,RemoteMachineViewState machine,String bonusType){
        String sound="BIG".equals(bonusType)?"big_bgm":"reg_bgm";
        String resolved=PiriSounds.forMachine(machine.machineType(),sound);
        PiriSounds.startRemoteLoop(machineId,resolved,machine.x()+.5,machine.y()+1.5,machine.z()+.5);
        bonusLoops.put(machineId,new LoopState(resolved,machine.x(),machine.y(),machine.z(),machine.dimension()));
    }

    private static LoopState loopState(RemoteMachineViewState machine,String bonusType){
        String sound="BIG".equals(bonusType)?"big_bgm":"reg_bgm";
        return new LoopState(PiriSounds.forMachine(machine.machineType(),sound),machine.x(),machine.y(),machine.z(),machine.dimension());
    }

    private static boolean inRange(RemoteMachineViewState machine,double radius){
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.player==null||client.world==null)return false;
        String currentDimension=client.world.getRegistryKey().getValue().toString();
        if(machine.dimension()!=null&&!machine.dimension().equals(currentDimension))return false;
        double dx=client.player.getX()-(machine.x()+.5),dy=client.player.getY()-(machine.y()+1.5),dz=client.player.getZ()-(machine.z()+.5);
        return withinRadius(dx,dy,dz,radius);
    }

    static boolean withinRadius(double dx,double dy,double dz,double radius){return dx*dx+dy*dy+dz*dz<=radius*radius;}

    private static void stop(int machineId){
        if(bonusLoops.remove(machineId)!=null)PiriSounds.stopRemoteLoop(machineId);
    }

    public static void reset(){bonusLoops.clear();PiriSounds.stopRemoteLoops();}
    public static int activeBonusLoops(){return bonusLoops.size();}
    private HallAudio(){}
}
