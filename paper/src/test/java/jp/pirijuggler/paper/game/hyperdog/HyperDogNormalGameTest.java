package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.*;
import jp.pirijuggler.paper.machine.MachineType;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HyperDogNormalGameTest {
    private static HyperDogNormalGame newGame(long seed){return new HyperDogNormalGame(1,seed,100000);}
    private static HyperDogNormalGame.Outcome play(HyperDogNormalGame game,HyperDogNormalGame.Role role){
        if(!game.replayFree())game.bet();
        var output=game.leverForced(role);
        assertEquals(role,output.role());
        assertEquals(game.games()+1,output.serial());
        assertEquals(output,game.finish());
        return output;
    }
    private static void resumeIfHit(HyperDogNormalGame game){
        if(game.waitingForAt())game.resumeAfterAt();
    }

    @Test void v6ModelAndSettingChanceAndRolePayouts() {
        assertEquals("e99ae52bbf431ec76267ce35a0d4de3f13c5e2c55e2d21a2c08011322700b3ac",
                HyperDogNormalGame.parameterSha256());
        var a=HyperDogNormalGame.Mode.values();
        double[] denom={8192,4096,512,256,96,12};
        double[] mult={.1451,.1830,.2423,.2851,.3424,.5346};
        for(int s=1;s<=6;s++){
            var state=new HyperDogNormalGame(s,6789,100000);
            assertEquals(s,state.setting());
            for(int m=0;m<a.length;m++)
                assertEquals(mult[s-1]/denom[m],HyperDogNormalGame.singleHitChance(a[m],s),1e-14);
        }
        assertArrayEquals(new int[]{0,0,3,1,5,5,1,5,15},Arrays.stream(HyperDogNormalGame.Role.values())
                .mapToInt(HyperDogNormalGame::payout).toArray());
        assertEquals(MachineType.HYPER_DOG,MachineType.valueOf("HYPER_DOG"));
    }
    @Test void separateBetLeverNoDoubleChargeAndReplayFreeBoundary() {
        assertThrows(IllegalArgumentException.class,()->new HyperDogNormalGame(0,1,100));
        assertThrows(IllegalArgumentException.class,()->new HyperDogNormalGame(7,1,100));
        var game=newGame(100);
        assertThrows(IllegalStateException.class,game::lever);
        game.bet();
        assertEquals(99997,game.credits());
        assertEquals(3,game.totalBet());
        assertThrows(IllegalStateException.class,game::bet);
        var spin=game.leverForced(HyperDogNormalGame.Role.REPLAY);
        assertEquals(3,spin.bet());
        assertThrows(IllegalStateException.class,game::lever);
        assertThrows(IllegalStateException.class,game::bet);
        assertEquals(spin,game.finish());
        assertTrue(game.replayFree());
        assertFalse(game.canBet());
        long before=game.credits();
        var zero=game.leverForced(HyperDogNormalGame.Role.GRAPE);
        assertEquals(0,zero.bet());
        assertEquals(before,game.credits());
        assertEquals(3,game.finish().payout());
        assertEquals(before+3,game.credits());
        assertFalse(game.replayFree());
        assertEquals(2,game.games());
        assertThrows(IllegalStateException.class,game::lever);
    }
    @Test void snapshotBeforeBetAfterBetDuringSpinAndAtEntryLossless() {
        var g=newGame(9);
        var initial=g.snapshot();
        assertEquals(initial,HyperDogNormalGame.restore(initial).snapshot());
        g.bet();
        assertEquals(g.snapshot(),HyperDogNormalGame.restore(g.snapshot()).snapshot());
        var before=newGame(9);before.bet();
        var forced=g.leverForced(HyperDogNormalGame.Role.STRONG_CHANCE);
        var loaded=HyperDogNormalGame.restore(g.snapshot());
        assertEquals(forced,loaded.pending());
        assertEquals(forced,g.pending());
        assertEquals(g.snapshot(),loaded.snapshot());
        assertEquals(g.finish(),loaded.finish());
        assertEquals(g.snapshot(),loaded.snapshot());
        assertEquals(g.waitingForAt(),loaded.waitingForAt());
        if(g.waitingForAt()){
            assertEquals(g.resumeAfterAt(),loaded.resumeAfterAt());
            assertEquals(g.snapshot(),loaded.snapshot());
        }
        JsonObject invalidSetting=g.snapshot();invalidSetting.addProperty("setting",0);
        assertThrows(IllegalArgumentException.class,()->HyperDogNormalGame.restore(invalidSetting));
        JsonObject invalidCredit=g.snapshot();invalidCredit.addProperty("credits",-1);
        assertThrows(IllegalArgumentException.class,()->HyperDogNormalGame.restore(invalidCredit));
    }
    @Test void overflowNeverPartiallySettlesBetOrThirdStop() {
        // A corrupted/exhausted inbound total must not debit a player's credits.
        var base=newGame(431);
        JsonObject highIn=base.snapshot();
        highIn.addProperty("in",Long.MAX_VALUE-1);
        var betOverflow=HyperDogNormalGame.restore(highIn);
        JsonObject beforeBet=betOverflow.snapshot();
        assertThrows(ArithmeticException.class,betOverflow::bet);
        assertEquals(beforeBet,betOverflow.snapshot());

        // A saturated payout ledger cannot silently credit or advance a game.
        var highOut=newGame(432);
        highOut.bet();highOut.leverForced(HyperDogNormalGame.Role.BAR);
        JsonObject highOutSnapshot=highOut.snapshot();
        highOutSnapshot.addProperty("out",Long.MAX_VALUE-10);
        var payoutOverflow=HyperDogNormalGame.restore(highOutSnapshot);
        JsonObject beforePayout=payoutOverflow.snapshot();
        assertThrows(ArithmeticException.class,payoutOverflow::finish);
        assertEquals(beforePayout,payoutOverflow.snapshot());
        assertNotNull(payoutOverflow.pending());

        // Even when out is safe, a near-max credit balance cannot partially
        // increment the paid-out counter ahead of the overflowing credit.
        var creditOverflow=new HyperDogNormalGame(1,433,Long.MAX_VALUE);
        creditOverflow.bet();creditOverflow.leverForced(HyperDogNormalGame.Role.BAR);
        JsonObject beforeCredit=creditOverflow.snapshot();
        assertThrows(ArithmeticException.class,creditOverflow::finish);
        assertEquals(beforeCredit,creditOverflow.snapshot());
        assertNotNull(creditOverflow.pending());
    }

    @Test void diskSnapshotResumesInFreshJvmWithExactLedgerModeAndRng() throws Exception {
        // Confirm actual process restart, beyond restore(new JsonObject) in one JVM.
        // The economy/session database and whole Paper-process restart are Phase04.
        for(String checkpoint:new String[]{"BET_POSTED","PENDING_STOP","REPLAY_READY","AT_PENDING"}){
            HyperDogNormalGame game=new HyperDogNormalGame(3,20261109L,100000);
            game.bet();
            switch(checkpoint){
                case "BET_POSTED" -> {}
                case "PENDING_STOP" -> game.leverForced(HyperDogNormalGame.Role.WEAK_CHERRY);
                case "REPLAY_READY" -> {
                    game.leverForced(HyperDogNormalGame.Role.REPLAY);
                    game.finish();
                }
                case "AT_PENDING" -> {
                    game.leverForced(HyperDogNormalGame.Role.BAR);
                    game.finish();
                }
                default -> throw new AssertionError(checkpoint);
            }
            Path input=Files.createTempFile("hyperdog03-"+checkpoint,".json");
            Path output=Files.createTempFile("hyperdog03-"+checkpoint+"-after",".json");
            try{
                JsonObject saved=game.snapshot();
                Files.writeString(input,saved.toString());
                var expected=HyperDogNormalGame.restore(saved);
                HyperDogRestartProbe.advance(expected,25);

                // Explicit code locations avoid Gradle's isolated worker classpath.
                var classes=new LinkedHashSet<String>();
                for(Class<?> type:new Class<?>[]{
                        HyperDogRestartProbe.class,HyperDogNormalGame.class,
                        jp.pirijuggler.common.reel.HyperDogReelControl.class,
                        com.google.gson.JsonObject.class}) {
                    classes.add(Path.of(type.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()).toString());
                }
                var model=HyperDogNormalGame.class.getClassLoader().getResource("hyperdog/v6.json");
                assertNotNull(model);
                classes.add(Path.of(model.toURI()).getParent().getParent().toString());
                String javaExecutable=Path.of(System.getProperty("java.home"),"bin",
                        System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
                Process child=new ProcessBuilder(javaExecutable,"-cp",
                        String.join(System.getProperty("path.separator"),classes),
                        HyperDogRestartProbe.class.getName(),input.toString(),output.toString())
                        .redirectErrorStream(true).start();
                boolean exited=child.waitFor(30,java.util.concurrent.TimeUnit.SECONDS);
                if(!exited)child.destroyForcibly();
                assertTrue(exited,"fresh JVM timed out at "+checkpoint);
                String outputText=new String(child.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                assertEquals(0,child.exitValue(),checkpoint+" fresh JVM error: "+outputText);
                assertTrue(outputText.contains("HYPER_DOG_PHASE03_FRESH_JVM_RESTART=PASS"));
                assertEquals(expected.snapshot(),
                        JsonParser.parseString(Files.readString(output)).getAsJsonObject(),
                        "RNG, mode, replay/AT state, BET and credit changed after JVM restart at "+checkpoint);
            }finally{
                Files.deleteIfExists(input);
                Files.deleteIfExists(output);
            }
        }
    }

    @Test void trueBarPreemptsBothIndependentAndLinkedDrawAndIsUpperTicket() {
        for(int seed=0;seed<256;seed++){
            var g=newGame(seed);g.bet();
            var o=g.leverForced(HyperDogNormalGame.Role.BAR);
            assertEquals(HyperDogNormalGame.Hit.BAR,o.hit());
            assertTrue(o.entersAt());
            assertEquals(o.modeAfter(),o.savedEntryMode());
            assertEquals(15,o.payout());
            g.finish();
            assertTrue(g.waitingForAt());
            assertFalse(g.replayFree());
            assertFalse(g.canLever());assertFalse(g.canBet());
            var after=g.resumeAfterAt();
            assertNotNull(after);
            assertFalse(g.waitingForAt());
        }
    }
    @Test void allConsecutiveRoleEventsAreIndependentAndReset() {
        boolean replay5=false,grape5=false,weak2=false,water2=false;
        for(long seed=0;seed<5000&&!(replay5&&grape5&&weak2&&water2);seed++){
            for(var role:new HyperDogNormalGame.Role[]{
                    HyperDogNormalGame.Role.REPLAY,HyperDogNormalGame.Role.GRAPE,
                    HyperDogNormalGame.Role.WEAK_CHERRY,HyperDogNormalGame.Role.WATERMELON}){
                var g=newGame(seed);
                boolean reached=true;int max=role==HyperDogNormalGame.Role.REPLAY||role==HyperDogNormalGame.Role.GRAPE?5:2;
                for(int i=1;i<=max;i++){
                    var o=play(g,role);
                    if(i==3 && (role==HyperDogNormalGame.Role.GRAPE||role==HyperDogNormalGame.Role.REPLAY))
                        assertEquals(role.name()+"_STREAK_3",o.trigger());
                    if(i==4 && (role==HyperDogNormalGame.Role.GRAPE||role==HyperDogNormalGame.Role.REPLAY))
                        assertEquals(role.name()+"_STREAK_4",o.trigger());
                    if(i==max){
                        if(role==HyperDogNormalGame.Role.REPLAY){replay5|=o.entersAt();assertTrue(o.entersAt());}
                        if(role==HyperDogNormalGame.Role.GRAPE){grape5|=o.entersAt();assertTrue(o.entersAt());}
                        if(role==HyperDogNormalGame.Role.WEAK_CHERRY){
                            weak2|=true;assertEquals("WEAK_CHERRY_2PLUS_CONSECUTIVE",o.trigger());
                        }
                        if(role==HyperDogNormalGame.Role.WATERMELON){
                            water2|=true;assertEquals("WATERMELON_2PLUS_CONSECUTIVE",o.trigger());
                        }
                    }
                    if(g.waitingForAt()&&i<max){reached=false;break;}
                }
                if(reached && !g.waitingForAt()){
                    resumeIfHit(g);
                    play(g,HyperDogNormalGame.Role.MISS);
                    assertEquals(0,g.consecutive(role));
                }
            }
        }
        assertTrue(replay5&&grape5&&weak2&&water2);
    }
    @Test void noATDoubleStartAndSavedModeAfterSameGameModeRise() {
        boolean seenShiftAndHit=false;
        for(long seed=0;seed<20000&&!seenShiftAndHit;seed++){
            var g=newGame(seed);
            var o=play(g,HyperDogNormalGame.Role.STRONG_CHERRY);
            assertEquals(1,g.games());
            if(o.entersAt()&&o.modeBefore()!=o.modeAfter()){
                seenShiftAndHit=true;
                assertEquals(o.modeAfter(),g.savedHitMode());
                assertEquals(o.modeAfter(),o.savedEntryMode());
            }
        }
        assertTrue(seenShiftAndHit);
    }
    @Test void deterministicIdenticalSeedAndResumedRandomStateForEverySetting() {
        for(int setting=1;setting<=6;setting++){
            var a=new HyperDogNormalGame(setting,113355,5000000);
            var b=new HyperDogNormalGame(setting,113355,5000000);
            for(int game=0;game<400;game++){
                assertEquals(a.snapshot(),b.snapshot());
                if(!a.replayFree()){a.bet();b.bet();}
                var x=a.lever();var y=b.lever();
                assertEquals(x,y);assertEquals(a.snapshot(),b.snapshot());
                b=HyperDogNormalGame.restore(b.snapshot());
                assertEquals(a.finish(),b.finish());
                assertEquals(a.snapshot(),b.snapshot());
                if(a.waitingForAt()){
                    assertEquals(a.resumeAfterAt(),b.resumeAfterAt());
                    assertEquals(a.snapshot(),b.snapshot());
                }
            }
        }
    }
    @Test void produceReproducibleSeededTraceForIndependentPythonV5Oracle() throws Exception {
        JsonObject trace=new JsonObject();trace.addProperty("seed",20261108L);
        trace.addProperty("credits",10_000_000L);
        JsonArray settings=new JsonArray();
        for(int setting=1;setting<=6;setting++){
            var g=new HyperDogNormalGame(setting,20261108L,10_000_000L);
            JsonObject entry=new JsonObject();entry.addProperty("setting",setting);
            entry.addProperty("initialMode",g.mode().name());
            JsonArray rounds=new JsonArray();
            for(int n=0;n<250;n++){
                if(!g.replayFree())g.bet();
                var v=g.lever();
                JsonObject row=new JsonObject();
                row.addProperty("serial",v.serial());row.addProperty("role",v.role().name());
                row.addProperty("bet",v.bet());row.addProperty("payout",v.payout());
                row.addProperty("trigger",v.trigger());row.addProperty("modeBefore",v.modeBefore().name());
                row.addProperty("modeAfter",v.modeAfter().name());
                row.addProperty("single",v.singleHit());row.addProperty("linked",v.linkedHit());
                row.addProperty("hit",v.hit().name());
                row.addProperty("creditsBeforeFinish",g.credits());
                g=HyperDogNormalGame.restore(g.snapshot());
                g.finish();
                row.addProperty("creditsAfterFinish",g.credits());
                if(g.waitingForAt()){
                    var after=g.resumeAfterAt();
                    row.addProperty("atExitMode",after.name());
                }
                rounds.add(row);
            }
            entry.add("rounds",rounds);settings.add(entry);
        }
        trace.add("settings",settings);
        Path path=Path.of("build","reports","hyperdog-phase03-trace.json");
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path,trace.toString());
        assertTrue(Files.size(path)>3000);
    }
}
