package jp.pirijuggler.paper.game.hyperdog;

import jp.pirijuggler.paper.machine.HyperDogOwnerView;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineType;
import jp.pirijuggler.paper.reel.HyperDogStopSession;
import jp.pirijuggler.paper.session.Session;
import jp.pirijuggler.common.reel.HyperDogReelControl;
import jp.pirijuggler.common.protocol.PacketType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HyperDogPresentationRegressionTest {
    @Test void spinBeginsAtLastCompleteStopsNotAtFixedReelIndices(){
        assertArrayEquals(new double[]{0,7,14},HyperDogReelStartPhase.fromSession(null));
        var saved=new HashMap<String,Object>();
        saved.put("stopped_mask",7);
        saved.put("display_left_stop",5);
        saved.put("display_center_stop",11);
        saved.put("display_right_stop",19);
        assertArrayEquals(new double[]{5,11,19},HyperDogReelStartPhase.fromSession(new Session(saved)));
        saved.put("stopped_mask",3);
        assertArrayEquals(new double[]{0,7,14},HyperDogReelStartPhase.fromSession(new Session(saved)));
    }

    @Test void ownerStillReceivesHyperDogExternalView(){
        var ownDog=new Machine(1,null,MachineType.HYPER_DOG,1,true,false,false,0,0,0,0,0);
        var other=new Machine(2,null,MachineType.JUGGLER,1,true,false,false,0,0,0,0,0);
        assertFalse(HyperDogOwnerView.shouldExcludeOwner(true,ownDog));
        assertTrue(HyperDogOwnerView.shouldExcludeOwner(true,other));
        assertFalse(HyperDogOwnerView.shouldExcludeOwner(false,ownDog));
    }

    @Test void reopeningRunningSpinIsTaggedAsSnapshot(){
        var stop=new HyperDogStopSession(UUID.randomUUID(),1,UUID.randomUUID(),
                HyperDogReelControl.Role.MISS,System.currentTimeMillis(),new double[]{5,11,19});
        var packets=HyperDogResumePackets.replay(stop);
        assertTrue(packets.stream().anyMatch(e->e.packetType()==PacketType.SPIN_START));
        assertTrue(packets.stream().allMatch(e->e.payload().has("resumeSnapshot")
                && e.payload().get("resumeSnapshot").getAsBoolean()));
    }
}
