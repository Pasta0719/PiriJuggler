package jp.pirijuggler.fabric.ui;

import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.fabric.network.ClientSession;
import java.util.*;
import java.util.function.*;

/** Input changes only the outbound sequence. Canonical assets remain server-owned. */
public final class SlotInput {
    private final ClientSession session;private final Consumer<Envelope> sender;private final LongSupplier time;
    private final Set<Integer> held=new HashSet<>();private final Map<String,Long> clicked=new HashMap<>();
    private Long closeAt;
    public SlotInput(ClientSession session,Consumer<Envelope> sender,LongSupplier nanos){this.session=session;this.sender=sender;time=nanos;}
    public boolean key(int key,PacketType action){if(!held.add(key))return false;return send(action);}
    public void release(int key){held.remove(key);}
    public void releaseAll(){held.clear();}
    public boolean mouse(String control,int button,PacketType action){
        if(button!=0)return false;long now=time.getAsLong();Long last=clicked.get(control);
        if(last!=null&&now-last<100_000_000L)return false;
        if(!send(action))return false;clicked.put(control,now);return true;
    }
    public boolean send(PacketType action){
        if(session.sessionId()==null||closeAt!=null)return false;
        if(!Set.of(PacketType.SPACE_ACTION,PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT,PacketType.LOAN,PacketType.INSERT_MEDALS,PacketType.CASH_OUT,PacketType.CLOSE_REQUEST).contains(action))throw new IllegalArgumentException("Not a slot input");
        JsonObject body=new JsonObject();body.addProperty("sessionId",session.sessionId().toString());body.addProperty("machineId",session.machineId());body.addProperty("clientSequence",session.takeSequence());
        sender.accept(Envelope.current(action,body));if(action==PacketType.CLOSE_REQUEST)closeAt=time.getAsLong();return true;
    }
    public boolean closing(){return closeAt!=null;}
    public boolean closeExpired(){return closeAt!=null&&time.getAsLong()-closeAt>=2_000_000_000L;}
}
