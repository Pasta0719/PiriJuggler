package jp.pirijuggler.common.protocol;

import java.util.Arrays;

public enum PacketType {
    HELLO(1), RESERVED_2(2), SPACE_ACTION(3), STOP_LEFT(4), STOP_CENTER(5), STOP_RIGHT(6),
    LOAN(7), INSERT_MEDALS(8), CASH_OUT(9), CLOSE_REQUEST(10), RESERVED_11(11),
    ADMIN_SET_SETTING(12), ADMIN_SET_AUTO(13), ADMIN_SET_ENABLED(14), ADMIN_RESET_DAILY(15),
    HELLO_ACK(101), OPEN_MACHINE(102), PUBLIC_STATE(103), ACTION_ACCEPTED(104),
    ACTION_REJECTED(105), SPIN_START(106), REEL_STOP(107), PAYOUT(108), NOTICE(109),
    TENPAI_SOUND(110), BONUS_START(111), BONUS_END(112), DATA_LAMP(113), CASHOUT_RESULT(114),
    SESSION_SUSPENDED(115), SESSION_END(116), ADMIN_STATE(117), ERROR(118);

    private final int id;

    PacketType(int id) { this.id = id; }
    public int id() { return id; }
    public boolean isClientToServer() { return id < 100; }
    public boolean isReserved() { return this == RESERVED_2 || this == RESERVED_11; }

    public static PacketType fromId(int id) {
        return Arrays.stream(values()).filter(type -> type.id == id).findFirst()
                .orElseThrow(() -> new ProtocolException("Unknown packet ID: " + id));
    }
}
