package jp.pirijuggler.fabric.ui;
import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.fabric.network.ClientSession;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SlotInputTest {
    private static Envelope packet(PacketType type,String json){return Envelope.current(type,JsonParser.parseString(json).getAsJsonObject());}
    private static ClientSession session(){var s=new ClientSession();s.receive(packet(PacketType.OPEN_MACHINE,"{\"sessionId\":\"00000000-0000-0000-0000-000000000001\",\"machineId\":12,\"expectedNextClientSequence\":8}"),true);return s;}
    @Test void keyEdgesAndMouseDebounceSendOnlyIdentityAndSequence(){
        var s=session();var sent=new ArrayList<Envelope>();var now=new AtomicLong();var input=new SlotInput(s,sent::add,now::get);
        assertTrue(input.key(32,PacketType.SPACE_ACTION));assertFalse(input.key(32,PacketType.SPACE_ACTION));now.set(1_000_000_000);assertFalse(input.key(32,PacketType.SPACE_ACTION));
        input.release(32);assertTrue(input.key(32,PacketType.SPACE_ACTION));
        assertFalse(input.mouse("LEFT",1,PacketType.STOP_LEFT));assertTrue(input.mouse("LEFT",0,PacketType.STOP_LEFT));assertFalse(input.mouse("LEFT",0,PacketType.STOP_LEFT));
        assertTrue(input.mouse("RIGHT",0,PacketType.STOP_RIGHT));now.addAndGet(99_999_999);assertFalse(input.mouse("LEFT",0,PacketType.STOP_LEFT));now.incrementAndGet();assertTrue(input.mouse("LEFT",0,PacketType.STOP_LEFT));
        assertEquals(5,sent.size());for(int i=0;i<sent.size();i++){var b=sent.get(i).payload();assertEquals(Set.of("sessionId","machineId","clientSequence"),b.keySet());assertEquals(i+8,b.get("clientSequence").getAsLong());}
    }
    @Test void closeIsSentOnceAndTwoSecondFallbackPreservesCanonicalState(){
        var s=session();var state=packet(PacketType.PUBLIC_STATE,"{\"sessionId\":\"00000000-0000-0000-0000-000000000001\",\"machineId\":12,\"expectedNextClientSequence\":8,\"credit\":32,\"heldMedals\":442}");s.receive(state,true);
        var sent=new ArrayList<Envelope>();var time=new AtomicLong();var input=new SlotInput(s,sent::add,time::get);
        assertTrue(input.key(256,PacketType.CLOSE_REQUEST));input.release(256);assertFalse(input.key(256,PacketType.CLOSE_REQUEST));assertFalse(input.send(PacketType.LOAN));
        time.set(1_999_999_999);assertFalse(input.closeExpired());time.incrementAndGet();assertTrue(input.closeExpired());assertEquals(state.payload(),s.publicState());assertNotNull(s.sessionId());assertEquals(1,sent.size());
    }
    @Test void delayedPublicStateCannotReuseSequenceAndNewOpenResetsIt(){
        var s=session();assertEquals(8,s.takeSequence());assertEquals(9,s.takeSequence());
        s.receive(packet(PacketType.PUBLIC_STATE,"{\"sessionId\":\"00000000-0000-0000-0000-000000000001\",\"machineId\":12,\"expectedNextClientSequence\":9}"),true);assertEquals(10,s.takeSequence());
        s.receive(packet(PacketType.OPEN_MACHINE,"{\"sessionId\":\"00000000-0000-0000-0000-000000000002\",\"machineId\":12,\"expectedNextClientSequence\":50}"),true);assertEquals(50,s.takeSequence());
    }
}
