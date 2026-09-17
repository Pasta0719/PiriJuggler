package jp.pirijuggler.fabric.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jp.pirijuggler.common.protocol.Envelope;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.protocol.Protocol;
import jp.pirijuggler.fabric.network.ClientSession;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** OP-only machine administration UI. Server remains authoritative for every mutation. */
public final class AdminScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Tokyo"));
    private final ClientSession session;
    private final Consumer<Envelope> sender;
    private boolean closeSent;

    public AdminScreen(ClientSession session, Consumer<Envelope> sender) {
        super(Text.literal("Piri Admin"));
        this.session = session;
        this.sender = sender;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override public void close() {
        sendClose();
        session.clearAdmin();
        super.close();
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, width, height, 0xE0000000);
        JsonObject s = session.adminState();
        if (s == null) {
            center(c, "ADMIN SESSION CLOSED", 30, 0xFFFF6666);
            return;
        }
        int left = width / 2 - 250;
        c.fill(left, 18, left + 500, height - 18, 0xFF111318);
        center(c, "PIRI ADMIN", 30, 0xFFFFD37A);
        center(c, "MACHINE " + s.get("machineId").getAsInt() + (bool(s,"busy") ? "  [BUSY]" : ""), 54, bool(s,"busy") ? 0xFFFF6666 : 0xFFFFFFFF);

        line(c, left + 22, 82, "SETTING", Integer.toString(s.get("setting").getAsInt()));
        line(c, left + 22, 102, "AUTO", bool(s,"autoSetting") ? "ON" : "OFF");
        line(c, left + 22, 122, "ENABLED", bool(s,"enabled") ? "ON" : "OFF");
        line(c, left + 22, 142, "PROFILE", s.get("activeProfile").getAsString());
        line(c, left + 250, 82, "TOTAL", value(s,"totalGames"));
        line(c, left + 250, 102, "BIG / REG", value(s,"bigCount") + " / " + value(s,"regCount"));
        line(c, left + 250, 122, "CURRENT", value(s,"currentGames"));
        line(c, left + 250, 142, "DIFF / MAX", value(s,"todayDifference") + " / " + value(s,"todayMaxDifference"));

        int y = 180;
        c.drawText(textRenderer, Text.literal("SETTING"), left + 22, y, 0xFFFFFFFF, false);
        for (int setting = 1; setting <= 6; setting++) button(c, left + 88 + (setting - 1) * 52, y - 7, 42, 24, Integer.toString(setting), mouseX, mouseY);
        button(c, left + 22, y + 35, 140, 26, "AUTO " + (bool(s,"autoSetting") ? "OFF" : "ON"), mouseX, mouseY);
        button(c, left + 177, y + 35, 140, 26, (bool(s,"enabled") ? "DISABLE" : "ENABLE"), mouseX, mouseY);
        button(c, left + 332, y + 35, 140, 26, "RESET DAILY", mouseX, mouseY);

        c.drawText(textRenderer, Text.literal("SETTING HISTORY (latest 30)"), left + 22, y + 78, 0xFFFFD37A, false);
        JsonArray history = s.has("settingHistory") ? s.getAsJsonArray("settingHistory") : new JsonArray();
        int rowY = y + 98;
        for (int i = 0; i < Math.min(history.size(), Math.max(0, (height - rowY - 35) / 12)); i++) {
            JsonObject h = history.get(i).getAsJsonObject();
            String text = TIME.format(Instant.ofEpochMilli(h.get("time").getAsLong())) + "  "
                    + h.get("old").getAsInt() + " -> " + h.get("new").getAsInt() + "  " + h.get("reason").getAsString();
            c.drawText(textRenderer, Text.literal(text), left + 22, rowY + i * 12, 0xFFD8D8D8, false);
        }
        center(c, "ESC: close", height - 32, 0xFFAAAAAA);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || session.adminState() == null) return false;
        int left = width / 2 - 250, y = 180;
        for (int setting = 1; setting <= 6; setting++) {
            if (inside(mouseX,mouseY,left + 88 + (setting - 1) * 52,y - 7,42,24)) {
                mutate(PacketType.ADMIN_SET_SETTING, setting); return true;
            }
        }
        JsonObject s = session.adminState();
        if (inside(mouseX,mouseY,left+22,y+35,140,26)) { mutate(PacketType.ADMIN_SET_AUTO,!bool(s,"autoSetting")); return true; }
        if (inside(mouseX,mouseY,left+177,y+35,140,26)) { mutate(PacketType.ADMIN_SET_ENABLED,!bool(s,"enabled")); return true; }
        if (inside(mouseX,mouseY,left+332,y+35,140,26)) { mutate(PacketType.ADMIN_RESET_DAILY,true); return true; }
        return false;
    }

    private void mutate(PacketType type, Object value) {
        JsonObject body = base();
        if (value instanceof Boolean b) body.addProperty("value", b);
        else body.addProperty("value", ((Number)value).intValue());
        sender.accept(new Envelope(Protocol.VERSION, type, body));
    }

    private void sendClose() {
        if (closeSent || session.adminSessionId() == null) return;
        closeSent = true;
        sender.accept(new Envelope(Protocol.VERSION, PacketType.ADMIN_CLOSE, base()));
    }

    private JsonObject base() {
        JsonObject body = new JsonObject();
        body.addProperty("adminSessionId", session.adminSessionId().toString());
        body.addProperty("machineId", session.adminMachineId());
        body.addProperty("adminSequence", session.takeAdminSequence());
        return body;
    }

    private void center(DrawContext c,String text,int y,int color){c.drawCenteredTextWithShadow(textRenderer,Text.literal(text),width/2,y,color);}
    private void line(DrawContext c,int x,int y,String label,String value){c.drawText(textRenderer,Text.literal(label+": "+value),x,y,0xFFFFFFFF,false);}
    private void button(DrawContext c,int x,int y,int w,int h,String text,double mx,double my){
        c.fill(x,y,x+w,y+h,inside(mx,my,x,y,w,h)?0xFF9A3038:0xFF681B22);
        c.drawCenteredTextWithShadow(textRenderer,Text.literal(text),x+w/2,y+8,0xFFFFFFFF);
    }
    private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private static boolean bool(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
    private static String value(JsonObject o,String key){return o.has(key)?o.get(key).getAsString():"0";}
}
