package jp.pirijuggler.fabric.ui;
import jp.pirijuggler.common.protocol.PacketType;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
public final class SlotKeys {
    private static final KeyBinding[] STOP=new KeyBinding[3];
    public static void register(){int[] defaults={GLFW.GLFW_KEY_LEFT,GLFW.GLFW_KEY_DOWN,GLFW.GLFW_KEY_RIGHT};String[] names={"left","center","right"};for(int i=0;i<3;i++)STOP[i]=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.piri.stop_"+names[i],defaults[i],"key.categories.piri"));}
    public static PacketType action(int key,int scan){
        for(int i=0;i<3;i++)if(STOP[i].matchesKey(key,scan))return new PacketType[]{PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT}[i];
        return switch(key){case GLFW.GLFW_KEY_SPACE->PacketType.SPACE_ACTION;case GLFW.GLFW_KEY_L->PacketType.LOAN;case GLFW.GLFW_KEY_I->PacketType.INSERT_MEDALS;case GLFW.GLFW_KEY_R->PacketType.CASH_OUT;case GLFW.GLFW_KEY_ESCAPE->PacketType.CLOSE_REQUEST;default->null;};
    }
    private SlotKeys(){}
}
