package jp.pirijuggler.paper.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.random.RandomGenerator;

/**
 * Server-authoritative 100G PIRI CHAIN setting hint. This presentation RNG is
 * independent of gameplay draws and has no effect on hit probabilities or RTP.
 * State lives in the existing per-machine JSON and survives normal reconnects.
 */
public final class JugglerGodSettingHint {
    private static final String PREFIX = "settingHint";
    private static final String[] REGULAR = {"WHITE", "BLUE", "GREEN", "YELLOW"};
    private static final String[] STRONG = {"PURPLE", "RED", "GOLD", "RAINBOW"};
    // Rows are settings 1..6, columns follow REGULAR and STRONG.
    private static final int[][] GOD_REGULAR = {
            {30,27,24,19}, {29,27,24,20}, {27,26,25,22},
            {25,25,26,24}, {23,25,26,26}, {21,23,27,29}
    };
    private static final int[][] EXTREME_REGULAR = {
            {35,29,23,13}, {32,29,24,15}, {28,28,25,19},
            {24,26,27,23}, {20,24,28,28}, {16,22,29,33}
    };
    private static final int[][] STRONG_WEIGHTS = {
            {100,0,0,0}, {100,0,0,0}, {60,40,0,0},
            {40,40,20,0}, {0,70,30,0}, {0,0,90,10}
    };

    public record State(int chain, int games, String color) {
        public State {
            if (chain < 0 || games < 0 || color == null) throw new IllegalArgumentException("hint state");
        }
    }
    private JugglerGodSettingHint() {}

    public static State read(JsonObject json) {
        if (json == null) return new State(0,0,"OFF");
        int chain = json.has(PREFIX+"Chain") ? json.get(PREFIX+"Chain").getAsInt() : 0;
        int games = json.has(PREFIX+"Games") ? json.get(PREFIX+"Games").getAsInt() : 0;
        String color = json.has(PREFIX+"Color") ? json.get(PREFIX+"Color").getAsString() : "OFF";
        return chain < 0 || games < 0 ? new State(0,0,"OFF") : new State(chain,games,color);
    }

    /**
     * Advance only on accepted actions. 'normalStop' is the third reel stop;
     * 'normalHit' is the internal awarded role, not the visual notification.
     * Thus a bonus hit exactly at 100G extends the chain even on delayed告知.
     */
    public static State advance(State prior, boolean countedNormalLever,
                                boolean normalStop, boolean normalHit,
                                boolean bonusStarted, boolean bonusEnded,
                                int setting, boolean extreme, RandomGenerator hintRandom) {
        int chain=prior.chain(), games=prior.games();
        String color=prior.color();
        if (countedNormalLever) {
            games=Math.min(101,games+1);
            // The first lever after a displayed 100G hint (101G) extinguishes it.
            color="OFF";
        }
        if (normalStop && !normalHit && games==100 && chain>0) {
            color=sample(setting,extreme,chain,hintRandom);
            chain=0;
            games=0;
        }
        if (bonusStarted) {
            chain=chain==0?1:Math.addExact(chain,1);
            games=0;
            color="OFF";
        }
        // Count from the actual bonus end, not the bonus-start presentation.
        if (bonusEnded) games=0;
        return new State(chain,games,color);
    }

    /** Keep the three hint fields through runtime normalization and debug updates. */
    public static String preserve(String original,JsonObject normalized) {
        JsonObject old=null;
        if(original!=null&&!original.isBlank()){
            try{old=JsonParser.parseString(original).getAsJsonObject();}
            catch(RuntimeException ignored){old=null;}
        }
        write(normalized,read(old));
        return normalized.toString();
    }

    public static void write(JsonObject target, State state) {
        target.addProperty(PREFIX+"Chain",state.chain());
        target.addProperty(PREFIX+"Games",state.games());
        target.addProperty(PREFIX+"Color",state.color());
    }

    public static String sample(int setting, boolean extreme, int chain, RandomGenerator rng) {
        if (setting<1||setting>6||chain<1) throw new IllegalArgumentException("setting/chain");
        if (chain>=7) return weighted(STRONG,STRONG_WEIGHTS[setting-1],rng);
        // 40% unlit, 60% regular. Every regular color is possible at every setting.
        if (rng.nextInt(100)>=60) return "OFF";
        return weighted(REGULAR,(extreme?EXTREME_REGULAR:GOD_REGULAR)[setting-1],rng);
    }

    private static String weighted(String[] colors,int[] weights,RandomGenerator rng) {
        int roll=rng.nextInt(100);
        for(int i=0;i<weights.length;i++){
            roll-=weights[i];
            if(roll<0)return colors[i];
        }
        throw new IllegalStateException("Invalid setting hint weight row");
    }
}
