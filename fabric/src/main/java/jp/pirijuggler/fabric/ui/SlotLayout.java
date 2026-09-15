package jp.pirijuggler.fabric.ui;

import jp.pirijuggler.common.protocol.PacketType;
import java.util.List;

public final class SlotLayout {
    public record Rect(int x,int y,int w,int h) { public boolean contains(double px,double py){return px>=x&&px<x+w&&py>=y&&py<y+h;} }
    public record Control(String name,Rect rect,PacketType action) {}
    public record Viewport(double scale,double x,double y) {
        public static Viewport fit(double width,double height){double s=Math.min(width/1920,height/1080);return new Viewport(s,(width-1920*s)/2,(height-1080*s)/2);}
        public double logicalX(double px){return (px-x)/scale;} public double logicalY(double py){return (py-y)/scale;}
        public Rect clip(Rect r){int left=(int)Math.floor(x+r.x*scale),top=(int)Math.floor(y+r.y*scale);return new Rect(left,top,(int)Math.ceil(x+(r.x+r.w)*scale)-left,(int)Math.ceil(y+(r.y+r.h)*scale)-top);}
    }
    public static final Rect DATA=new Rect(210,12,1500,260), CABINET=new Rect(290,205,1340,835), LAMP=new Rect(350,390,300,170), REELS=new Rect(670,300,900,390), STATUS=new Rect(670,710,900,95);
    public static final List<Control> CONTROLS=List.of(
        new Control("BET",new Rect(420,860,150,100),PacketType.SPACE_ACTION),
        new Control("LEVER",new Rect(250,780,130,260),PacketType.SPACE_ACTION),
        new Control("LEFT",new Rect(720,865,180,110),PacketType.STOP_LEFT),
        new Control("CENTER",new Rect(990,865,180,110),PacketType.STOP_CENTER),
        new Control("RIGHT",new Rect(1260,865,180,110),PacketType.STOP_RIGHT),
        new Control("CASH OUT",new Rect(1490,870,100,90),PacketType.CASH_OUT),
        new Control("LOAN",new Rect(1490,740,100,55),PacketType.LOAN),
        new Control("INSERT",new Rect(1490,805,100,55),PacketType.INSERT_MEDALS));
    private SlotLayout(){}
}
