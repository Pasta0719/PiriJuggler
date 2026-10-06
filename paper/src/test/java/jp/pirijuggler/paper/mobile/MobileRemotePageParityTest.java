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
        assertTrue(page.contains("#cabinet{position:absolute;left:290px;top:205px;width:1340px;height:835px"));
        assertTrue(page.contains("#reelBacking{position:absolute;left:670px;top:300px;width:900px;height:390px"));
        assertTrue(page.contains("#reel0{left:670px}#reel1{left:985px}#reel2{left:1300px}"));
        assertTrue(page.contains("#lamp{position:absolute;left:350px;top:390px;width:300px;height:170px"));
        assertTrue(page.contains("#statusPanel{position:absolute;left:670px;top:710px;width:900px;height:95px"));
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
        assertFalse(page.contains("playSound(\"bonus_end\")"));
    }

    @Test
    void historyIsNotHardLimitedToTenRows() throws Exception {
        String page=page();
        assertFalse(page.contains(".slice(0,10)"));
        assertTrue(page.contains("height:220px"));
        assertTrue(page.contains("overflow-y:auto"));
    }
}
