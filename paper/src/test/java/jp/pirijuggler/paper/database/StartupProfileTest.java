package jp.pirijuggler.paper.database;

import jp.pirijuggler.paper.machine.Machine;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StartupProfileTest {
    Map<String, Object> distribution(int setting) { Map<String, Object> weights = new HashMap<>(); for (int s = 1; s <= 6; s++) weights.put(Integer.toString(s), s == setting ? 100 : 0); return weights; }
    Map<String, Object> definition(Map<String, Object> pattern, int six, int five) { return Map.of("distribution",distribution(1),"min_setting_6",six,"min_setting_5_plus",five,"pattern",pattern); }
    List<Machine> machines(int count) {
        List<Machine> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) result.add(new Machine(i * 3, new Machine.Location(UUID.randomUUID(),"w",i,64,0,"NORTH"),2,true,true,false,0,0,0,0,0));
        return result;
    }
    @Test void schedulePriorityAndReason() {
        var def = definition(Map.of("type","NONE"),0,0);
        var config = Map.<String, Object>of("events",Map.of("profiles",Map.of("normal",def,"special",def,"manual",def),"default_profile","normal","weekday",Map.of("TUESDAY","normal"),"special_dates",Map.of("2023-11-14","special")));
        assertEquals("manual", StartupProfile.resolve(config,LocalDate.parse("2023-11-14"),"manual").name());
        assertEquals("SPECIAL_DATE", StartupProfile.resolve(config,LocalDate.parse("2023-11-14"),null).source());
        assertEquals("SERVER_START", StartupProfile.resolve(config,LocalDate.parse("2023-11-21"),null).reason());
    }
    @Test void allSuffixAndBaseAllocation() {
        var all = new StartupProfile("s","MANUAL_NEXT",definition(Map.of("type","ALL","setting",6,"machine_ids",List.of(3)),0,0));
        assertEquals(Map.of(3,6,6,1,9,1), all.allocate(machines(3),new SplittableRandom(1),ignored -> {}));
        var suffix = new StartupProfile("s","SPECIAL_DATE",definition(Map.of("type","SUFFIX","suffixes",List.of(3,9),"distribution",distribution(5)),0,0));
        assertEquals(Map.of(3,5,6,1,9,5),suffix.allocate(machines(3),new SplittableRandom(1),ignored -> {}));
    }
    @Test void runsAlwaysAchieveMaximumNonOverlappingCountEvenAcrossSparseIds() {
        var profile = new StartupProfile("s","MANUAL_NEXT",definition(Map.of("type","RUN","run_length",3,"run_count",3,"distribution",distribution(4)),0,0));
        for (int seed = 0; seed < 100; seed++) {
            List<String> warnings = new ArrayList<>(); var values = profile.allocate(machines(8),new SplittableRandom(seed),warnings::add);
            assertEquals(6, values.values().stream().filter(v -> v == 4).count()); assertEquals(1,warnings.size());
        }
    }
    @Test void guaranteesUpgradeIncludingPatternAndExcludedMachinesRemainUntouched() {
        var profile = new StartupProfile("s","MANUAL_NEXT",definition(Map.of("type","ALL","setting",6,"machine_ids",List.of(3)),2,3));
        List<Machine> machines = new ArrayList<>(machines(3));
        machines.add(new Machine(100,machines.getFirst().location(),2,false,true,false,0,0,0,0,0));
        machines.add(new Machine(101,machines.getFirst().location(),2,true,false,false,0,0,0,0,0));
        var result = profile.allocate(machines,new SplittableRandom(2),ignored -> {});
        assertEquals(3,result.size()); assertEquals(6,result.get(3)); assertEquals(2,result.values().stream().filter(v -> v == 6).count());
        assertEquals(3,result.values().stream().filter(v -> v >= 5).count());
        assertTrue(profile.allocate(List.of(),new SplittableRandom(3),ignored -> {}).isEmpty());
    }
}
