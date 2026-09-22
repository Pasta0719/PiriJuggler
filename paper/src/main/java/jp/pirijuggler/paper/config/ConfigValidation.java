package jp.pirijuggler.paper.config;

import jp.pirijuggler.common.protocol.Protocol;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates the actual file, without filling missing keys from bundled defaults. */
public final class ConfigValidation {
    private static final Set<String> ROLES = Set.of("miss", "replay", "grape", "cherry", "bell", "piero",
            "big", "reg", "cherry_big", "cherry_reg", "piero_big", "piero_reg");
    private static final Set<String> PREMIUMS = Set.of("reverse", "middle_cherry", "sound_first_peka",
            "strong_after_peka", "five_notice_blink", "fake_tenpai");
    private static final Set<String> SETTINGS = Set.of("1", "2", "3", "4", "5", "6");
    private static final Set<String> SOUNDS = Set.of("notice_volume", "notice_strong_volume", "tenpai_volume",
            "bet_volume", "lever_volume", "stop_volume", "payout_volume", "error_volume",
            "bonus_start_volume", "bonus_end_volume");

    private ConfigValidation() { }

    public record Result(Map<String, Object> values, List<String> errors) {
        public Result { values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); errors = List.copyOf(errors); }
        public boolean valid() { return errors.isEmpty(); }
    }

    public static Result load(Reader input) {
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(50);
            options.setCodePointLimit(1_000_000);
            Object raw = new Yaml(new SafeConstructor(options)).load(input);
            if (!(raw instanceof Map<?, ?>)) return new Result(Map.of(), List.of("config: expected mapping"));
            return validate(normalizeMap((Map<?, ?>) raw));
        } catch (YAMLException | IllegalArgumentException exception) {
            return new Result(Map.of(), List.of("config: " + exception.getMessage()));
        }
    }

    public static Result validate(Map<String, Object> values) {
        Check c = new Check(values);
        c.keys("", Set.of("protocol_version", "economy", "game", "sound", "juggler_god", "premium", "prizes", "events", "probabilities"));
        c.equalInteger("protocol_version", Protocol.VERSION);
        c.keys("economy", Set.of("loan_medals", "loan_amount"));
        c.integer("economy.loan_medals", 1, Integer.MAX_VALUE);
        c.number("economy.loan_amount", BigDecimal.ONE);
        c.integer("game.idle_timeout_seconds", 30, Long.MAX_VALUE);
        c.integer("game.disconnect_grace_seconds", 0, Long.MAX_VALUE);
        c.keys("sound", SOUNDS);
        for (String sound : SOUNDS) c.numberRange("sound." + sound, BigDecimal.ZERO, BigDecimal.valueOf(2));
        c.keys("juggler_god", Set.of("normal_to_heaven_ppm", "heaven_to_heaven_ppm", "settings"));
        c.integer("juggler_god.normal_to_heaven_ppm", 0, 1_000_000);
        c.integer("juggler_god.heaven_to_heaven_ppm", 0, 1_000_000);
        c.keys("juggler_god.settings", Set.of("1","2","3","4","5","6"));
        for(int setting=1;setting<=6;setting++){
            String base="juggler_god.settings."+setting;
            c.keys(base, Set.of("bonus_scale_ppm"));
            c.integer(base+".bonus_scale_ppm", 0, 1_000_000);
        }
        BigInteger denominator = c.integer("premium.denominator", 1, Long.MAX_VALUE);
        BigInteger chance = c.integer("premium.big_chance_weight", 0, Long.MAX_VALUE);
        if (chance.compareTo(denominator) > 0) c.fail("premium.big_chance_weight", "must not exceed denominator");
        c.weights("premium.weights", PREMIUMS, null);
        BigInteger eligibleTotal = BigInteger.ZERO;
        for (String premium : PREMIUMS) {
            if (!premium.equals("middle_cherry")) eligibleTotal = eligibleTotal.add(c.integer("premium.weights." + premium, 0, Long.MAX_VALUE));
        }
        if (eligibleTotal.signum() <= 0) c.fail("premium.weights", "A+C+D+E+F sum must be positive");
        c.keys("probabilities.settings", SETTINGS);
        for (String setting : SETTINGS) c.weights("probabilities.settings." + setting, ROLES, 1_000_000_000L);
        c.keys("prizes", Set.of("small", "medium", "large"));
        for (String size : List.of("small", "medium", "large")) {
            c.integer("prizes." + size + ".medal_cost", 1, Long.MAX_VALUE);
            c.number("prizes." + size + ".vault_value", BigDecimal.ZERO);
        }
        if (!"Asia/Tokyo".equals(c.at("events.timezone"))) c.fail("events.timezone", "must be Asia/Tokyo");
        Map<String, Object> profiles = c.map("events.profiles");
        if (profiles.isEmpty()) c.fail("events.profiles", "must not be empty");
        for (String profile : profiles.keySet()) {
            String prefix = "events.profiles." + profile;
            c.weights(prefix + ".distribution", SETTINGS, 100L);
            c.integer(prefix + ".min_setting_6", 0, Long.MAX_VALUE);
            c.integer(prefix + ".min_setting_5_plus", 0, Long.MAX_VALUE);
            String pattern = prefix + ".pattern";
            Object kind = c.at(pattern + ".type");
            if (!(kind instanceof String type)) {
                c.fail(pattern + ".type", "expected NONE, ALL, SUFFIX or RUN");
                continue;
            }
            switch (type) {
                case "NONE" -> c.map(pattern);
                case "ALL" -> {
                    c.integer(pattern + ".setting", 1, 6);
                    c.integerList(pattern + ".machine_ids", 1, Long.MAX_VALUE);
                }
                case "SUFFIX" -> {
                    c.integerList(pattern + ".suffixes", 0, 9);
                    c.weights(pattern + ".distribution", SETTINGS, 100L);
                }
                case "RUN" -> {
                    c.integer(pattern + ".run_length", 1, Long.MAX_VALUE);
                    c.integer(pattern + ".run_count", 0, Long.MAX_VALUE);
                    c.weights(pattern + ".distribution", SETTINGS, 100L);
                }
                default -> c.fail(pattern + ".type", "expected NONE, ALL, SUFFIX or RUN");
            }
        }
        c.profileReference("events.default_profile", profiles);
        c.keys("events.weekday", Set.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"));
        for (DayOfWeek day : DayOfWeek.values()) c.profileReference("events.weekday." + day.name(), profiles);
        for (String date : c.map("events.special_dates").keySet()) {
            try { LocalDate.parse(date); } catch (DateTimeParseException exception) { c.fail("events.special_dates." + date, "expected ISO date"); }
            c.profileReference("events.special_dates." + date, profiles);
        }
        return new Result(values, c.errors);
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (!(key instanceof String) && !(key instanceof Integer)) throw new IllegalArgumentException("Invalid mapping key");
            String name = key.toString();
            if (result.containsKey(name)) throw new IllegalArgumentException("Duplicate mapping key: " + name);
            result.put(name, normalize(value));
        });
        return Collections.unmodifiableMap(result);
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) return normalizeMap(map);
        if (value instanceof List<?> list) return Collections.unmodifiableList(list.stream().map(ConfigValidation::normalize).toList());
        return value;
    }

    private static final class Check {
        private final Map<String, Object> values;
        private final List<String> errors = new ArrayList<>();
        Check(Map<String, Object> values) { this.values = values; }
        void fail(String path, String message) { errors.add((path.isEmpty() ? "config" : path) + ": " + message); }

        Object at(String path) {
            Object current = values;
            if (path.isEmpty()) return current;
            for (String key : path.split("\\.")) {
                if (!(current instanceof Map<?, ?> map)) return null;
                current = map.get(key);
            }
            return current;
        }

        @SuppressWarnings("unchecked") Map<String, Object> map(String path) {
            Object value = at(path);
            if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
            fail(path, "expected mapping");
            return Map.of();
        }

        void keys(String path, Set<String> expected) {
            if (!map(path).keySet().equals(expected)) fail(path, "keys must be " + expected);
        }

        BigInteger integer(String path, long min, long max) { return integerValue(path, at(path), min, max); }

        BigInteger integerValue(String path, Object value, long min, long max) {
            if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof BigInteger)) {
                fail(path, "expected integer");
                return BigInteger.ZERO;
            }
            BigInteger number = new BigInteger(value.toString());
            if (number.compareTo(BigInteger.valueOf(min)) < 0 || number.compareTo(BigInteger.valueOf(max)) > 0)
                fail(path, "out of range " + min + ".." + max);
            return number;
        }

        void equalInteger(String path, long expected) { integer(path, expected, expected); }

        void number(String path, BigDecimal min) {
            Object value = at(path);
            try {
                if (!(value instanceof Number) || new BigDecimal(value.toString()).compareTo(min) < 0)
                    fail(path, "expected finite number >= " + min);
            } catch (NumberFormatException exception) { fail(path, "expected finite number"); }
        }

        void numberRange(String path, BigDecimal min, BigDecimal max) {
            number(path, min);
            Object value = at(path);
            if (value instanceof Number) {
                try {
                    if (new BigDecimal(value.toString()).compareTo(max) > 0) fail(path, "must be <= " + max);
                } catch (NumberFormatException exception) {
                    // number() already records non-finite values.
                }
            }
        }

        void weights(String path, Set<String> names, Long sum) {
            keys(path, names);
            BigInteger total = BigInteger.ZERO;
            for (String name : names) total = total.add(integer(path + "." + name, 0, Long.MAX_VALUE));
            if (sum == null ? total.signum() <= 0 : !total.equals(BigInteger.valueOf(sum)))
                fail(path, sum == null ? "sum must be positive" : "sum must be " + sum);
        }

        void integerList(String path, long min, long max) {
            if (!(at(path) instanceof List<?> list)) { fail(path, "expected list"); return; }
            for (int i = 0; i < list.size(); i++) integerValue(path + "[" + i + "]", list.get(i), min, max);
        }

        void profileReference(String path, Map<String, Object> profiles) {
            if (!(at(path) instanceof String name) || !profiles.containsKey(name)) fail(path, "unknown profile");
        }
    }
}
