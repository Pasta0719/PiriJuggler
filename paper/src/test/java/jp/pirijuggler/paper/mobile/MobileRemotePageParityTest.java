package jp.pirijuggler.paper.mobile;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class MobileRemotePageParityTest {
    private static String page() throws Exception {
        Path source=Path.of("src/main/java/jp/pirijuggler/paper/mobile/MobileRemoteGateway.java");
        if(!Files.exists(source)) source=Path.of("paper/src/main/java/jp/pirijuggler/paper/mobile/MobileRemoteGateway.java");
        String text=Files.readString(source, StandardCharsets.UTF_8);
        String marker="private static final String PAGE = \"\"\"";
        int start=text.indexOf(marker);
        if(start<0) throw new IllegalStateException("PAGE text block not found");
        start+=marker.length();
        int end=text.indexOf("\"\"\";", start);
        if(end<0) throw new IllegalStateException("PAGE text block terminator not found");
        return text.substring(start,end);
    }

    @Test
    void fabricCanonicalLayoutIsTheOnlyMachineLayout() throws Exception {
        String page=page();
        assertTrue(page.contains("#stage{position:absolute;width:1920px;height:1080px"));
        assertTrue(page.contains("#cabinet{position:absolute;left:310px;top:292px;width:1320px;height:748px"));
        assertTrue(page.contains("#reelBacking{position:absolute;left:670px;top:315px;width:900px;height:390px"));
        assertTrue(page.contains("#reel0{left:670px}#reel1{left:985px}#reel2{left:1300px}"));
        assertTrue(page.contains("#lamp{position:absolute;left:350px;top:405px;width:300px;height:170px"));
        assertTrue(page.contains("#statusPanel{position:absolute;left:670px;top:720px;width:900px;height:95px"));
        assertTrue(page.contains("#betBtn{left:440px;top:860px;width:150px;height:100px}"));
        assertTrue(page.contains("#leverBtn{left:300px;top:780px;width:130px;height:260px"));
        assertTrue(page.contains("#leftBtn{left:720px;top:865px}#centerBtn{left:990px;top:865px}#rightBtn{left:1260px;top:865px}"));
        assertTrue(page.contains(".sideBtn{left:1664px;width:220px;height:55px}"));
        assertFalse(page.contains("width:1600px"));
        assertFalse(page.contains("orientation:portrait"));
    }

    @Test
    void fabricStatusAndGodIndicatorsUseCanonicalAnchors() throws Exception {
        String page=page();
        assertTrue(page.contains("#replayText{position:absolute;left:1050px;top:816px"));
        assertTrue(page.contains("#countText{position:absolute;left:1090px;top:816px"));
        assertTrue(page.contains("#stockLamp{position:absolute;left:1390px;top:835px;width:150px;height:58px"));
        assertTrue(page.contains("#stockInner{width:138px;height:46px"));
        assertFalse(page.contains("stateText"));
        assertTrue(page.contains("sym===\"seven\"||sym===\"grape\"||sym===\"replay\""));
        assertTrue(page.contains("if(sym===\"bar\")return[230,150]"));
    }

    @Test
    void fabricPaletteAndMachineGeometryRemainCanonical() throws Exception {
        String page=page();
        assertTrue(page.contains("#stage{position:absolute;width:1920px;height:1080px"));
        assertTrue(page.contains("background:#000000;color:#F6F1E7"));
        assertTrue(page.contains("background:#3C0A10;border:4px solid #B68A42"));
        assertTrue(page.contains("background:#8A8175"));
        assertTrue(page.contains("background:#F4F1E8"));
        assertTrue(page.contains("#stage.skillstop #cabinet{background:#111015!important"));
        assertTrue(page.contains("background:linear-gradient(to bottom,#E9D07A,#64410D)"));
        assertTrue(page.contains("#stockLamp.on{background:#58e36a}"));
        assertTrue(page.contains("#stockLamp{position:absolute;left:1390px;top:835px;width:150px;height:58px"));
        assertTrue(page.contains("#replayText{position:absolute;left:1050px;top:816px"));
        assertTrue(page.contains("#countText{position:absolute;left:1090px;top:816px"));
        assertTrue(page.contains("#skillChallenge{position:absolute;left:410px;top:580px;width:180px;height:140px"));
        assertTrue(page.contains("#leftBtn{left:720px;top:865px}#centerBtn{left:990px;top:865px}#rightBtn{left:1260px;top:865px}"));
    }

    @Test
    void mobilePanelsStayCleanlyLayeredAndMoneyFitsInsideRightFrame() throws Exception {
        String page=page();
        assertTrue(page.contains(".panel{position:absolute;background:#090B0E;border:4px solid #B68A42;border-radius:18px;z-index:2"));
        assertTrue(page.contains("#cabinet{position:absolute;left:310px;top:292px;width:1320px;height:748px"));
        assertTrue(page.contains("overflow:hidden;z-index:1}"));
        assertTrue(page.contains("#gameGraph{position:absolute;left:55px;top:88px;width:825px;height:146px;background:#090b0e!important;z-index:3"));
        assertTrue(page.contains("#mobileMoney{position:absolute;left:1668px;top:670px;width:210px;height:70px"));
        assertTrue(page.contains("#chain{position:absolute;left:1668px;top:575px;width:210px;height:80px"));
        int topPanelBottom=12+270,cabinetTop=292;
        int leftPanelRight=18+275,cabinetLeft=310;
        int cabinetRight=310+1320,rightPanelLeft=1645;
        int rightPanelBottom=325+430,chainBottom=575+80,moneyTop=670,moneyBottom=670+70;
        assertTrue(cabinetTop>topPanelBottom);
        assertTrue(cabinetLeft>leftPanelRight);
        assertTrue(cabinetRight<rightPanelLeft);
        assertTrue(chainBottom<moneyTop);
        assertTrue(moneyBottom<rightPanelBottom);
    }

    @Test
    void fabricStateRecoverySemanticsArePresent() throws Exception {
        String page=page();
        assertTrue(page.contains("/api/resume"));
        assertTrue(page.contains("function requestResume(reason)"));
        assertTrue(page.contains("motion.presses"));
        assertTrue(page.contains("reconcileStoppedFromSnapshot"));
        assertTrue(page.contains("String(j.spinId)!==String(motion.spinId)"));
        assertTrue(page.contains("document.addEventListener(\"visibilitychange\""));
        assertTrue(page.contains("window.addEventListener(\"online\""));
        assertTrue(page.contains("window.addEventListener(\"pageshow\""));
        assertTrue(page.contains("nextGameAt=Math.max(nextGameAt,motion.at+2000)"));
    }

    @Test
    void godFirstBigUsesDedicatedStartBgmAndEndAssets() throws Exception {
        String page=page();
        assertTrue(page.contains("juggler_god_god_bonus_start"));
        assertTrue(page.contains("juggler_god_god_big_bgm"));
        assertTrue(page.contains("juggler_god_god_bonus_end"));
        assertTrue(page.contains("godBigAudioPending"));
        assertTrue(page.contains("godBigAudioActive"));
        assertTrue(page.contains("!!p.godFirstBigAudio||godBigAudioActive"));
        assertFalse(page.contains("playSound(\"bonus_end\")"));
    }

    @Test
    void mobileAudioLifecycleMatchesFabricStopAllSemantics() throws Exception {
        String page=page();
        assertTrue(page.contains("const audioOneShots=new Set(),audioTimers=new Set()"));
        assertTrue(page.contains("function trackOneShot(a)"));
        assertTrue(page.contains("audioOneShots.add(a)"));
        assertTrue(page.contains("for(const timer of audioTimers)clearTimeout(timer)"));
        assertTrue(page.contains("for(const a of audioOneShots){try{a.pause();a.currentTime=0}catch(e){}}"));
        assertTrue(page.contains("audioOneShots.clear()"));
        assertTrue(page.contains("stopLoop();"));
        assertTrue(page.contains("if(freeze){"));
        assertTrue(page.contains("stopAllAudio();"));
        assertTrue(page.contains("playLater(machineSound(\"notice\"),\"notice\",n*100)"));
    }

    @Test
    void historyIsNotHardLimitedToTenRows() throws Exception {
        String page=page();
        assertFalse(page.contains(".slice(0,10)"));
        assertTrue(page.contains("height:220px"));
        assertTrue(page.contains("overflow-y:auto"));
    }
}
