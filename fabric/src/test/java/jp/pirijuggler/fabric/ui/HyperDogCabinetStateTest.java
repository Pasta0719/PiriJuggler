package jp.pirijuggler.fabric.ui;

import com.google.gson.*;
import jp.pirijuggler.common.protocol.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogCabinetStateTest {
    private static final UUID SESSION=UUID.fromString("00000000-0000-0000-0000-000000000987");
    private static Envelope packet(PacketType type,JsonObject j){
        return Envelope.current(type,j);
    }
    private static JsonObject identity(){
        JsonObject j=new JsonObject();j.addProperty("sessionId",SESSION.toString());j.addProperty("machineId",987);
        return j;
    }
    private static HyperDogCabinetState state(AtomicLong ms){
        HyperDogCabinetState s=new HyperDogCabinetState(ms::get);
        JsonObject open=identity();open.addProperty("machineType","HYPER_DOG");
        s.receive(packet(PacketType.OPEN_MACHINE,open));return s;
    }
    private static void feed(HyperDogCabinetState s,String stage,long award,String color,int count,boolean ticket){
        JsonObject j=identity();
        j.addProperty("gameState","AT_READY");j.addProperty("atStage",stage);
        j.addProperty("atRemaining",15000000000000000L);
        j.addProperty("credit",Long.MAX_VALUE);
        j.addProperty("heldMedals",Long.MAX_VALUE-1);
        j.addProperty("bet",3);j.addProperty("pay",30);
        j.addProperty("lampOn",false);j.addProperty("hasCzTicket",ticket);
        j.addProperty("newAward",award);j.addProperty("rightLampColor",color);
        j.addProperty("rightLampCount",count);
        s.receive(packet(PacketType.PUBLIC_STATE,j));
    }
    @Test void illuminatedAtLampsKeepTheApprovedOuterFrameAndDarkMargins(){
        for(int i=0;i<2;i++){
            var layers=HyperDogLampGeometry.announcement(i);
            assertEquals(HyperDogCabinetState.announcementRect(i),layers.frame());
            assertEquals(76,layers.frame().w());
            assertEquals(64,layers.frame().h());
            assertEquals(50,layers.emitter().w());
            assertEquals(38,layers.emitter().h());
            assertTrue(layers.emitter().x()-layers.frame().x()>=13);
            assertTrue(layers.emitter().y()-layers.frame().y()>=13);
            assertInset(layers);
        }
        for(int i=0;i<3;i++){
            var right=HyperDogLampGeometry.right(i);
            assertEquals(HyperDogCabinetState.rightLampRect(i),right.frame());
            assertEquals(14,right.emitter().w());
            assertEquals(14,right.emitter().h());
            assertInset(right);
            var left=HyperDogLampGeometry.left(i);
            assertEquals(HyperDogCabinetState.leftStatusRect(i),left.frame());
            assertEquals(132,left.emitter().w());
            assertEquals(22,left.emitter().h());
            assertInset(left);
        }
    }

    private static void assertInset(HyperDogLampGeometry.Layers layers){
        var outer=layers.frame();
        for(var r:List.of(layers.gasket(),layers.lens(),layers.emitter())){
            assertTrue(r.x()>outer.x() && r.y()>outer.y());
            assertTrue(r.x()+r.w()<outer.x()+outer.w());
            assertTrue(r.y()+r.h()<outer.y()+outer.h());
        }
        assertTrue(layers.emitter().x()>layers.lens().x());
        assertTrue(layers.emitter().y()>layers.lens().y());
        assertTrue(layers.emitter().x()+layers.emitter().w()<layers.lens().x()+layers.lens().w());
        assertTrue(layers.emitter().y()+layers.emitter().h()<layers.lens().y()+layers.lens().h());
    }

    @Test void canonicalRectanglesMatchLegacyInputForWideTallAndFourByThree(){
        assertEquals(SlotLayout.REELS,new SlotLayout.Rect(670,300,900,390));
        assertEquals(SlotLayout.STATUS,new SlotLayout.Rect(670,710,900,95));
        var controls=SlotLayout.CONTROLS;
        assertEquals(List.of("BET","LEVER","LEFT","CENTER","RIGHT","LOAN","INSERT","CASH OUT"),
                controls.stream().map(SlotLayout.Control::name).toList());
        for(int[] size:new int[][]{{960,540},{1280,720},{1024,768},{1280,1024},{3440,1440},{800,800}}){
            var vp=SlotLayout.Viewport.fit(size[0],size[1]);
            for(var control:controls){
                var r=control.rect();
                double px=vp.x()+vp.scale()*(r.x()+r.w()/2.),
                       py=vp.y()+vp.scale()*(r.y()+r.h()/2.);
                assertTrue(r.contains(vp.logicalX(px),vp.logicalY(py)),
                        Arrays.toString(size)+" "+control.name());
                long match=controls.stream().filter(c->c.rect().contains(vp.logicalX(px),vp.logicalY(py))).count();
                assertEquals(1,match,control.name()+" hitboxes must not overlap");
            }
            assertEquals(670,vp.logicalX(vp.x()+670*vp.scale()),0.000001);
            assertEquals(300,vp.logicalY(vp.y()+300*vp.scale()),0.000001);
            assertTrue(vp.x()>=0&&vp.y()>=0);
        }
        for(int i=0;i<2;i++)assertEquals(new SlotLayout.Rect(i==0?574:1520,230,76,64),
                HyperDogCabinetState.announcementRect(i));
        for(int i=0;i<3;i++){
            assertEquals(new SlotLayout.Rect(418,372+61*i,146,36),
                    HyperDogCabinetState.leftStatusRect(i));
            assertEquals(new SlotLayout.Rect(1583,370+69*i,34,34),
                    HyperDogCabinetState.rightLampRect(i));
        }
        assertEquals(new SlotLayout.Rect(356,575,272,132),
                HyperDogCabinetState.lcdRect());
    }
    @Test void negativeAndNoAwardNeverShowFalseLampGuarantee(){
        var time=new AtomicLong();
        var view=state(time);
        feed(view,"UPPER_AT",0,"RAINBOW",3,false);
        assertEquals(0,view.rightCount());
        assertNull(view.color());
        for(long award:new long[]{1,9,10,20,30,50,100,499,500,1999,2000,2001}){
            feed(view,"UPPER_AT",award,"RAINBOW",3,false);
            assertEquals(award==2000?3:0,view.rightCount(),"rainbow "+award);
            feed(view,"UPPER_AT",award,"RED",3,false);
            assertEquals(award>=500?3:0,view.rightCount(),"red "+award);
        }
        feed(view,"UPPER_AT",20,"BLUE",8,false);
        assertEquals(3,view.rightCount());
        feed(view,"UPPER_AT",10,"WHITE",1,false);
        assertEquals(1,view.rightCount());
        assertEquals("WHITE",view.color());
        assertEquals(Long.toString(15000000000000000L),view.displayText());
        assertEquals(Long.toString(Long.MAX_VALUE),view.value("credit"));
        assertEquals(Long.toString(Long.MAX_VALUE-1),view.value("heldMedals"));
    }
    @Test void phaseLampsAndTicketsBlinkStrictlyWithoutMisclassifyingCz(){
        var time=new AtomicLong(0L);var view=state(time);
        feed(view,"LOWER_AT",0,"WHITE",0,true);
        assertTrue(view.active(HyperDogCabinetState.Lamp.LOWER));
        assertTrue(view.active(HyperDogCabinetState.Lamp.CZ));
        time.set(451);assertFalse(view.active(HyperDogCabinetState.Lamp.CZ));
        time.set(1999);assertFalse(view.active(HyperDogCabinetState.Lamp.CZ));
        time.set(2001);assertTrue(view.active(HyperDogCabinetState.Lamp.CZ));
        feed(view,"UPPER_AT",0,"WHITE",0,true);
        assertFalse(view.active(HyperDogCabinetState.Lamp.LOWER));
        assertTrue(view.active(HyperDogCabinetState.Lamp.UPPER));
        assertFalse(view.active(HyperDogCabinetState.Lamp.CZ));
        feed(view,"UPPER_CZ",0,"WHITE",0,true);
        assertFalse(view.active(HyperDogCabinetState.Lamp.UPPER));
        assertTrue(view.active(HyperDogCabinetState.Lamp.CZ));
        JsonObject comeback=identity();comeback.addProperty("comebackDisplay",true);
        view.receive(packet(PacketType.PUBLIC_STATE,comeback));
        time.set(0);assertTrue(view.active(HyperDogCabinetState.Lamp.LOWER));
        time.set(250);assertFalse(view.active(HyperDogCabinetState.Lamp.LOWER));
        time.set(450);assertTrue(view.active(HyperDogCabinetState.Lamp.LOWER));
        time.set(650);assertFalse(view.active(HyperDogCabinetState.Lamp.LOWER));
        time.set(2000);assertTrue(view.active(HyperDogCabinetState.Lamp.LOWER));
    }
    @Test void phase06ServerStagesAndThirdStopFlashAreIdempotentAndGuaranteed(){
        var time=new AtomicLong(1000L);
        var view=state(time);
        JsonObject lever=identity();
        lever.addProperty("atStage","UPPER_AT");
        lever.addProperty("rightLampActive",true);
        lever.addProperty("awardRevealed",false);
        lever.addProperty("rightLampColor","WHITE");
        lever.addProperty("rightLampCount",1);
        lever.addProperty("presentationId",77);
        view.receive(packet(PacketType.PUBLIC_STATE,lever));
        assertEquals(1,view.rightCount());
        assertEquals("WHITE",view.color());
        assertNull(view.flashColor());
        JsonObject stop=lever.deepCopy();
        stop.addProperty("rightLampCount",3);
        stop.addProperty("rightLampColor","YELLOW");
        view.receive(packet(PacketType.PUBLIC_STATE,stop));
        assertEquals(3,view.rightCount());
        assertEquals("YELLOW",view.color());
        JsonObject commit=stop.deepCopy();
        commit.addProperty("awardRevealed",true);
        commit.addProperty("newAward",2000);
        commit.addProperty("flashEventId",77);
        commit.addProperty("flashColor","RAINBOW");
        commit.addProperty("flashAward",2000);
        commit.addProperty("flashStartedEpochMs",1000);
        view.receive(packet(PacketType.PUBLIC_STATE,commit));
        assertEquals("RAINBOW",view.flashColor());
        assertEquals(35,view.flashAlphaPercent());
        time.set(1080);
        assertEquals(0,view.flashAlphaPercent());
        time.set(1150);
        assertEquals(35,view.flashAlphaPercent());
        time.set(1600);
        assertNull(view.flashColor());
        view.receive(packet(PacketType.PUBLIC_STATE,commit));
        assertNull(view.flashColor(),"duplicate packets cannot trigger a new flash");
        JsonObject invalid=commit.deepCopy();
        invalid.addProperty("flashEventId",78);
        invalid.addProperty("flashAward",1999);
        invalid.addProperty("flashStartedEpochMs",1600);
        view.receive(packet(PacketType.PUBLIC_STATE,invalid));
        assertNull(view.flashColor(),"rainbow is only for a true single 2000 award");
        JsonObject off=identity();
        off.addProperty("rightLampActive",false);
        off.addProperty("rightLampColor","RED");
        off.addProperty("rightLampCount",3);
        view.receive(packet(PacketType.PUBLIC_STATE,off));
        assertEquals(0,view.rightCount());
    }
    @Test void serverIdentifiedFifoDisplaysTotalBeforePlusThenRestoresRemaining(){
        var time=new AtomicLong(100L);var view=state(time);
        var state=identity();state.addProperty("atStage","UPPER_AT");
        state.addProperty("atRemaining",612);state.addProperty("newAward",0);
        JsonArray events=new JsonArray();
        for(long[] info:new long[][]{{41,900},{42,70}}){
            JsonObject e=new JsonObject();
            e.addProperty("id",info[0]);e.addProperty("kind",info[0]==41?"TOTAL":"ADD");
            e.addProperty("amount",info[1]);events.add(e);
        }
        state.add("displayEvents",events);
        view.receive(packet(PacketType.PUBLIC_STATE,state));
        assertTrue(view.displayLocked());assertEquals("TOTAL 900",view.displayText());
        view.receive(packet(PacketType.PUBLIC_STATE,state));
        assertEquals(2,view.displayQueued(),"replayed network packets may not duplicate queues");
        time.set(1499);assertEquals("TOTAL 900",view.displayText());
        time.set(1500);assertEquals("+70",view.displayText());
        assertTrue(view.displayLocked());
        time.set(2699);assertEquals("+70",view.displayText());
        time.set(2700);assertEquals("612",view.displayText());
        assertFalse(view.displayLocked());
        view.receive(packet(PacketType.PUBLIC_STATE,state));
        assertEquals(0,view.displayQueued());
        // A brand-new screen/session sees the unresolved server events once after re-seat.
        JsonObject open=identity();open.addProperty("machineType","HYPER_DOG");
        view.receive(packet(PacketType.OPEN_MACHINE,open));
        view.receive(packet(PacketType.PUBLIC_STATE,state));
        assertEquals("TOTAL 900",view.displayText());
        assertEquals(2,view.displayQueued());
    }
    @Test void foreignMessagesNeverMutateCabinetAndErrorsArePrivateToSession(){
        var time=new AtomicLong();
        var view=state(time);
        feed(view,"LOWER_AT",30,"GREEN",3,false);
        var foreign=identity();foreign.addProperty("machineId",988);
        foreign.addProperty("credit",0);foreign.addProperty("atStage","UPPER_AT");
        view.receive(packet(PacketType.PUBLIC_STATE,foreign));
        assertEquals("LOWER_AT",view.stage());
        assertTrue(view.active(HyperDogCabinetState.Lamp.LOWER));
        var lamp=identity();lamp.addProperty("atCount",20);
        view.receive(packet(PacketType.DATA_LAMP,lamp));
        assertEquals(20,view.dataLamp().get("atCount").getAsInt());
        view.receive(packet(PacketType.SESSION_END,foreign));
        assertEquals(987,view.machineId());
        var error=identity();error.addProperty("errorCode","SEQUENCE_OLD");
        view.receive(packet(PacketType.ACTION_REJECTED,error));
        assertEquals("SEQUENCE_OLD",view.error());
        view.markCloseRequested();assertTrue(view.closeRequested());
        view.receive(packet(PacketType.SESSION_END,identity()));
        assertNull(view.sessionId());assertNull(view.dataLamp());assertFalse(view.closeRequested());
    }
    @Test void largeMedalStringsFitInsideTinyLcd(){
        for(int width:new int[]{80,120,260,272}){
            float scale=HyperDogCabinetScreen.fitScale(
                    "TOTAL 9223372036854775807".length()*8,3.2f,width);
            assertTrue(scale*("TOTAL 9223372036854775807".length()*8)<=width+.001f);
            assertTrue(scale>0);
        }
    }
}
