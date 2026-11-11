package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.fabric.PiriJugglerClient;

/** Suppress only a seated HYPER_DOG owner's duplicate positional BET. */
public final class HyperDogOwnerAudioFilter {
    private HyperDogOwnerAudioFilter() {}

    public static boolean suppressOwnBet(boolean dogOpen,int ownerMachineId,int audibleMachineId,String logical){
        return dogOpen && ownerMachineId==audibleMachineId && "bet".equals(logical);
    }

    public static boolean allowRemoteOneShot(int machineId,String logical){
        if(!HallAudio.publicHallSoundAllowed(logical))return false;
        var own=PiriJugglerClient.hyperDogReels();
        return !suppressOwnBet(own.opened(),own.machineId(),machineId,logical);
    }
}
