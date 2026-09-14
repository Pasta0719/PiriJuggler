package jp.pirijuggler.paper.database;

import jp.pirijuggler.paper.machine.Machine;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/** Startup allocation is part of the atomic business-period transaction. */
public record StartupProfile(String name, String source, Map<String, Object> definition) {
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
    public static StartupProfile resolve(Map<String, Object> config, LocalDate day, String next) {
        var events = map(config.get("events"));
        var dates = map(events.get("special_dates"));
        var weekdays = map(events.get("weekday"));
        String source = next != null ? "MANUAL_NEXT" : dates.containsKey(day.toString()) ? "SPECIAL_DATE"
                : weekdays.containsKey(day.getDayOfWeek().name()) ? "WEEKDAY" : "DEFAULT";
        String name = next != null ? next : (String) dates.getOrDefault(day.toString(),
                weekdays.getOrDefault(day.getDayOfWeek().name(), events.get("default_profile")));
        Object definition = map(events.get("profiles")).get(name);
        if (definition == null) throw new IllegalArgumentException("Unknown startup profile: " + name);
        return new StartupProfile(name, source, map(definition));
    }
    public String reason() {
        return name.equals("normal") && map(definition.get("pattern")).get("type").equals("NONE")
                && (source.equals("DEFAULT") || source.equals("WEEKDAY")) ? "SERVER_START" : "EVENT";
    }
    public Map<Integer, Integer> allocate(List<Machine> machines, RandomGenerator rng, Consumer<String> warning) {
        List<Integer> ids = machines.stream().filter(m -> !m.deleted() && m.enabled() && m.autoSetting())
                .map(Machine::id).sorted().toList();
        Map<Integer, Integer> result = new LinkedHashMap<>();
        var pattern = map(definition.get("pattern"));
        String type = (String) pattern.get("type");
        if (type.equals("ALL")) {
            var selected = (List<?>) pattern.get("machine_ids");
            for (int id : ids) if (selected.isEmpty() || contains(selected, id)) result.put(id, integer(pattern, "setting"));
        } else if (type.equals("SUFFIX")) {
            for (int id : ids) if (contains((List<?>) pattern.get("suffixes"), id % 10))
                result.put(id, draw(map(pattern.get("distribution")), rng));
        } else if (type.equals("RUN")) {
            int length = integer(pattern, "run_length");
            int count = Math.min(integer(pattern, "run_count"), ids.size() / length);
            if (count < integer(pattern, "run_count")) warning.accept("RUN count clamped to " + count);
            // Select disjoint runs with a compressed stars-and-bars representation. Every
            // selection has the maximum requested count; greedy interval choices cannot strand gaps.
            List<Integer> choices = new ArrayList<>();
            for (int i = 0; i < ids.size() - count * (length - 1); i++) choices.add(i);
            List<Integer> starts = new ArrayList<>();
            for (int i = 0; i < count; i++) starts.add(choices.remove(rng.nextInt(choices.size())));
            Collections.sort(starts);
            for (int run = 0; run < count; run++) for (int j = 0; j < length; j++) {
                int id = ids.get(starts.get(run) + run * (length - 1) + j);
                result.put(id, draw(map(pattern.get("distribution")), rng));
            }
        }
        for (int id : ids) result.computeIfAbsent(id, unused -> draw(map(definition.get("distribution")), rng));
        Set<Integer> corrected = new HashSet<>();
        guarantee(result, corrected, 6, integer(definition, "min_setting_6"), rng, warning);
        guarantee(result, corrected, 5, integer(definition, "min_setting_5_plus"), rng, warning);
        return result;
    }
    private static void guarantee(Map<Integer, Integer> values, Set<Integer> corrected, int target, int minimum,
                                  RandomGenerator rng, Consumer<String> warning) {
        int count = Math.min(minimum, values.size());
        if (count < minimum) warning.accept("Setting " + target + " guarantee clamped to " + count);
        while (values.values().stream().filter(v -> v >= target).count() < count) {
            int low = values.entrySet().stream().filter(e -> !corrected.contains(e.getKey()) && e.getValue() < target)
                    .mapToInt(Map.Entry::getValue).min().orElseThrow();
            List<Integer> candidates = values.entrySet().stream().filter(e -> e.getValue() == low && !corrected.contains(e.getKey()))
                    .map(Map.Entry::getKey).toList();
            int id = candidates.get(rng.nextInt(candidates.size())); values.put(id, target); corrected.add(id);
        }
    }
    private static boolean contains(List<?> values, int target) { return values.stream().anyMatch(v -> ((Number) v).intValue() == target); }
    private static int integer(Map<String, Object> map, String key) { return ((Number) map.get(key)).intValue(); }
    private static int draw(Map<String, Object> distribution, RandomGenerator rng) {
        int ticket = rng.nextInt(100);
        for (int setting = 1; setting <= 6; setting++) { ticket -= ((Number) distribution.get(Integer.toString(setting))).intValue(); if (ticket < 0) return setting; }
        throw new IllegalArgumentException("Distribution must sum to 100");
    }
}
