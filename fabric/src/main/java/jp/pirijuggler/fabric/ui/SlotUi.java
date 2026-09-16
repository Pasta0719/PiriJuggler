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
    private static Envelope queuedLever;private static Consumer<Envelope> outbound;
    public static boolean hidesHud(){var screen=MinecraftClient.getInstance().currentScreen;return screen instanceof SlotScreen||screen instanceof SlotChatScreen||screen instanceof AdminScreen;}
    public static void receive(Envelope packet,ClientSession session,Consumer<Envelope> sender){
        var client=MinecraftClient.getInstance();var b=packet.payload();
        if(packet.packetType()==PacketType.OPEN_MACHINE){
            reset();outbound=sender;view=new SlotViewState(System::nanoTime);input=new SlotInput(session,envelope->{
                int pressed=view.localInput(envelope.packetType());
                if(pressed>=0){envelope.payload().addProperty("pressedIndex",pressed);PiriSounds.queue("stop",1,0);}
                if(envelope.packetType()==PacketType.SPACE_ACTION){String state=view.value("gameState");String sound=state.contains("BETTED")||state.equals("REPLAY_READY")?"lever":state.contains("SPINNING")?"stop":"bet";ACCEPT_SOUNDS.put(envelope.payload().get("clientSequence").getAsLong(),sound);if(ACCEPT_SOUNDS.size()>128)ACCEPT_SOUNDS.remove(ACCEPT_SOUNDS.keySet().iterator().next());}
                if(envelope.packetType()==PacketType.CLOSE_REQUEST)queuedLever=null;
                if(envelope.packetType()==PacketType.SPACE_ACTION&&view.shouldQueueLever()){
                    if(queuedLever==null)queuedLever=envelope;
                    return;
                }
                sender.accept(envelope);
            },System::nanoTime,action->view.canSend(action)
                    &&!(action==PacketType.SPACE_ACTION&&queuedLever!=null)
                    &&(view.nextGameRemainingNanos()==0||action==PacketType.SPACE_ACTION||action==PacketType.CLOSE_REQUEST));view.receive(packet);
            String state=view.value("gameState");if(state.startsWith("BIG_"))PiriSounds.startLoop("big_bgm");else if(state.startsWith("REG_"))PiriSounds.startLoop("reg_bgm");
            client.setScreen(new SlotScreen(view,input));return;
        }
        if(view==null)return;
        switch(packet.packetType()) {
            case NOTICE -> {if(view.matchesSpin(b))switch(b.get("sound").getAsString()){case "NOTICE"->PiriSounds.queue("notice",1,0);case "NOTICE_STRONG"->PiriSounds.queue("notice_strong",1,0);case "NOTICE_X5"->PiriSounds.queue("notice",5,100_000_000);default->{}}}
            case SPIN_START -> {if(view.matches(b)&&!"RESUME_NORMAL".equals(b.get("animation").getAsString()))PiriSounds.queue("lever",1,0);}
            case TENPAI_SOUND -> {if(view.matchesSpin(b))PiriSounds.queue("tenpai",1,0);}
            case PAYOUT -> PiriSounds.queue("payout",1,0);
            case BONUS_START -> {
                if(b.has("bonusType")){
                    String type=b.get("bonusType").getAsString();
                    if("BIG".equals(type)){PiriSounds.queue("bonus_start",1,0);pendingBigBgmAt=System.nanoTime()+BIG_BGM_START_DELAY_NANOS;}
                    else if("REG".equals(type)){pendingBigBgmAt=-1L;PiriSounds.startLoop("reg_bgm");}
                }
            }
            case BONUS_END -> {
                pendingBigBgmAt=-1L;PiriSounds.stopLoop();
                if(b.has("bonusType")&&"BIG".equals(b.get("bonusType").getAsString()))PiriSounds.queue("bonus_end",1,0);
            }
            case PUBLIC_STATE -> {if(view.matches(b)&&"SEATED_READY".equals(b.get("gameState").getAsString())){pendingBigBgmAt=-1L;PiriSounds.stopLoop();}}
            case ACTION_ACCEPTED -> {String sound=ACCEPT_SOUNDS.remove(b.get("clientSequence").getAsLong());if("bet".equals(sound))PiriSounds.queue(sound,1,0);}
            case ACTION_REJECTED,ERROR -> {ACCEPT_SOUNDS.clear();queuedLever=null;PiriSounds.queue("error",1,0);}
            default -> {}
        }
        view.receive(packet);
        if((packet.packetType()==PacketType.SESSION_END||packet.packetType()==PacketType.SESSION_SUSPENDED)&&session.sessionId()==null){if(hidesHud())client.setScreen(null);reset();}
    }
    public static void tick(){
        if(queuedLever!=null&&view!=null&&view.queuedLeverReady()&&outbound!=null){Envelope lever=queuedLever;queuedLever=null;outbound.accept(lever);}
        if(pendingBigBgmAt>=0&&System.nanoTime()>=pendingBigBgmAt){pendingBigBgmAt=-1L;PiriSounds.startLoop("big_bgm");}
        PiriSounds.tick();if(input!=null&&input.closeExpired()&&hidesHud())MinecraftClient.getInstance().setScreen(null);
    }
    public static void reset(){view=null;input=null;queuedLever=null;outbound=null;ACCEPT_SOUNDS.clear();pendingBigBgmAt=-1L;PiriSounds.reset();}
    private SlotUi(){}
}
