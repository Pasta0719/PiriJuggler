package jp.pirijuggler.common.reel;

import java.util.List;

/** The approved 1..21 strip. Kept separate from every existing machine strip. */
public final class SkillStopReels {
    public static final int LENGTH = 21;
    private static final List<List<Symbol>> NUMBERED = List.of(
            parse("REPLAY GRAPE BAR CHERRY GRAPE REPLAY GRAPE REPLAY BELL SEVEN PIERO REPLAY GRAPE CHERRY BAR GRAPE REPLAY GRAPE PIERO SEVEN GRAPE"),
            parse("CHERRY GRAPE PIERO REPLAY CHERRY GRAPE BAR REPLAY CHERRY GRAPE REPLAY CHERRY GRAPE PIERO BAR REPLAY CHERRY GRAPE BELL SEVEN REPLAY"),
            parse("BELL REPLAY PIERO GRAPE BELL REPLAY PIERO GRAPE BELL REPLAY PIERO GRAPE BELL REPLAY PIERO GRAPE BELL REPLAY BAR SEVEN GRAPE"));
    public static Symbol numbered(Reel reel, int number) { return NUMBERED.get(reel.ordinal()).get(Math.floorMod(number - 1, LENGTH)); }
    public static int stopIndex(int topNumber) { return Math.floorMod(22 - topNumber, LENGTH); }
    public static int topNumber(int stopIndex) { return Math.floorMod(21 - stopIndex, LENGTH) + 1; }
    public static Symbol at(Reel reel, int index) { return numbered(reel, 21 - Math.floorMod(index, LENGTH)); }
    public static Symbol row(Reel reel, int stopIndex, int row) {
        if (row < -1 || row > 1) throw new IllegalArgumentException("row");
        return at(reel, stopIndex + row);
    }
    private static List<Symbol> parse(String text) { return java.util.Arrays.stream(text.split(" ")).map(Symbol::valueOf).toList(); }
    private SkillStopReels() {}
}
