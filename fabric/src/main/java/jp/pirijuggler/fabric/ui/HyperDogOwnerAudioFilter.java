package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.fabric.PiriJugglerClient;

/** Only the owner channel plays a seated HYPER_DOG's audio; spectators still hear hall audio. */
public final class HyperDogOwnerAudioFilter {
    private HyperDogOwnerAudioFilter() {}
    public static boolean suppressOwnBet(boolean dogOpen,int ownerMachineId,int audibleMachineId,String logical){
        return suppressOwnAudio(dogOpen,ownerMachineId,audibleMachineId)&&"bet".equals(logical);
    }
    public static boolean suppressOwnAudio(boolean dogOpen,int ownerMachineId,int audibleMachineId){
        return dogOpen&&ownerMachineId>0&&ownerMachineId==audibleMachineId;
    }
    public static boolean allowRemoteOneShot(int machineId,String logical){
        if(!HallAudio.publicHallSoundAllowed(logical))return false;
        var owner=PiriJugglerClient.hyperDogReels();
        return !suppressOwnAudio(owner.opened(),owner.machineId(),machineId);
    }
}
