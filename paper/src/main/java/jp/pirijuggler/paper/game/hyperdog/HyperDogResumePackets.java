package jp.pirijuggler.paper.game.hyperdog;

import jp.pirijuggler.paper.reel.HyperDogStopSession;
import jp.pirijuggler.common.protocol.Envelope;
import java.util.ArrayList;
import java.util.List;

/** Resume an already-running spin without restarting its animation clock. */
public final class HyperDogResumePackets {
    private HyperDogResumePackets() {}

    public static List<Envelope> replay(HyperDogStopSession session){
        if(session.complete())return List.of();
        var result=new ArrayList<Envelope>();
        for(Envelope packet:session.replayOnReconnect()){
            var payload=packet.payload().deepCopy();
            payload.addProperty("resumeSnapshot",true);
            result.add(Envelope.current(packet.packetType(),payload));
        }
        return List.copyOf(result);
    }
}
