package jp.pirijuggler.paper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.StringReader;
import static org.junit.jupiter.api.Assertions.*;

class V4ConfigValidationTest {
    private static String defaults() throws Exception {
        return Files.readString(Path.of(System.getProperty("piri.specRoot"), "paper/src/main/resources/config.yml"));
    }
    private static ConfigValidation.Result load(String yaml) { return ConfigValidation.load(new StringReader(yaml)); }

    @Test void denominatorIsConfigurableAndChanceUsesItsActualValue() throws Exception {
        String yaml = defaults().replace("denominator: 1000000", "denominator: 10");
        assertTrue(load(yaml.replace("big_chance_weight: 50000", "big_chance_weight: 10")).valid());
        assertFalse(load(yaml.replace("big_chance_weight: 50000", "big_chance_weight: 11")).valid());
        assertFalse(load(yaml.replace("big_chance_weight: 50000", "big_chance_weight: -1")).valid());
    }

    @Test void premiumBAloneCannotServeBigOrPieroBig() throws Exception {
        String yaml = defaults();
        for (String key : new String[]{"reverse", "sound_first_peka", "strong_after_peka", "five_notice_blink", "fake_tenpai"})
            yaml = yaml.replace(key + ": 1", key + ": 0");
        assertFalse(load(yaml).valid());
        assertTrue(load(yaml.replace("reverse: 0", "reverse: 1")).valid());
    }

    @Test void validBoundariesAndMissingGraceAreChecked() throws Exception {
        assertTrue(load(defaults().replace("idle_timeout_seconds: 180", "idle_timeout_seconds: 30")
                .replace("disconnect_grace_seconds: 60", "disconnect_grace_seconds: 0")
                .replace("notice_volume: 1.0", "notice_volume: 2.0")).valid());
        assertFalse(load(defaults().replace("  disconnect_grace_seconds: 60", "")).valid());
    }
}
