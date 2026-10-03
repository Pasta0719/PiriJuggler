package jp.pirijuggler.common.protocol;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SkillStopPresentationTest {
    @Test void publicTargetsRoundTripAndRejectInvalidState(){
        for(var target:SkillStopPresentation.Target.values()){
            var value=new SkillStopPresentation(20,target);var json=new JsonObject();value.write(json);
            assertEquals(value,SkillStopPresentation.read(json));assertEquals(2,json.size());
        }
        var invalid=new JsonObject();invalid.addProperty("skillRemaining",0);invalid.addProperty("skillChallenge","BAR");
        assertThrows(IllegalArgumentException.class,()->SkillStopPresentation.read(invalid));
        invalid.addProperty("skillRemaining",1.5);assertThrows(ArithmeticException.class,()->SkillStopPresentation.read(invalid));
        invalid.remove("skillChallenge");assertThrows(IllegalArgumentException.class,()->SkillStopPresentation.read(invalid));
        assertEquals(SkillStopPresentation.EMPTY,SkillStopPresentation.read(new JsonObject()));
    }
    @Test void successEventReplaysRemainSilentAcrossLaterSpins(){
        var gate=new SkillStopPresentation.NoticeGate();String first=UUID.randomUUID().toString();
        assertTrue(gate.accept(first));assertFalse(gate.accept(first));
        for(int i=0;i<100;i++)assertTrue(gate.accept(UUID.randomUUID().toString()));
        assertFalse(gate.accept(first));gate.clear();assertTrue(gate.accept(first));
    }
}
