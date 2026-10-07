package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.fabric.network.RemoteMachineRegistry;
import jp.pirijuggler.fabric.network.RemoteMachineViewState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.*;
import java.util.function.Predicate;

public final class HallAudio {
    static final double SE_RADIUS=16.0, BGM_RADIUS=12.0;
    static final float NORMAL_VOLUME=.35f, NOTICE_VOLUME=.45f, BGM_VOLUME=.18f;
    // Keep the original audible cutoff; only reshape in-range distance falloff and add wall occlusion.
    static final float WALL_OCCLUSION=.55f;

    private record LoopState(String sound,String machineType,int x,int y,int z,String dimension) {}
    private record Pending(int machineId,String op,String sound,long atNanos,int generation) implements Comparable<Pending>{
        @Override public int compareTo(Pending other){return Long.compare(atNanos,other.atNanos);}
    }

    private static final Map<Integer,LoopState> bonusLoops=new HashMap<>();
    private static final Map<Integer,String> desiredLoops=new HashMap<>();
    private static final PriorityQueue<Pending> pending=new PriorityQueue<>();
    private static final Map<Integer,Integer> generations=new HashMap<>();

    public static void publicNotice(RemoteMachineViewState machine,String sound){
        if(machine==null||!inRange(machine,SE_RADIUS))return;
        play(machine.machineId(),machine,sound);
    }

    public static void drain(RemoteMachineRegistry registry){
        PiriSounds.pruneRemoteOneShots();
        RemoteMachineRegistry.AudioEvent event;
        while((event=registry.pollAudioEvent())!=null){
            RemoteMachineViewState machine=registry.view(event.machineId());
            switch(event.op()){
                case "REMOVE" -> stopAll(event.machineId());
                case "SYNC" -> {
                    if(machine!=null)sync(machine);
                }
                case "STOP_ALL" -> stopAll(event.machineId());
                case "LOOP_STOP" -> {
                    cancelLoopStarts(event.machineId());
                    desiredLoops.remove(event.machineId());
                    stopLoop(event.machineId());
                }
                case "PLAY","LOOP_START" -> {
                    if(machine==null)break;
                    int generation=generations.getOrDefault(event.machineId(),0);
                    int count=Math.max(1,event.count());
                    for(int i=0;i<count;i++){
                        long delay=Math.max(0L,event.delayMs())+Math.max(0L,event.spacingMs())*i;
                        pending.add(new Pending(event.machineId(),event.op(),event.sound(),System.nanoTime()+delay*1_000_000L,generation));
                    }
                }
                default -> { }
            }
        }

        long now=System.nanoTime();
        while(!pending.isEmpty()&&pending.peek().atNanos()<=now){
            Pending next=pending.remove();
            if(next.generation()!=generations.getOrDefault(next.machineId(),0))continue;
            RemoteMachineViewState machine=registry.view(next.machineId());
            if(machine==null)continue;
            if("PLAY".equals(next.op()))play(next.machineId(),machine,next.sound());
            else if("LOOP_START".equals(next.op())){desiredLoops.put(next.machineId(),next.sound());startLoop(next.machineId(),machine,next.sound());}
        }

        for(var entry:new ArrayList<>(desiredLoops.entrySet())){
            int id=entry.getKey();RemoteMachineViewState machine=registry.view(id);
            if(machine==null||"NONE".equals(machine.bonusMode())){desiredLoops.remove(id);stopLoop(id);continue;}
            if(inRange(machine,BGM_RADIUS))startLoop(id,machine,entry.getValue());
            else stopLoop(id);
        }
    }

    private static void sync(RemoteMachineViewState machine){
        if("BIG".equals(machine.bonusMode())){
            String sound=machine.godFirstBigAudio()?"god_big_bgm":"big_bgm";
            desiredLoops.put(machine.machineId(),sound);startLoop(machine.machineId(),machine,sound);
        }else if("REG".equals(machine.bonusMode())){
            desiredLoops.put(machine.machineId(),"reg_bgm");startLoop(machine.machineId(),machine,"reg_bgm");
        }
    }

    private static void play(int machineId,RemoteMachineViewState machine,String logical){
        if(!inRange(machine,SE_RADIUS))return;
        String sound=resolve(machine,logical);
        float base=("notice".equals(logical)||"notice_strong".equals(logical)||"tenpai".equals(logical))?NOTICE_VOLUME:NORMAL_VOLUME;
        float volume=attenuatedVolume(machine,base,SE_RADIUS);
        PiriSounds.playRemoteAt(machineId,sound,machine.x()+.5,machine.y()+1.5,machine.z()+.5,volume);
    }

    private static void startLoop(int machineId,RemoteMachineViewState machine,String logical){
        if(!inRange(machine,BGM_RADIUS))return;
        String sound=resolve(machine,logical);
        float volume=attenuatedVolume(machine,BGM_VOLUME,BGM_RADIUS);
        LoopState current=bonusLoops.get(machineId);
        if(current!=null&&matches(current,machine,sound)){PiriSounds.updateRemoteLoopVolume(machineId,volume);return;}
        PiriSounds.startRemoteLoop(machineId,sound,machine.x()+.5,machine.y()+1.5,machine.z()+.5,volume);
        bonusLoops.put(machineId,new LoopState(sound,machine.machineType(),machine.x(),machine.y(),machine.z(),machine.dimension()));
    }

    private static String resolve(RemoteMachineViewState machine,String logical){return resolveLogical(machine.machineType(),logical,PiriSounds::available);}
    static String resolveLogical(String type,String logical,Predicate<String> available){
        return switch(logical){
            case "god_freeze" -> available.test("juggler_god_god_freeze")?"juggler_god_god_freeze":"god_freeze";
            case "god_stop_1","god_stop_2","god_stop_3" -> {
                String dedicated="juggler_god_"+logical;
                yield available.test(dedicated)?dedicated:PiriSounds.resolveForMachine(type,"stop",available);
            }
            case "god_bonus_start" -> available.test("juggler_god_god_bonus_start")?"juggler_god_god_bonus_start":PiriSounds.resolveForMachine(type,"bonus_start",available);
            case "god_bonus_end" -> available.test("juggler_god_god_bonus_end")?"juggler_god_god_bonus_end":PiriSounds.resolveForMachine(type,"bonus_end",available);
            case "god_big_bgm" -> available.test("juggler_god_god_big_bgm")?"juggler_god_god_big_bgm":PiriSounds.resolveForMachine(type,"big_bgm",available);
            default -> PiriSounds.resolveForMachine(type,logical,available);
        };
    }

    private static boolean matches(LoopState state,RemoteMachineViewState machine,String sound){
        return state!=null&&state.x()==machine.x()&&state.y()==machine.y()&&state.z()==machine.z()
                &&Objects.equals(state.dimension(),machine.dimension())&&Objects.equals(state.machineType(),machine.machineType())
                &&Objects.equals(state.sound(),sound);
    }

    private static boolean inRange(RemoteMachineViewState machine,double radius){
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.player==null||client.world==null)return false;
        String currentDimension=client.world.getRegistryKey().getValue().toString();
        if(machine.dimension()!=null&&!machine.dimension().isBlank()&&!machine.dimension().equals(currentDimension))return false;
        double dx=client.player.getX()-(machine.x()+.5),dy=client.player.getY()-(machine.y()+1.5),dz=client.player.getZ()-(machine.z()+.5);
        return withinRadius(dx,dy,dz,radius);
    }

    static boolean withinRadius(double dx,double dy,double dz,double radius){return dx*dx+dy*dy+dz*dz<=radius*radius;}

    static float distanceGain(double distance,double radius){
        if(radius<=0||distance>=radius)return 0f;
        double t=Math.max(0.0,distance/radius);
        return (float)Math.max(0.0,1.0-t*t);
    }

    private static float attenuatedVolume(RemoteMachineViewState machine,float base,double radius){
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.player==null||client.world==null)return 0f;
        Vec3d source=new Vec3d(machine.x()+.5,machine.y()+1.5,machine.z()+.5);
        Vec3d ear=client.player.getEyePos();
        double distance=source.distanceTo(ear);
        float gain=distanceGain(distance,radius);
        if(gain<=0f)return 0f;
        if(blocked(client,source,ear))gain*=WALL_OCCLUSION;
        return base*gain;
    }

    private static boolean blocked(MinecraftClient client,Vec3d source,Vec3d ear){
        Vec3d delta=ear.subtract(source);
        double length=delta.length();
        if(length<=1.0)return false;
        Vec3d start=source.add(delta.normalize().multiply(Math.min(.75,length*.25)));
        HitResult hit=client.world.raycast(new RaycastContext(start,ear,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,client.player));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getPos().squaredDistanceTo(ear)>.04;
    }


    private static void cancelLoopStarts(int machineId){
        pending.removeIf(p->p.machineId()==machineId&&"LOOP_START".equals(p.op()));
    }

    private static void stopLoop(int machineId){
        bonusLoops.remove(machineId);
        PiriSounds.stopRemoteLoop(machineId);
    }

    private static void stopAll(int machineId){
        generations.merge(machineId,1,Integer::sum);
        pending.removeIf(p->p.machineId()==machineId);
        desiredLoops.remove(machineId);bonusLoops.remove(machineId);
        PiriSounds.stopRemoteAudio(machineId);
    }

    public static void reset(){
        pending.clear();desiredLoops.clear();bonusLoops.clear();generations.clear();PiriSounds.stopRemoteAudioAll();
    }
    public static int activeBonusLoops(){return bonusLoops.size();}
    static int pendingEvents(){return pending.size();}
    private HallAudio(){}
}
