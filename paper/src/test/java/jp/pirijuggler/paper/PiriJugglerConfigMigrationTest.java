package jp.pirijuggler.paper;

import jp.pirijuggler.paper.config.ConfigValidation;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PiriJugglerConfigMigrationTest {
    private static String config() throws Exception {
        return Files.readString(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"));
    }

    @Test void legacyGodRowsBecomeValidYamlWithoutLosingNewlines() throws Exception {
        String legacy=config()
                .replace("bonus_scale_ppm: 297986", "bonus_scale_ppm: 743613")
                .replace("bonus_scale_ppm: 223878", "bonus_scale_ppm: 537600")
                .replace(", precursor_two_high_ppm: 94884", "")
                .replace(", precursor_two_high_ppm: 139688", "");
        var result=PiriJugglerPlugin.migrateJugglerGodPremonitionText(legacy);
        assertEquals(2,result.changed());
        assertEquals(0,result.skipped());
        assertTrue(result.text().contains("bonus_scale_ppm: 297986"));
        assertTrue(result.text().contains("precursor_two_high_ppm: 94884"));
        assertTrue(result.text().contains("bonus_scale_ppm: 223878"));
        assertTrue(result.text().contains("precursor_two_high_ppm: 139688"));
        assertTrue(result.text().contains("\neconomy:") || result.text().contains("\njuggler_god:"),
                "the migration must preserve real YAML line separators");
        var validation=ConfigValidation.load(new StringReader(result.text()));
        assertTrue(validation.valid(),()->"Migrated YAML must be valid: "+validation.errors());

        var repeated=PiriJugglerPlugin.migrateJugglerGodPremonitionText(result.text());
        assertEquals(0,repeated.changed());
        assertEquals(result.text(),repeated.text(),"migration must be idempotent");
    }

    @Test void customTuningIsNotOverwritten() throws Exception {
        String custom=config()
                .replace("bonus_scale_ppm: 297986", "bonus_scale_ppm: 410000")
                .replace(", precursor_two_high_ppm: 94884", "");
        var migrated=PiriJugglerPlugin.migrateJugglerGodPremonitionText(custom);
        assertEquals(0,migrated.changed());
        assertEquals(1,migrated.skipped());
        assertEquals(custom,migrated.text());
        assertTrue(ConfigValidation.load(new StringReader(migrated.text())).valid());
    }
}
