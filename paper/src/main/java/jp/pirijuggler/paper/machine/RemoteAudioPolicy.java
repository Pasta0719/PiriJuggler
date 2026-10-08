package jp.pirijuggler.paper.machine;

import jp.pirijuggler.common.protocol.PacketType;

final class RemoteAudioPolicy {
    static boolean ownerOnly(PacketType type) {
        return type == PacketType.ACTION_REJECTED || type == PacketType.ERROR;
    }

    private RemoteAudioPolicy() {}
}
