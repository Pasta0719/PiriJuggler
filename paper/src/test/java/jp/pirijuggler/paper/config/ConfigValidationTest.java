package jp.pirijuggler.paper.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigValidationTest {
    private static final Path ROOT = Path.of(System.getProperty("piri.specRoot"));
    private static String defaults() throws Exception { return Files.readString(ROOT.resolve("paper/src/main/resources/config.yml")); }
    private static ConfigValidation.Result load(String config) { return ConfigValidation.load(new StringReader(config)); }

    @Test void fullDefaultConfigIsValidAndFixedGameConstantsStillMatchLock() throws Exception {
        String yaml = defaults();
        var result = load(yaml);
        assertTrue(result.valid(), () -> result.errors().toString());
        var lock = JsonParser.parseString(Files.readString(ROOT.resolve("docs/spec-lock.json"))).getAsJsonObject();
        var fixed = lock.getAsJsonObject("fixedConstants");
        assertEquals(fixed.get("creditMax").getAsInt(), FixedGameRules.CREDIT_MAX);
        assertEquals(fixed.get("normalBet").getAsInt(), FixedGameRules.NORMAL_BET);
        assertEquals(fixed.get("entryBet").getAsInt(), FixedGameRules.ENTRY_BET);
        assertEquals(fixed.get("bonusBet").getAsInt(), FixedGameRules.BONUS_BET);
        assertEquals(fixed.get("bigThreshold").getAsInt(), FixedGameRules.BIG_THRESHOLD);
        assertEquals(fixed.get("bigPayout").getAsInt(), FixedGameRules.BIG_PAYOUT);
        assertEquals(fixed.get("regThreshold").getAsInt(), FixedGameRules.REG_THRESHOLD);
        assertEquals(fixed.get("regPayout").getAsInt(), FixedGameRules.REG_PAYOUT);
        assertEquals(fixed.get("maxMedalBundle").getAsInt(), FixedGameRules.MAX_MEDAL_BUNDLE);
    }

    @ParameterizedTest @CsvSource(delimiter = '|', value = {
            "protocol_version: 2|protocol_version: 1", "loan_medals: 46|loan_medals: 0", "loan_medals: 46|loan_medals: 1.5",
            "loan_amount: 1000|loan_amount: 0", "loan_amount: 1000|loan_amount: -1",
            "medal_cost: 52|medal_cost: 0", "medal_cost: 52|medal_cost: 1.5", "vault_value: 1000|vault_value: -1",
            "big_chance_weight: 50000|big_chance_weight: -1", "big_chance_weight: 50000|big_chance_weight: 1000001",
            "denominator: 1000000|denominator: 0", "reverse: 1|reverse: -1", "reverse: 1|reverse: 0.5",
            "replay: 137023842|replay: 137023843", "replay: 137023842|replay: -1",
            "replay: 137023842|replay: 137023842.0", "replay: 137023842|replay: 999999999999999999999999999",
            "notice_volume: 1.0|notice_volume: .nan", "notice_volume: 1.0|notice_volume: .inf",
            "idle_timeout_seconds: 180|idle_timeout_seconds: 0", "idle_timeout_seconds: 180|idle_timeout_seconds: 29",
            "disconnect_grace_seconds: 60|disconnect_grace_seconds: -1", "notice_volume: 1.0|notice_volume: 2.01", "notice_volume: 1.0|notice_volume: -0.1", "MONDAY: normal|MONDAY: unknown",
            "1: 55|1: 54", "min_setting_6: 0|min_setting_6: -1", "type: NONE|type: OTHER"})
    void invalidValuesDisableGameplay(String before, String after) throws Exception {
        var result = load(defaults().replace(before, after));
        assertFalse(result.valid(), before + " -> " + after);
        assertFalse(result.errors().isEmpty());
    }

    @Test void allZeroPremiumAndMissingRoleAndMissingRequiredKeysAreRejected() throws Exception {
        String config = defaults();
        for (String name : new String[]{"reverse", "middle_cherry", "sound_first_peka", "strong_after_peka", "five_notice_blink", "fake_tenpai"})
            config = config.replace(name + ": 1", name + ": 0");
        assertFalse(load(config).valid());
        assertFalse(load(defaults().replace("      replay: 137023842", "")).valid());
        assertFalse(load(defaults().replace("  notice_volume: 1.0", "")).valid());
        assertFalse(load(defaults().replace("  loan_medals: 46", "")).valid());
        assertFalse(load(defaults() + "credit_max: 99\n").valid());
    }

    @ParameterizedTest @ValueSource(strings = {"", "[]", "null", "x: [", "!!java.lang.Runtime {}", "x: &loop [*loop]"})
    void malformedYamlCannotBecomeValidConfiguration(String yaml) { assertFalse(load(yaml).valid()); }

    @Test void duplicateKeysAreRejectedAndValidEditsAreAccepted() throws Exception {
        assertFalse(load(defaults() + "protocol_version: 1\n").valid());
        assertTrue(load(defaults().replace("big_chance_weight: 50000", "big_chance_weight: 0")).valid());
        assertTrue(load(defaults().replace("big_chance_weight: 50000", "big_chance_weight: 1000000")).valid());
        assertTrue(load(defaults().replace("loan_medals: 46", "loan_medals: 50").replace("loan_amount: 1000", "loan_amount: 1500").replace("vault_value: 1000", "vault_value: 0")).valid());
    }

    @Test void allSpecifiedPatternShapesValidate() throws Exception {
        for (String pattern : new String[]{"{type: ALL, setting: 6, machine_ids: []}",
                "{type: SUFFIX, suffixes: [3, 7], distribution: {1: 0, 2: 0, 3: 10, 4: 30, 5: 35, 6: 25}}",
                "{type: RUN, run_length: 3, run_count: 2, distribution: {1: 0, 2: 0, 3: 10, 4: 30, 5: 35, 6: 25}}"}) {
            var result = load(defaults().replace("{type: NONE}", pattern));
            assertTrue(result.valid(), () -> result.errors().toString());
        }
    }
}
