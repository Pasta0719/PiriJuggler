package jp.pirijuggler.fabric.ui;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogAudioTest {
    private static Envelope p(PacketType type,JsonObject j){return Envelope.current(type,j);}
    @Test void existingOggMappingAndServerEventIdDedup(){
        List<String> sounds=new ArrayList<>();
        var audio=new HyperDogAudio(sounds::add);
        var ready=new JsonObject();ready.addProperty("gameState","SEATED_READY");
        audio.receive(p(PacketType.PUBLIC_STATE,ready));
        var bet=new JsonObject();bet.addProperty("action","SPACE_ACTION");
        audio.receive(p(PacketType.ACTION_ACCEPTED,bet));
        var spin=new JsonObject();spin.addProperty("mode","AT_RED7_CONFIRM");
        audio.receive(p(PacketType.SPIN_START,spin));
        var stop=new JsonObject();stop.addProperty("automatic",true);
        audio.receive(p(PacketType.REEL_STOP,stop));
        var add=new JsonObject();
        add.addProperty("flashEventId",11);
        add.addProperty("flashAward",2000);
        add.addProperty("awardRevealed",true);
        add.addProperty("atStage","UPPER_AT");
        audio.receive(p(PacketType.PUBLIC_STATE,add));
        audio.receive(p(PacketType.PUBLIC_STATE,add));
        assertEquals(List.of("bet","lever","notice","stop","tenpai","notice_strong"),sounds);
        audio.reset();
        audio.receive(p(PacketType.PUBLIC_STATE,add));
        assertEquals(7,sounds.size());
    }
    @Test void noPreCommitAwardAndCzWinLossUseExistingAudio(){
        List<String> sounds=new ArrayList<>();
        var audio=new HyperDogAudio(sounds::add);
        var cz=new JsonObject();cz.addProperty("atStage","UPPER_CZ");
        audio.receive(p(PacketType.PUBLIC_STATE,cz));
        var leak=new JsonObject();leak.addProperty("flashEventId",99);
        leak.addProperty("flashAward",2000);leak.addProperty("awardRevealed",false);
        audio.receive(p(PacketType.PUBLIC_STATE,leak));
        assertTrue(sounds.isEmpty());
        audio.receive(p(PacketType.PUBLIC_STATE,cz));
        var win=new JsonObject();win.addProperty("atStage","UPPER_INITIAL");
        audio.receive(p(PacketType.PUBLIC_STATE,win));
        assertEquals(List.of("bonus_start"),sounds);
        audio.receive(p(PacketType.PUBLIC_STATE,cz));
        var lose=new JsonObject();lose.addProperty("atStage","LOWER_INITIAL");
        audio.receive(p(PacketType.PUBLIC_STATE,lose));
        assertEquals(List.of("bonus_start","bonus_end"),sounds);
    }
}
