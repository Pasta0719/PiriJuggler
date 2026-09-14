package jp.pirijuggler.common.protocol;

public final class Protocol {
    public static final String CHANNEL = "piri:main";
    public static final int MAGIC = 0x50495249;
    public static final int VERSION = 1;
    public static final String MOD_VERSION = "1.0.0";
    public static final String SERVER_VERSION = "1.0.0";
    public static final int MAX_PAYLOAD_BYTES = 32767;
    public static final int MAX_ENVELOPE_BYTES = 4 + 2 + 1 + 3 + MAX_PAYLOAD_BYTES;

    private Protocol() { }
}
