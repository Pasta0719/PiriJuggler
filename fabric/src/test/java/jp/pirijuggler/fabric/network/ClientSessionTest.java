package jp.pirijuggler.fabric.network;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ClientSessionTest {
    @Test void receivesOpenAndPublicStateThenResetsOnMatchingEndOrDisconnect() {
        ClientSession state = new ClientSession(); JsonObject payload = new JsonObject(); UUID id = UUID.randomUUID();
        payload.addProperty("sessionId",id.toString()); payload.addProperty("machineId",1); payload.addProperty("expectedNextClientSequence",5);
        state.receive(Envelope.current(PacketType.OPEN_MACHINE,payload),true); assertEquals(id,state.sessionId()); assertEquals(5,state.nextSequence());
        payload.addProperty("credit",32); state.receive(Envelope.current(PacketType.PUBLIC_STATE,payload),true);
        assertEquals(32,state.publicState().get("credit").getAsInt()); state.publicState().addProperty("credit",0); assertEquals(32,state.publicState().get("credit").getAsInt());
        JsonObject other = payload.deepCopy(); other.addProperty("sessionId",UUID.randomUUID().toString());
        state.receive(Envelope.current(PacketType.SESSION_END,other),true); assertEquals(id,state.sessionId());
        state.receive(Envelope.current(PacketType.SESSION_SUSPENDED,payload),true); assertNull(state.sessionId());
        state.receive(Envelope.current(PacketType.OPEN_MACHINE,payload),true);
        JsonObject end = new JsonObject(); end.addProperty("sessionId",id.toString());
        state.receive(Envelope.current(PacketType.SESSION_END,end),true); assertNull(state.sessionId());
        state.receive(Envelope.current(PacketType.OPEN_MACHINE,payload),false); assertNull(state.sessionId());
        state.receive(Envelope.current(PacketType.OPEN_MACHINE,payload),true); state.reset(); assertNull(state.sessionId());
    }
}
