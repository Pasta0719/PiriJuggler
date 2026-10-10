package jp.pirijuggler.paper;

import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.config.JugglerGodPremonitionMigration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PiriJugglerConfigMigrationTest {

    @Test void legacyStockGodRowsMigrateWithoutCorruptingYamlNewlines() {
        String legacy=String.join("\n",
                "juggler_god:",
                "  settings:",
                "    '1': {bonus_scale_ppm: 743613, small_role_scale_ppm: 813500}",
                "juggler_god_extreme:",
                "  settings:",
                "    '6': {bonus_scale_ppm: 537600, small_role_scale_ppm: 700000, god_continuation_percent: 90}",
                "");
        var migrated=JugglerGodPremonitionMigration.migrate(legacy);
        assertEquals(2,migrated.changed(),migrated::toString);
        assertEquals(0,migrated.skipped(),migrated::toString);
        assertTrue(migrated.text().contains("bonus_scale_ppm: 297986"));
        assertTrue(migrated.text().contains("precursor_two_high_ppm: 94884"));
        assertTrue(migrated.text().contains("bonus_scale_ppm: 223878"));
        assertTrue(migrated.text().contains("precursor_two_high_ppm: 139688"));
        assertTrue(migrated.text().contains("\njuggler_god_extreme:"),
                "real newline must separate the YAML sections");
        var second=JugglerGodPremonitionMigration.migrate(migrated.text());
        assertEquals(0,second.changed(),second::toString);
        assertEquals(migrated.text(),second.text());
    }

    @Test void customizedGodRowsAreNotOverwritten() {
        String custom=String.join("\n",
                "juggler_god:",
                "  settings:",
                "    '1': {bonus_scale_ppm: 410000, small_role_scale_ppm: 813500}",
                "");
        var migrated=JugglerGodPremonitionMigration.migrate(custom);
        assertEquals(0,migrated.changed(),migrated::toString);
        assertEquals(1,migrated.skipped(),migrated::toString);
        assertEquals(custom,migrated.text());
    }

    @Test void currentFullConfigurationRemainsValidAfterMigration() throws Exception {
        String full=Files.readString(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"));
        var migrated=JugglerGodPremonitionMigration.migrate(full);
        assertTrue(ConfigValidation.load(new StringReader(migrated.text())).valid(),
                ()->"Invalid YAML after migration: "+migrated);
    }
}
