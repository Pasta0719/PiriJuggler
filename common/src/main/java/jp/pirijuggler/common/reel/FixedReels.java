package jp.pirijuggler.common.reel;
import java.util.List;
import static jp.pirijuggler.common.reel.Symbol.*;
/** SPEC §4: compiled constants; no config/resource-pack override. */
public final class FixedReels {
    public static final int LENGTH=21;
    private static final List<List<Symbol>> REELS=List.of(
        List.of(GRAPE,REPLAY,GRAPE,SEVEN,PIERO,GRAPE,REPLAY,GRAPE,CHERRY,BAR,GRAPE,REPLAY,GRAPE,BELL,SEVEN,REPLAY,GRAPE,REPLAY,GRAPE,BAR,CHERRY),
        List.of(CHERRY,PIERO,REPLAY,SEVEN,GRAPE,CHERRY,REPLAY,BELL,GRAPE,CHERRY,REPLAY,BAR,GRAPE,CHERRY,REPLAY,BELL,GRAPE,CHERRY,REPLAY,BAR,GRAPE),
        List.of(BELL,REPLAY,GRAPE,SEVEN,BAR,BELL,REPLAY,GRAPE,PIERO,BELL,REPLAY,GRAPE,PIERO,BELL,REPLAY,GRAPE,PIERO,BELL,REPLAY,GRAPE,PIERO));
    public static List<Symbol> sequence(Reel reel){return REELS.get(reel.ordinal());}
    public static Symbol at(Reel reel,int index){return sequence(reel).get(Math.floorMod(index,LENGTH));}
    public static Symbol row(Reel reel,int stopIndex,int row){if(row < -1 || row > 1)throw new IllegalArgumentException("Row must be -1, 0 or 1");return at(reel,stopIndex+row);}
    private FixedReels(){}
}
