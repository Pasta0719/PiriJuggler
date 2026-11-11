package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Paper-owned, cosmetic-only Phase06 decision. Does not consume gameplay RNG,
 * modify awards, or expose final award/flash until the third stop is settled.
 * Stable id/seed permit deterministic re-publication after a packet replay.
 */
public final class HyperDogPresentation {
    public enum Color { WHITE, BLUE, GREEN, YELLOW, PURPLE, RED, RAINBOW }
    private static final long[] MIN={10,20,30,50,100,500,2000};
    private static final int[][] COLOR_WEIGHTS={
        {100,0,0,0,0,0,0},
        {70,30,0,0,0,0,0},
        {55,35,10,0,0,0,0},
        {25,35,30,10,0,0,0},
        {10,15,25,40,10,0,0},
        {5,10,15,40,30,0,0},
        {2,3,10,20,35,30,0},
        {0,0,0,0,5,85,10},
        {2,3,10,20,35,30,0}
    };
    private static final int[][] COUNT_WEIGHTS={
        {45,40,15},{30,45,25},{25,40,35},{15,35,50}
    };
    private final long id,award;
    private final Color finalColor,flashColor;
    private final int[] counts=new int[3];
    private final int revealStage;

    private HyperDogPresentation(long id,long award,Color finalColor,Color flashColor,
                                 int finalCount,boolean firstStopAdds,int revealStage) {
        this.id=id;this.award=award;this.finalColor=finalColor;this.flashColor=flashColor;
        this.revealStage=revealStage;
        counts[0]=1;
        if(finalCount==3){counts[1]=2;counts[2]=3;}
        else if(finalCount==2){counts[1]=firstStopAdds?2:1;counts[2]=2;}
        else{counts[1]=1;counts[2]=1;}
    }

    /** The entropy must be chosen and persisted by Paper, never by the client. */
    public static HyperDogPresentation choose(long eventId,long award,long entropy) {
        if(eventId<=0||award<10)throw new IllegalArgumentException("real new award required");
        SplittableRandom random=new SplittableRandom(entropy);
        int[] colors=COLOR_WEIGHTS[colorBand(award)];
        Color target=Color.values()[draw(colors,random)];
        if(!eligible(target,award))throw new IllegalStateException("guarantee violated");
        int[] counts=COUNT_WEIGHTS[countBand(award)];
        int count=draw(counts,random)+1;
        boolean firstStopAdds=random.nextBoolean();
        int revealStage=draw(new int[]{20,40,40},random);
        Color flash=target;
        if(random.nextInt(100)<20){
            List<Color> higher=new ArrayList<>();
            for(Color candidate:Color.values())
                if(candidate.ordinal()>target.ordinal()&&eligible(candidate,award))higher.add(candidate);
            if(!higher.isEmpty())flash=higher.get(random.nextInt(higher.size()));
        }
        return new HyperDogPresentation(eventId,award,target,flash,count,firstStopAdds,revealStage);
    }
    private static int colorBand(long n){
        if(n<20)return 0;
        if(n<30)return 1;
        if(n<50)return 2;
        if(n<100)return 3;
        if(n<200)return 4;
        if(n<500)return 5;
        if(n<2000)return 6;
        if(n==2000)return 7;
        return 8;
    }
    private static int countBand(long n){return n<50?0:n<200?1:n<500?2:3;}
    private static int draw(int[] weights,SplittableRandom rng){
        int sum=0;for(int w:weights)sum+=w;
        if(sum!=100)throw new IllegalStateException("percentage table");
        int r=rng.nextInt(sum);
        for(int i=0;i<weights.length;i++){r-=weights[i];if(r<0)return i;}
        throw new AssertionError("unreachable");
    }
    public static boolean eligible(Color color,long award){
        if(award<MIN[color.ordinal()])return false;
        return color!=Color.RAINBOW||award==2000;
    }
    public long id(){return id;}
    public long award(){return award;}
    public Color finalColor(){return finalColor;}
    public Color flashColor(){return flashColor;}
    public int finalCount(){return counts[2];}
    public int revealStage(){return revealStage;}
    public int lampCount(int completedStops){
        if(completedStops<0||completedStops>3)throw new IllegalArgumentException("stops");
        return counts[Math.min(2,completedStops)];
    }
    public Color lampColor(int completedStops){
        if(completedStops<0||completedStops>3)throw new IllegalArgumentException("stops");
        return completedStops>=revealStage?finalColor:Color.WHITE;
    }
    /**
     * Third-stop display/flash is only published when the game has committed.
     * The server may replay the same event id without generating a new effect.
     */
    public JsonObject publicStage(int completedStops,boolean committed,long thirdStopEpochMs){
        if(completedStops<0||completedStops>3 || (committed&&completedStops!=3))
            throw new IllegalArgumentException("stopping stage");
        JsonObject j=new JsonObject();
        j.addProperty("presentationId",id);
        j.addProperty("rightLampActive",true);
        j.addProperty("rightLampCount",lampCount(completedStops));
        j.addProperty("rightLampColor",lampColor(completedStops).name());
        j.addProperty("awardRevealed",committed);
        if(committed){
            if(thirdStopEpochMs<0)throw new IllegalArgumentException("timestamp");
            j.addProperty("newAward",award);
            j.addProperty("flashEventId",id);
            j.addProperty("flashColor",flashColor.name());
            j.addProperty("flashAward",award);
            j.addProperty("flashStartedEpochMs",thirdStopEpochMs);
        }
        return j;
    }
}
