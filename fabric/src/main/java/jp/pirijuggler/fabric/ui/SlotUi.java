package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.fabric.network.ClientSession;
import net.minecraft.client.MinecraftClient;
import java.util.*;
import java.util.function.Consumer;

public final class SlotUi {
    private static SlotViewState view;private static SlotInput input;
    private static final Map<Long,String> ACCEPT_SOUNDS=new LinkedHashMap<>();
    public static boolean hidesHud(){var screen=MinecraftClient.getInstance().currentScreen;return screen instanceof SlotScreen||screen instanceof SlotChatScreen;}
    public static void receive(Envelope packet,ClientSession session,Consumer<Envelope> sender){
        var client=MinecraftClient.getInstance();var b=packet.payload();
        if(packet.packetType()==PacketType.OPEN_MACHINE){
            reset();view=new SlotViewState(System::nanoTime);input=new SlotInput(session,envelope->{
                if(envelope.packetType()==PacketType.SPACE_ACTION){String state=view.value("gameState");String sound=state.contains("BETTED")||state.equals("REPLAY_READY")?"lever":state.contains("SPINNING")?"stop":"bet";ACCEPT_SOUNDS.put(envelope.payload().get("clientSequence").getAsLong(),sound);if(ACCEPT_SOUNDS.size()>128)ACCEPT_SOUNDS.remove(ACCEPT_SOUNDS.keySet().iterator().next());}
                sender.accept(envelope);
            },System::nanoTime,view::canSend);view.receive(packet);client.setScreen(new SlotScreen(view,input));return;
        }
        if(view==null)return;
        switch(packet.packetType()) {
            case NOTICE -> {if(view.matchesSpin(b))switch(b.get("sound").getAsString()){case "NOTICE"->PiriSounds.queue("notice",1,0);case "NOTICE_STRONG"->PiriSounds.queue("notice_strong",1,0);case "NOTICE_X5"->PiriSounds.queue("notice",5,100_000_000);default->{}}}
            case SPIN_START -> {if(view.matches(b)&&!"RESUME_NORMAL".equals(b.get("animation").getAsString()))PiriSounds.queue("lever",1,0);}
            case REEL_STOP -> {if(view.matchesSpin(b))PiriSounds.queue("stop",1,0);}
            case TENPAI_SOUND -> {if(view.matchesSpin(b))PiriSounds.queue("tenpai",1,0);}
            case PAYOUT -> PiriSounds.queue("payout",1,0);
            case BONUS_START -> PiriSounds.queue("bonus_start",1,0);
            case BONUS_END -> PiriSounds.queue("bonus_end",1,0);
            case ACTION_ACCEPTED -> {String sound=ACCEPT_SOUNDS.remove(b.get("clientSequence").getAsLong());if("bet".equals(sound))PiriSounds.queue(sound,1,0);}
            case ACTION_REJECTED,ERROR -> {ACCEPT_SOUNDS.clear();PiriSounds.queue("error",1,0);}
            default -> {}
        }
        view.receive(packet);
        if((packet.packetType()==PacketType.SESSION_END||packet.packetType()==PacketType.SESSION_SUSPENDED)&&session.sessionId()==null){if(hidesHud())client.setScreen(null);reset();}
    }
    public static void tick(){PiriSounds.tick();if(input!=null&&input.closeExpired()&&hidesHud())MinecraftClient.getInstance().setScreen(null);}
    public static void reset(){view=null;input=null;ACCEPT_SOUNDS.clear();PiriSounds.reset();}
    private SlotUi(){}
}
