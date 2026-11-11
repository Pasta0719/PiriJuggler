package jp.pirijuggler.fabric.ui;
import jp.pirijuggler.common.protocol.Envelope;
import java.util.function.Consumer;

/** Marked replay packets are state reconstruction, not new audible game events. */
public final class HyperDogResumeAudioGuard {
    private HyperDogResumeAudioGuard(){}
    public static <T> void playIfFresh(Consumer<T> play,T sound,Envelope packet){
        var body=packet.payload();
        if(body!=null&&body.has("resumeSnapshot")&&body.get("resumeSnapshot").getAsBoolean())return;
        play.accept(sound);
    }
}
