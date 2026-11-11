package jp.pirijuggler.fabric.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Shared seated/world CHANCE emblem geometry and regression guardrails. */
class HyperDogLayoutRegressionTest {
    private static boolean overlaps(SlotLayout.Rect a,SlotLayout.Rect b){
        return a.x()<b.x()+b.w()&&b.x()<a.x()+a.w()
                &&a.y()<b.y()+b.h()&&b.y()<a.y()+a.h();
    }
    private static void clear(String a,SlotLayout.Rect r,String b,SlotLayout.Rect s){
        assertFalse(overlaps(r,s),a+" overlaps "+b+": "+r+" vs "+s);
    }
    private static void inside(String name,SlotLayout.Rect r,SlotLayout.Rect outer){
        assertTrue(r.x()>=outer.x()&&r.y()>=outer.y()
                &&r.x()+r.w()<=outer.x()+outer.w()
                &&r.y()+r.h()<=outer.y()+outer.h(),name+" outside "+outer);
    }

    @Test void reelWindowUsesTheExistingMachineBackgroundColor(){
        assertEquals(UiConstants.color("REEL_BG"),HyperDogCabinetScreen.reelBackgroundColor());
    }

    @Test void oneChanceEmblemAlignedWithLcdAndClearOfAllOtherPanels(){
        var chance=HyperDogCabinetState.announcementRect(0);
        var body=HyperDogCabinetState.cabinetRect();
        var lcd=HyperDogCabinetState.lcdRect();
        assertEquals(266,chance.w());
        assertEquals(60,chance.h());
        assertEquals(lcd.x()+lcd.w()/2,chance.x()+chance.w()/2);
        assertThrows(IllegalArgumentException.class,
                ()->HyperDogCabinetState.announcementRect(1));
        inside("CHANCE",chance,body);
        clear("CHANCE",chance,"top data",SlotLayout.DATA);
        clear("CHANCE",chance,"left data",SlotLayout.DATA_LEFT);
        clear("CHANCE",chance,"reels",SlotLayout.REELS);
        clear("CHANCE",chance,"right data",HyperDogCabinetState.rightDataRect());
        clear("CHANCE",chance,"LCD",lcd);
        assertTrue(chance.y()-(SlotLayout.DATA.y()+SlotLayout.DATA.h())>=16);
        assertTrue(HyperDogCabinetState.leftStatusRect(0).y()-
                   (chance.y()+chance.h())>=12);
        assertTrue(SlotLayout.REELS.x()-(chance.x()+chance.w())>=20);
        assertEquals(1340,SlotLayout.CABINET.w(),"other cabinets must remain unchanged");
    }

    @Test void announcementHasRecessedNestedEmblemLayers(){
        var l=HyperDogLampGeometry.announcement(0);
        inside("gasket",l.gasket(),l.frame());
        inside("lens",l.lens(),l.gasket());
        inside("emitter",l.emitter(),l.lens());
        assertTrue(l.emitter().w()>200,"CHANCE text needs ample horizontal space");
        assertTrue(l.emitter().h()>30,"CHANCE text needs adequate height");
    }

    @Test void allThreeLeftAndRightIndicatorsRemainInTheirOriginalPlaces(){
        var body=HyperDogCabinetState.cabinetRect();
        var chance=HyperDogCabinetState.announcementRect(0);
        for(int i=0;i<3;i++){
            var left=HyperDogLampGeometry.left(i);
            var right=HyperDogLampGeometry.right(i);
            assertEquals(new SlotLayout.Rect(388,372+61*i,146,36),left.frame());
            assertEquals(new SlotLayout.Rect(1583,454+69*i,34,34),right.frame());
            inside("left "+i,left.frame(),body);
            inside("right "+i,right.frame(),body);
            inside("left emitter "+i,left.emitter(),left.lens());
            inside("right emitter "+i,right.emitter(),right.lens());
            clear("CHANCE",chance,"left status "+i,left.frame());
            clear("CHANCE",chance,"right status "+i,right.frame());
            clear("right indicator "+i,right.frame(),"right sidebar",
                    HyperDogCabinetState.rightDataRect());
        }
    }
}
