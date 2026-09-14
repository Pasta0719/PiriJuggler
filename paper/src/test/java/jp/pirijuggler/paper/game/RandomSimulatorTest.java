package jp.pirijuggler.paper.game;

import com.google.gson.*;
import jp.pirijuggler.paper.reel.InternalRole;
import jp.pirijuggler.paper.database.StartupProfile;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RandomSimulatorTest {
    @Test void everyWeightIntervalHasExactBoundariesIncludingOverlaps() throws Exception {
        var config=GameFixture.defaults();var weights=new RoleWeights(config);var settings=StartupProfile.map(StartupProfile.map(config.get("probabilities")).get("settings"));
        for(int setting=1;setting<=6;setting++) {
            var row=StartupProfile.map(settings.get(Integer.toString(setting)));int cursor=0;
            for(var entry:row.entrySet()) {int weight=((Number)entry.getValue()).intValue();var role=InternalRole.valueOf(entry.getKey().toUpperCase(Locale.ROOT));assertEquals(role,weights.at(setting,cursor));assertEquals(role,weights.at(setting,cursor+weight-1));cursor+=weight;}
            assertEquals(1_000_000_000,cursor);
        }
        assertThrows(IllegalArgumentException.class,()->weights.at(0,0));assertThrows(IllegalArgumentException.class,()->weights.at(1,1_000_000_000));
    }
    @Test void eventSimulatorAndOtherMachinesCannotAdvanceGameplayStream() {
        var control=new RandomStreams(42);var varied=new RandomStreams(42);
        for(int i=0;i<100;i++){varied.eventAllocation().nextLong();varied.gameplay(999).nextLong();varied.runtimeSimulation().nextLong();assertEquals(control.gameplay(1).nextLong(),varied.gameplay(1).nextLong());}
        var reversed=new RandomStreams(42);reversed.gameplay(2);assertEquals(new RandomStreams(42).gameplay(1).nextLong(),reversed.gameplay(1).nextLong());
    }
    @Test void simulatorAppliesReplayBonusEntryAndAllGrossBonusBets() {
        for(var role:List.of(InternalRole.REPLAY,InternalRole.GRAPE,InternalRole.BIG,InternalRole.REG,InternalRole.CHERRY_BIG,InternalRole.PIERO_REG)) {
            var r=Simulator.run(GameFixture.forced(role),1,20,new SplittableRandom(1));String bonus=GameRules.bonus(role);
            assertEquals(role==InternalRole.REPLAY?1:20,r.paidNormalSpins());
            assertEquals((role==InternalRole.REPLAY?3:60)+(bonus==null?0:20*GameRules.bonusTotalBet(bonus)),r.totalBet());
            assertEquals(20L*GameRules.payout(role)+(bonus==null?0:20L*GameRules.bonusGross(bonus)),r.totalPayout());
            assertEquals(r.totalPayout()-r.totalBet(),r.net());
        }
    }
    @Test void tenMillionFixedSeedForAllSixSettingsMeetsPointTwoPercentageTolerance() throws Exception {
        var weights=new RoleWeights(GameFixture.defaults());double[] targets={97.8,99.4,101,103.4,106,111.4};var output=new JsonArray();
        for(int setting=1;setting<=6;setting++) {
            var result=Simulator.run(weights,setting,10_000_000,new SplittableRandom(0x504952494A554747L));
            assertEquals(targets[setting-1],result.payoutPercent(),.20,"Setting "+setting+" "+result);
            var record=new Gson().toJsonTree(result).getAsJsonObject();record.addProperty("setting",setting);record.addProperty("targetPercent",targets[setting-1]);output.add(record);
        }
        Path path=Path.of(System.getProperty("piri.specRoot"),"paper/build/reports/simulator-10m.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(output));
    }
    @Test void fixedSeedIsDeterministicAndCommandBoundsAreEnforced() throws Exception {
        var weights=new RoleWeights(GameFixture.defaults());assertEquals(Simulator.run(weights,1,100_000,new SplittableRandom(0x504952494A554747L)),Simulator.run(weights,1,100_000,new SplittableRandom(0x504952494A554747L)));
        for(long invalid:new long[]{0,-1,100_000_001})assertThrows(IllegalArgumentException.class,()->Simulator.run(weights,1,invalid,new SplittableRandom(1)));
        assertThrows(IllegalArgumentException.class,()->Simulator.run(weights,7,1,new SplittableRandom(1)));
    }
}
