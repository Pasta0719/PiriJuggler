package jp.pirijuggler.paper.machine;

/** HYPER_DOG also broadcasts its public cabinet view to the seated owner. */
public final class HyperDogOwnerView {
    private HyperDogOwnerView() {}

    public static boolean shouldExcludeOwner(boolean activeOwner,Machine machine){
        return activeOwner && machine.type()!=MachineType.HYPER_DOG;
    }
}
