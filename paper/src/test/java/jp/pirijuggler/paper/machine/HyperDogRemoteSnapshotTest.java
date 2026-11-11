package jp.pirijuggler.paper.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.pirijuggler.paper.session.Session;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogRemoteSnapshotTest {
    private static Machine machine(MachineType type){
        return new Machine(1,null,type,1,true,false,false,0,0,0,0L,0L);
    }
    private static JsonObject body(){
        JsonObject b=new JsonObject(),pos=new JsonObject();
        pos.addProperty("left",0);pos.addProperty("center",7);pos.addProperty("right",14);
        b.add("displayStops",pos);b.addProperty("spinning",true);b.addProperty("stoppedMask",0);
        return b;
    }
    private static Session stored(int l,int m,int r){
        var state=new JsonObject();var reel=new JsonObject();
        reel.addProperty("spinId",UUID.fromString("11111111-2222-4333-8444-555555555555").toString());
        var stops=new JsonArray();stops.add(l);stops.add(m);stops.add(r);
        var phases=new JsonArray();phases.add(8.0);phases.add(2.0);phases.add(13.0);
        reel.add("stops",stops);reel.add("startPhases",phases);state.add("reel",reel);
        return new Session(Map.of("machine_state_json",state.toString()));
    }
    @Test void spinningSnapshotProvidesValidIdAndStartPositions(){
        var b=body();HyperDogRemoteSnapshot.correct(machine(MachineType.HYPER_DOG),stored(-1,-1,-1),b);
        assertTrue(b.get("spinning").getAsBoolean());
        assertEquals("11111111-2222-4333-8444-555555555555",b.get("spinId").getAsString());
        assertEquals(8.0,b.getAsJsonObject("startPhase").get("left").getAsDouble());
        assertEquals(0,b.get("stoppedMask").getAsInt());
    }
    @Test void partialAndCompleteStopsAreRetained(){
        var b=body();HyperDogRemoteSnapshot.correct(machine(MachineType.HYPER_DOG),stored(3,-1,17),b);
        assertTrue(b.get("spinning").getAsBoolean());
        assertEquals(5,b.get("stoppedMask").getAsInt());
        assertEquals(3,b.getAsJsonObject("displayStops").get("left").getAsInt());
        assertEquals(17,b.getAsJsonObject("displayStops").get("right").getAsInt());
        b=body();HyperDogRemoteSnapshot.correct(machine(MachineType.HYPER_DOG),stored(1,2,3),b);
        assertFalse(b.get("spinning").getAsBoolean());
        assertEquals(7,b.get("stoppedMask").getAsInt());
    }
    @Test void noPhysicalSpinAndOtherMachines(){
        var dog=body();HyperDogRemoteSnapshot.correct(machine(MachineType.HYPER_DOG),
                new Session(Map.of("machine_state_json","{}")),dog);
        assertFalse(dog.get("spinning").getAsBoolean());
        var legacy=body();HyperDogRemoteSnapshot.correct(machine(MachineType.JUGGLER),stored(-1,-1,-1),legacy);
        assertTrue(legacy.get("spinning").getAsBoolean());
        assertFalse(legacy.has("spinId"));
    }
}
