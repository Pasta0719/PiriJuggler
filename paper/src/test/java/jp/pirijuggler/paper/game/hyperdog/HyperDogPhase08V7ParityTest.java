package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.pirijuggler.common.reel.HyperDogReelControl;
import jp.pirijuggler.common.reel.Reel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase08: check the actual Paper RNG/award implementation against the immutable
 * approved v7 file, not a separately retyped table or legacy v5/v6 fixture.
 * Monte Carlo tolerances are fixed before execution; no selected lucky seeds.
 */
class HyperDogPhase08V7ParityTest {
    private static final Path ROOT=Path.of(System.getProperty("piri.specRoot"));
    private static final Path SPEC=ROOT.resolve("original-at-phases/reference/pirijuggler_new_machine_calibrated_v7.json");

    private static JsonObject reference() throws Exception {
        return JsonParser.parseString(Files.readString(SPEC,StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static double chance(JsonObject o,String field) { return o.get(field).getAsDouble(); }
    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test void allNineRolePaymentsAndSettingModeChancesMatchCurrentV7() throws Exception {
        JsonObject spec=reference();
        String canonical=Files.readString(SPEC,StandardCharsets.UTF_8).replace("\r\n","\n");
        String fingerprint=sha256(canonical.getBytes(StandardCharsets.UTF_8));
        assertEquals(fingerprint,HyperDogNormalGame.parameterSha256());
        assertEquals(fingerprint,HyperDogAtGame.parameterSha256());
        JsonObject pay=spec.getAsJsonObject("fixed_requirements").getAsJsonObject("payout_regular_or_at");
        for(var role:HyperDogNormalGame.Role.values()) {
            assertEquals(pay.getAsJsonObject(role.name()).get("NORMAL").getAsInt(),
                    HyperDogNormalGame.payout(role),"normal payout "+role);
            HyperDogReelControl.Role stripRole=HyperDogReelControl.Role.valueOf(role.name());
            for(String area:new String[]{"NORMAL","LOWER_AT","UPPER_AT"}) {
                assertEquals(pay.getAsJsonObject(role.name()).get(area).getAsInt(),
                        HyperDogReelControl.payout(stripRole,!"NORMAL".equals(area)),
                        "reel/accounting "+role+"/"+area);
            }
        }
        JsonObject n=spec.getAsJsonObject("normal_mode");
        for(int setting=1;setting<=6;setting++){
            double factor=n.getAsJsonObject("single_hit_probability_multiplier_by_setting")
                    .get(Integer.toString(setting)).getAsDouble();
            for(var mode:HyperDogNormalGame.Mode.values()){
                double denominator=n.getAsJsonObject("base_single_at_hit_denominator_by_mode")
                        .get(mode.name()).getAsDouble();
                assertEquals(Math.min(.95,factor/denominator),
                        HyperDogNormalGame.singleHitChance(mode,setting),1e-15,
                        "setting="+setting+" mode="+mode);
            }
        }
        JsonObject linked=n.getAsJsonObject("role_linked_at_hit_percent");
        double scale=n.get("rare_role_linked_at_hit_scale").getAsDouble();
        assertEquals(0.85,scale,1e-15);
        Map<HyperDogNormalGame.Role,String> rare=Map.of(
                HyperDogNormalGame.Role.WEAK_CHERRY,"WEAK_CHERRY_SINGLE",
                HyperDogNormalGame.Role.WATERMELON,"WATERMELON_SINGLE",
                HyperDogNormalGame.Role.CHANCE,"CHANCE",
                HyperDogNormalGame.Role.STRONG_CHERRY,"STRONG_CHERRY",
                HyperDogNormalGame.Role.STRONG_CHANCE,"STRONG_CHANCE");
        for(var e:rare.entrySet())
            assertEquals(linked.get(e.getValue()).getAsDouble()/100.*scale,
                    HyperDogNormalGame.effectiveRoleLinkedAtChance(e.getKey(),e.getValue()),1e-15);
        for(String event:new String[]{"REPLAY_STREAK_4","REPLAY_STREAK_5","GRAPE_STREAK_4","GRAPE_STREAK_5"}){
            var role=event.startsWith("REPLAY")?HyperDogNormalGame.Role.REPLAY:HyperDogNormalGame.Role.GRAPE;
            assertEquals(linked.get(event).getAsDouble()/100.,
                    HyperDogNormalGame.effectiveRoleLinkedAtChance(role,event),1e-15);
        }
        JsonObject upper=spec.getAsJsonObject("upper_at").getAsJsonObject("regular_addons");
        assertEquals(2000, spec.getAsJsonObject("fixed_requirements")
                .get("upper_max_single_role_bonus_medals").getAsInt());
        var multipliers=upper.getAsJsonObject("role_multiplier_from_lower_by_role");
        assertEquals(8,multipliers.get("WEAK_CHERRY").getAsInt());
        assertEquals(8,multipliers.get("WATERMELON").getAsInt());
        assertEquals(9,multipliers.get("CHANCE").getAsInt());
        assertEquals(10,multipliers.get("STRONG_CHERRY").getAsInt());
        assertEquals(10,multipliers.get("STRONG_CHANCE").getAsInt());
        assertEquals(0.5,spec.getAsJsonObject("upper_cz")
                .get("total_success_rate_without_guarantee").getAsDouble(),1e-15);
    }

    @Test void sixSettingsAndIndependentSeedsSampleServerDrawsWithoutCherryPicking() throws Exception {
        JsonObject spec=reference().getAsJsonObject("normal_and_at_common_role_draw");
        JsonObject roles=spec.getAsJsonObject("exclusive_base_role_probability_per_game");
        final int count=60000; // per seed, two independent predeclared seeds per setting
        for(int setting=1;setting<=6;setting++){
            long[] observed=new long[HyperDogNormalGame.Role.values().length];
            final long[] seeds={20261118000L+setting*1009L,20261212000L+setting*1009L};
            for(long seed:seeds){
                var game=new HyperDogNormalGame(setting,seed,2000000);
                for(int i=0;i<count;i++) {
                    if(game.canBet())game.bet();
                    var pending=game.lever();
                    observed[pending.role().ordinal()]++;
                    var finished=game.finish();
                    assertEquals(pending,finished);
                    if(finished.entersAt())game.resumeAfterAt();
                }
            }
            for(var role:HyperDogNormalGame.Role.values()){
                double p=switch(role) {
                    case MISS -> spec.getAsJsonObject("miss_probability_per_game").get("NORMAL").getAsDouble();
                    case GRAPE -> spec.getAsJsonObject("grape_probability_per_game").get("NORMAL").getAsDouble();
                    default -> roles.get(role.name()).getAsDouble();
                };
                double expected=seeds.length*count*p;
                double sigma=Math.sqrt(seeds.length*count*p*(1-p));
                double tolerance=7*sigma+4; // fixed 7-sigma + low-count discretization
                assertTrue(Math.abs(observed[role.ordinal()]-expected)<tolerance,
                    "setting="+setting+" role="+role+" observed="+observed[role.ordinal()]+
                    " expected="+expected+" tolerance="+tolerance);
            }
        }
    }

    @Test void realUpperCzGameDrawsFiveTurnsToHalfSuccessWithoutGuarantee() throws Exception {
        int successful=0;
        final int trials=10000;
        for(int i=0;i<trials;i++){
            var start=new HyperDogAtGame(1,31000000L+i*137L,1000000,0,0,true);
            start.confirmRed7();
            JsonObject saved=start.snapshot();
            saved.addProperty("stage","UPPER_CZ");
            saved.addProperty("czGame",0);
            saved.addProperty("czFromUpper",true);
            saved.addProperty("czForcedGame",0);
            var cz=HyperDogAtGame.restore(saved);
            boolean win=false;
            for(int turn=0;turn<5;turn++){
                var prepared=cz.leverForced(HyperDogAtGame.Role.MISS);
                assertEquals(0,prepared.bet());
                assertEquals(0,prepared.payout());
                boolean outcome=prepared.czSuccess();
                cz.finish();
                if(outcome){win=true;break;}
            }
            successful+=win?1:0;
        }
        double ratio=(double)successful/trials;
        assertTrue(Math.abs(ratio-0.5)<0.025,
                "actual Paper five-game CZ success "+ratio+" independent seeds=10000");
    }

    @Test void realUpperRareAwardAmountsRespectV7AndSingleTwoThousandCap() {
        var game=new HyperDogAtGame(3,2026120801L,10_000_000,0,0,true);
        game.confirmRed7();
        for(int i=0;i<5;i++){
            var initial=game.leverForced(HyperDogAtGame.Role.MISS);
            assertEquals(100,initial.award());
            assertEquals(0,initial.bet());
            assertEquals(0,initial.payout());
            game.finish();
        }
        assertEquals(HyperDogAtGame.Stage.UPPER_AT,game.stage());
        var roles=new HyperDogAtGame.Role[]{
                HyperDogAtGame.Role.WEAK_CHERRY,HyperDogAtGame.Role.WATERMELON,
                HyperDogAtGame.Role.CHANCE,HyperDogAtGame.Role.STRONG_CHERRY,
                HyperDogAtGame.Role.STRONG_CHANCE
        };
        for(int n=0;n<1000;n++){
            var role=roles[n%roles.length];
            if(game.waitingForBet())game.bet();
            var spin=game.leverForced(role);
            assertEquals(3,spin.bet());
            assertEquals(HyperDogReelControl.payout(HyperDogReelControl.Role.valueOf(role.name()),true),
                    spin.payout());
            assertTrue(spin.award()>=10 && spin.award()<=2000,
                    "upper single award "+spin.award()+" role="+role);
            if(spin.premium())assertEquals(2000,spin.award());
            assertEquals(0,spin.ticket());
            game.finish();
        }
    }

    @Test void reelSolverGivesLegalFinalPositionsForEveryRoleAndNeverAllowsFalsePremium() {
        for(var role:HyperDogReelControl.Role.values())
            assertTrue(HyperDogReelControl.legalTriplets(role)>0,role.name());
        for(var reel:Reel.values()){
            for(int index=-42;index<=42;index++)
                assertEquals(HyperDogReelControl.icon(reel,Math.floorMod(index,21)),
                        HyperDogReelControl.icon(reel,index));
        }
    }
}
