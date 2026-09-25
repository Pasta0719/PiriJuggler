package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.fabric.network.ClientSession;
import net.minecraft.client.MinecraftClient;
import java.util.*;
import java.util.function.Consumer;

public final class SlotUi {
    private static final long BIG_BGM_START_DELAY_NANOS=4_500_000_000L;
    private static SlotViewState view;private static SlotInput input;
    private static final Map<Long,String> ACCEPT_SOUNDS=new LinkedHashMap<>();
    private static long pendingBigBgmAt=-1L;
    private static String pendingBigBgmName,pendingGodHitSound;
    private static boolean godBigAudioPending,godBigAudioActive,awaitingInitialPublicState;
    private static Envelope queuedLever;private static Consumer<Envelope> outbound;

    public static boolean hidesHud(){var screen=MinecraftClient.getInstance().currentScreen;return screen instanceof SlotScreen||screen instanceof GodScreen||screen instanceof SlotChatScreen||screen instanceof AdminScreen;}

    public static void receive(Envelope packet,ClientSession session,Consumer<Envelope> sender){
        var client=MinecraftClient.getInstance();var b=packet.payload();
        if(packet.packetType()==PacketType.OPEN_MACHINE){
            reset();awaitingInitialPublicState=true;outbound=sender;view=new SlotViewState(System::nanoTime);input=new SlotInput(session,envelope->{
                int pressed=view.localInput(envelope.packetType());
                if(pressed>=0){
                    envelope.payload().addProperty("pressedIndex",pressed);
                    if("JUGGLER_GOD".equals(view.machineType())&&view.godFreeze()){
                        int ordinal=Math.max(1,Math.min(3,view.godStoppedCount()));
                        PiriSounds.queue(special("juggler_god_god_stop_"+ordinal,sound("stop")),1,0);
                    }else PiriSounds.queue(sound("stop"),1,0);
                }
                if(envelope.packetType()==PacketType.SPACE_ACTION){
                    String state=view.value("gameState");
                    String sound="GOD".equals(view.machineType())?(state.contains("SPINNING")?"stop":"lever"):(state.contains("BETTED")||state.equals("REPLAY_READY")?"lever":state.contains("SPINNING")?"stop":"bet");
                    ACCEPT_SOUNDS.put(envelope.payload().get("clientSequence").getAsLong(),sound);
                    if(ACCEPT_SOUNDS.size()>128)ACCEPT_SOUNDS.remove(ACCEPT_SOUNDS.keySet().iterator().next());
                }
                if(envelope.packetType()==PacketType.CLOSE_REQUEST)queuedLever=null;
                if(envelope.packetType()==PacketType.SPACE_ACTION&&view.shouldQueueLever()){
                    if(queuedLever==null)queuedLever=envelope;
                    return;
                }
                sender.accept(envelope);
            },System::nanoTime,action->view.canSend(action)
                    &&!(action==PacketType.SPACE_ACTION&&queuedLever!=null)
                    &&(view.nextGameRemainingNanos()==0||action==PacketType.SPACE_ACTION||action==PacketType.CLOSE_REQUEST));
            view.receive(packet);
            client.setScreen(MachineScreenFactory.create(view.machineType(),view,input));
            return;
        }

        if(view==null)return;
        switch(packet.packetType()) {
            case NOTICE -> {
                if(view.matchesSpin(b))switch(b.get("sound").getAsString()){
                    case "NOTICE"->PiriSounds.queue(sound("notice"),1,0);
                    case "NOTICE_STRONG"->PiriSounds.queue(sound("notice_strong"),1,0);
                    case "NOTICE_X5"->PiriSounds.queue(sound("notice"),5,100_000_000);
                    default->{}
                }
            }
            case SPIN_START -> {
                if(view.matches(b)){
                    boolean resumed="RESUME_NORMAL".equals(b.get("animation").getAsString());
                    boolean godFreeze=b.has("godFreeze")&&b.get("godFreeze").getAsBoolean();
                    if(godFreeze){
                        godBigAudioPending=true;
                        pendingBigBgmAt=-1L;pendingBigBgmName=null;pendingGodHitSound=null;
                        PiriSounds.stopAll();
                    }
                    if(!resumed)PiriSounds.queue(godFreeze?special("juggler_god_god_freeze","god_freeze"):sound("lever"),1,0);
                    if(godBigAudioActive&&"BIG_SPINNING".equals(view.value("gameState")))
                        PiriSounds.startLoop(special("juggler_god_god_big_bgm",sound("big_bgm")));
                }
            }
            case TENPAI_SOUND -> {if(view.matchesSpin(b))PiriSounds.queue(sound("tenpai"),1,0);}
            case PAYOUT -> PiriSounds.queue(sound("payout"),1,0);
            case BONUS_START -> {
                if(b.has("bonusType")){
                    String type=b.get("bonusType").getAsString();
                    if("BIG".equals(type)){
                        boolean godBig="JUGGLER_GOD".equals(view.machineType())&&(godBigAudioPending||view.godFirstBigAudio());
                        godBigAudioPending=false;godBigAudioActive=godBig;
                        String start=godBig?special("juggler_god_god_bonus_start",sound("bonus_start")):sound("bonus_start");
                        String bgm=godBig?special("juggler_god_god_big_bgm",sound("big_bgm")):sound("big_bgm");
                        if(godBig){
                            pendingBigBgmAt=-1L;pendingBigBgmName=null;
                            if(view.godPresentationActive()){
                                PiriSounds.queueAfter(start,1,0,view.godPresentationStartRemainingNanos());
                            }else pendingGodHitSound=start;
                        }else{
                            PiriSounds.queue(start,1,0);
                            pendingBigBgmName=bgm;pendingBigBgmAt=System.nanoTime()+BIG_BGM_START_DELAY_NANOS;
                        }
                    }else if("REG".equals(type)){
                        godBigAudioPending=false;godBigAudioActive=false;pendingBigBgmAt=-1L;pendingBigBgmName=null;
                        PiriSounds.startLoop(sound("reg_bgm"));
                    }
                }
            }
            case BONUS_END -> {
                boolean bigEnd=b.has("bonusType")&&"BIG".equals(b.get("bonusType").getAsString());
                boolean godFirstBigEnd=bigEnd&&"JUGGLER_GOD".equals(view.machineType())&&godBigAudioActive;
                pendingBigBgmAt=-1L;pendingBigBgmName=null;godBigAudioPending=false;godBigAudioActive=false;PiriSounds.stopLoop();
                if(bigEnd)PiriSounds.queue(godFirstBigEnd?special("juggler_god_god_bonus_end",sound("bonus_end")):sound("bonus_end"),1,0);
            }
            case PUBLIC_STATE -> {
                if(view.matches(b)){
                    boolean firstGodBig=b.has("godFirstBigAudio")&&b.get("godFirstBigAudio").getAsBoolean();
                    if(firstGodBig)godBigAudioPending=true;
                    String state=b.get("gameState").getAsString();
                    if(awaitingInitialPublicState){
                        awaitingInitialPublicState=false;
                        if(state.startsWith("BIG_")){
                            godBigAudioActive=firstGodBig;
                            PiriSounds.startLoop(firstGodBig?special("juggler_god_god_big_bgm",sound("big_bgm")):sound("big_bgm"));
                        }else if(state.startsWith("REG_"))PiriSounds.startLoop(sound("reg_bgm"));
                    }
                    if("SEATED_READY".equals(state)){
                        pendingBigBgmAt=-1L;pendingBigBgmName=null;godBigAudioPending=false;PiriSounds.stopLoop();
                    }
                }
            }
            case ACTION_ACCEPTED -> {
                String accepted=ACCEPT_SOUNDS.remove(b.get("clientSequence").getAsLong());
                if("bet".equals(accepted))PiriSounds.queue(sound("bet"),1,0);
            }
            case ACTION_REJECTED,ERROR -> {
                ACCEPT_SOUNDS.clear();queuedLever=null;PiriSounds.queue(sound("error"),1,0);
            }
            default -> {}
        }

        view.receive(packet);
        if((packet.packetType()==PacketType.SESSION_END||packet.packetType()==PacketType.SESSION_SUSPENDED)&&session.sessionId()==null){
            if(hidesHud())client.setScreen(null);reset();
        }
    }

    public static void tick(){
        if(pendingGodHitSound!=null&&view!=null&&view.godPresentationActive()){
            String hit=pendingGodHitSound;pendingGodHitSound=null;
            PiriSounds.queueAfter(hit,1,0,view.godPresentationStartRemainingNanos());
        }
        if(queuedLever!=null&&view!=null&&view.queuedLeverReady()&&outbound!=null){
            Envelope lever=queuedLever;queuedLever=null;outbound.accept(lever);
        }
        if(pendingBigBgmAt>=0&&System.nanoTime()>=pendingBigBgmAt){
            pendingBigBgmAt=-1L;
            String name=pendingBigBgmName;pendingBigBgmName=null;
            if(name!=null)PiriSounds.startLoop(name);
        }
        PiriSounds.tick();
        if(input!=null&&input.closeExpired()&&hidesHud())MinecraftClient.getInstance().setScreen(null);
    }

    private static String sound(String base){return PiriSounds.forMachine(view==null?null:view.machineType(),base);}
    private static String special(String dedicated,String fallback){return PiriSounds.available(dedicated)?dedicated:fallback;}

    public static void reset(){
        view=null;input=null;queuedLever=null;outbound=null;ACCEPT_SOUNDS.clear();
        pendingBigBgmAt=-1L;pendingBigBgmName=null;pendingGodHitSound=null;
        godBigAudioPending=false;godBigAudioActive=false;awaitingInitialPublicState=false;
        PiriSounds.reset();
    }

    private SlotUi(){}
}
