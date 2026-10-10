package jp.pirijuggler.paper;

import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.config.JugglerGodPremonitionMigration;
import jp.pirijuggler.paper.config.JugglerGodStockOddsMigration;
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

    @Test void existingCustomizedGodAndExtremeConfigsWithoutNewOddsStillBoot() throws Exception {
        // Regression: previous releases kept custom rows without precursor_two_high_ppm,
        // but strict config validation then disabled all gameplay at startup.
        String current=Files.readString(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"));
        String oldConfig=current.replaceAll(", precursor_two_high_ppm: \\d+", "")
                .replace("bonus_scale_ppm: 270000", "bonus_scale_ppm: 410000")
                .replace("bonus_scale_ppm: 198000", "bonus_scale_ppm: 410001");
        assertFalse(oldConfig.contains("precursor_two_high_ppm"));
        assertTrue(ConfigValidation.load(new StringReader(oldConfig)).valid(),
                "Custom operator profiles missing only the newly optional key must load");
        var migrated=JugglerGodPremonitionMigration.migrate(oldConfig);
        assertTrue(migrated.text().contains("bonus_scale_ppm: 410000"));
        assertTrue(migrated.text().contains("bonus_scale_ppm: 410001"));
        assertTrue(ConfigValidation.load(new StringReader(migrated.text())).valid(),
                "Startup must accept unchanged legacy custom rows after migration");
    }

    @Test void optionalPrecursorDoesNotPermitUnexpectedKeysOrBadValues() throws Exception {
        String current=Files.readString(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"));
        String withUnknown=current.replace("precursor_two_high_ppm: 94884",
                "precursor_two_high_ppm: 94884, unknown_tuning: 1");
        assertFalse(ConfigValidation.load(new StringReader(withUnknown)).valid());
        String withWrongType=current.replace("precursor_two_high_ppm: 94884",
                "precursor_two_high_ppm: invalid");
        assertFalse(ConfigValidation.load(new StringReader(withWrongType)).valid());
        String withMissingRequired=current.replace("small_role_scale_ppm: 813500, ", "");
        assertFalse(ConfigValidation.load(new StringReader(withMissingRequired)).valid());
    }

    @Test void currentFullConfigurationRemainsValidAfterMigration() throws Exception {
        String full=Files.readString(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"));
        var migrated=JugglerGodPremonitionMigration.migrate(full);
        assertTrue(ConfigValidation.load(new StringReader(migrated.text())).valid(),
                ()->"Invalid YAML after migration: "+migrated);
    }

    @Test void stockOddsMigrationUpgradesOnlyRecognizedDefaultNormalBases() throws Exception {
        String old=String.join("\n",
                "juggler_god:",
                "  settings:",
                "    '1': {bonus_scale_ppm: 297986, small_role_scale_ppm: 813500, precursor_two_high_ppm: 94884}",
                "    '2': {bonus_scale_ppm: 412345, small_role_scale_ppm: 809600, precursor_two_high_ppm: 105041}",
                "juggler_god_extreme:",
                "  settings:",
                "    '6': {bonus_scale_ppm: 223878, small_role_scale_ppm: 700000, god_continuation_percent: 90, precursor_two_high_ppm: 139688}",
                "");
        var result=JugglerGodStockOddsMigration.migrate(old);
        assertEquals(2,result.adjusted());
        assertEquals(3,result.stockKeysAdded());
        assertEquals(1,result.customKept());
        assertTrue(result.text().contains("bonus_scale_ppm: 270000,"));
        assertTrue(result.text().contains("bonus_scale_ppm: 187000,"));
        assertTrue(result.text().contains("bonus_scale_ppm: 412345,"));
        assertTrue(result.text().contains("bonus_stock_scale_ppm: 743613"));
        assertTrue(result.text().contains("bonus_stock_scale_ppm: 734884"));
        assertTrue(result.text().contains("bonus_stock_scale_ppm: 537600"));
        var second=JugglerGodStockOddsMigration.migrate(result.text());
        assertEquals(0,second.adjusted());
        assertEquals(0,second.stockKeysAdded());
        assertEquals(result.text(),second.text());
    }

    @Test void stockOddsMigrationPreservesOperatorSpecifiedStockOdds() {
        String old=String.join("\n",
                "juggler_god:",
                "  settings:",
                "    '1': {bonus_scale_ppm: 300000, small_role_scale_ppm: 813500, precursor_two_high_ppm: 94884, bonus_stock_scale_ppm: 900000}",
                "");
        var result=JugglerGodStockOddsMigration.migrate(old);
        assertEquals(old,result.text());
        assertEquals(0,result.adjusted());
        assertEquals(0,result.stockKeysAdded());
    }

    @Test void stockOddsDefaultsAreValidWithAndWithoutOptionalKey() throws Exception {
        String current=Files.readString(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"));
        assertTrue(ConfigValidation.load(new StringReader(current)).valid());
        String legacy=current.replaceAll(", bonus_stock_scale_ppm: \\d+","");
        assertTrue(ConfigValidation.load(new StringReader(legacy)).valid());
        var migrated=JugglerGodStockOddsMigration.migrate(legacy);
        assertEquals(12,migrated.stockKeysAdded());
        assertTrue(ConfigValidation.load(new StringReader(migrated.text())).valid());
        assertFalse(ConfigValidation.load(new StringReader(
                current.replace("bonus_stock_scale_ppm: 743613","bonus_stock_scale_ppm: -1"))).valid());
    }
}
