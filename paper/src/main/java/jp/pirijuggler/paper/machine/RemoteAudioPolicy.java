package jp.pirijuggler.paper.machine;

import jp.pirijuggler.common.protocol.PacketType;

final class RemoteAudioPolicy {
    static boolean ownerOnly(PacketType type) {
        return type == PacketType.ACTION_REJECTED || type == PacketType.ERROR;
    }

    static String bigEndSound(boolean firstGodBig) {
        return firstGodBig ? "god_bonus_end" : "bonus_end";
    }

    private RemoteAudioPolicy() {}
}
