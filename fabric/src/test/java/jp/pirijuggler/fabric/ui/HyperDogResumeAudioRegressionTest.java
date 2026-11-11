package jp.pirijuggler.fabric.ui;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogResumeAudioRegressionTest {
    private static Envelope packet(PacketType type,String... data){
        JsonObject body=new JsonObject();
        for(int i=0;i<data.length;i+=2){
            if("resumeSnapshot".equals(data[i])||"automatic".equals(data[i]))
                body.addProperty(data[i],Boolean.parseBoolean(data[i+1]));
            else body.addProperty(data[i],data[i+1]);
        }
        return Envelope.current(type,body);
    }
    @Test void replayDoesNotResoundButFutureReelEventsDo(){
        List<String> sounds=new ArrayList<>();
        HyperDogAudio audio=new HyperDogAudio(sounds::add);
        audio.receive(packet(PacketType.SPIN_START,"spinId","s1","mode","NORMAL","resumeSnapshot","true"));
        audio.receive(packet(PacketType.REEL_STOP,"spinId","s1","reel","LEFT","resumeSnapshot","true"));
        audio.receive(packet(PacketType.NOTICE,"sound","NOTICE_STRONG","resumeSnapshot","true"));
        assertEquals(List.of(),sounds,"restore only");
        audio.receive(packet(PacketType.REEL_STOP,"spinId","s1","reel","LEFT"));
        assertEquals(List.of(),sounds,"deduplicated restored stop");
        audio.receive(packet(PacketType.REEL_STOP,"spinId","s1","reel","RIGHT"));
        audio.receive(packet(PacketType.SPIN_START,"spinId","s2","mode","NORMAL"));
        assertEquals(List.of("stop","lever"),sounds,"new events audible exactly once");
    }
}
