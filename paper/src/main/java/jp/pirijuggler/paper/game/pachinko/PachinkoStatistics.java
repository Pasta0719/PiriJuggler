package jp.pirijuggler.paper.game.pachinko;

import com.google.gson.JsonObject;

/** Durable Phase 09 counters. Reset is explicit by replacing with empty(). */
public record PachinkoStatistics(
        long initialJackpots,long initial450,long initial1500,long rushEntries,
        long rightWins,long right1500,long right3000
) {
    public PachinkoStatistics {
        if(initialJackpots<0||initial450<0||initial1500<0||rushEntries<0||rightWins<0||right1500<0||right3000<0)
            throw new IllegalArgumentException("negative pachinko statistics");
    }
    public static PachinkoStatistics empty(){return new PachinkoStatistics(0,0,0,0,0,0,0);}
    public PachinkoStatistics initial(boolean rush){return new PachinkoStatistics(initialJackpots+1,initial450+(rush?0:1),initial1500+(rush?1:0),rushEntries+(rush?1:0),rightWins,right1500,right3000);}
    public PachinkoStatistics right(PachinkoRuntime.RightOutcome o){
        if(o==PachinkoRuntime.RightOutcome.OUT||o==PachinkoRuntime.RightOutcome.NONE)return this;
        return new PachinkoStatistics(initialJackpots,initial450,initial1500,rushEntries,rightWins+1,right1500+(o==PachinkoRuntime.RightOutcome.WIN_1500?1:0),right3000+(o==PachinkoRuntime.RightOutcome.WIN_3000?1:0));
    }
    JsonObject toJson(){var o=new JsonObject();o.addProperty("initialJackpots",initialJackpots);o.addProperty("initial450",initial450);o.addProperty("initial1500",initial1500);o.addProperty("rushEntries",rushEntries);o.addProperty("rightWins",rightWins);o.addProperty("right1500",right1500);o.addProperty("right3000",right3000);return o;}
    static PachinkoStatistics fromJson(JsonObject o){if(o==null)return empty();return new PachinkoStatistics(v(o,"initialJackpots"),v(o,"initial450"),v(o,"initial1500"),v(o,"rushEntries"),v(o,"rightWins"),v(o,"right1500"),v(o,"right3000"));}
    private static long v(JsonObject o,String k){return o.has(k)?o.get(k).getAsLong():0;}
}
