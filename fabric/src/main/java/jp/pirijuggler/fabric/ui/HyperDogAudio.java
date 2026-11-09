package jp.pirijuggler.fabric.ui;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * HYPER_DOG-only reuse of registered OGGs. Missing OGGs are silently ignored
 * by PiriSounds.play; this class never changes or suppresses other machines.
 * Only already-authorized owner packets may trigger effects.
 */
public final class HyperDogAudio {
    private final Consumer<String> play;
    private final Set<Long> flashed=new HashSet<>();
    private String lastPublicGameState="",lastStage="";
    public HyperDogAudio(Consumer<String> play){this.play=Objects.requireNonNull(play);}
    public void reset(){flashed.clear();lastPublicGameState="";lastStage="";}
    public void receive(Envelope packet) {
        try {
            JsonObject j=packet.payload();
            switch(packet.packetType()){
                case ACTION_ACCEPTED -> {
                    if("SPACE_ACTION".equals(string(j,"action"))&&
                            "SEATED_READY".equals(lastPublicGameState))play.accept("bet");
                }
                case SPIN_START -> {
                    play.accept("lever");
                    if("AT_RED7_CONFIRM".equals(string(j,"mode")))play.accept("notice");
                }
                case REEL_STOP -> {
                    play.accept("stop");
                    if(j.has("automatic")&&j.get("automatic").getAsBoolean())play.accept("tenpai");
                }
                case TENPAI_SOUND -> play.accept("tenpai");
                case PAYOUT -> play.accept("payout");
                case NOTICE -> play.accept("NOTICE_STRONG".equals(string(j,"sound"))?
                        "notice_strong":"notice");
                case PUBLIC_STATE -> {
                    String next=string(j,"atStage");
                    if("UPPER_CZ".equals(lastStage)&&!next.isEmpty()&&!next.equals(lastStage))
                        play.accept("UPPER_INITIAL".equals(next)?"bonus_start":"bonus_end");
                    if(j.has("flashEventId")&&j.has("awardRevealed")&&
                            j.get("awardRevealed").getAsBoolean()){
                        long id=j.get("flashEventId").getAsLong();
                        if(id>0&&flashed.add(id)){
                            long award=j.has("flashAward")?j.get("flashAward").getAsLong():0;
                            if(award>=10)play.accept(award>=500?"notice_strong":"payout");
                        }
                    }
                    lastStage=next;
                    String state=string(j,"gameState");
                    if(!state.isEmpty())lastPublicGameState=state;
                }
                default -> {}
            }
        }catch(RuntimeException ignored){
            // Audio is optional: never abort a validated session or input on bad sound metadata.
        }
    }
    private static String string(JsonObject o,String field){
        return o.has(field)&&o.get(field).isJsonPrimitive()?o.get(field).getAsString():"";
    }
}
