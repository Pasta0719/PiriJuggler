package jp.pirijuggler.runtime.client;
import jp.pirijuggler.fabric.ui.SlotUi;
public final class HudAudit {
    public static int leaks,worldCalls;
    public static void rendered(){if(SlotUi.hidesHud())leaks++;else worldCalls++;}
    private HudAudit(){}
}
