package jp.pirijuggler.common.reel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** HYPER_DOG-only physical reels and authoritative stop solver; never changes an existing machine. */
public final class HyperDogReelControl {
    public static final int LENGTH = 21;
    public enum Icon { RED7, RAINBOW7, BAR, GRAPE, REPLAY, CHERRY, WATERMELON }
    public enum Role { MISS, REPLAY, GRAPE, WATERMELON, WEAK_CHERRY, STRONG_CHERRY,
        CHANCE, STRONG_CHANCE, BAR, AT_ENTRY_RED7, CZ_WIN, CZ_LOSE }
    public record Choice(int pressedIndex, int stopIndex, int slip, int durationMs) {}

    private static final Icon[][] STRIPS = {
        parse("RED7 GRAPE REPLAY RAINBOW7 CHERRY GRAPE REPLAY WATERMELON CHERRY BAR GRAPE REPLAY WATERMELON RAINBOW7 GRAPE REPLAY WATERMELON RED7 GRAPE REPLAY BAR"),
        parse("WATERMELON RED7 GRAPE REPLAY CHERRY BAR GRAPE REPLAY BAR RAINBOW7 GRAPE REPLAY CHERRY CHERRY GRAPE REPLAY RED7 RAINBOW7 WATERMELON GRAPE REPLAY"),
        parse("BAR GRAPE REPLAY RAINBOW7 CHERRY GRAPE REPLAY BAR RAINBOW7 GRAPE REPLAY WATERMELON CHERRY GRAPE REPLAY RED7 WATERMELON GRAPE REPLAY CHERRY RED7")
    };
    private static final int[][] LINES = {{-1,-1,-1},{0,0,0},{1,1,1},{-1,0,1},{1,0,-1}};
    private static final int GRAPE=1, REPLAY=2, WATERMELON=4, CHERRY=8, BAR=16,
        RAINBOW7=32, RED7=64, CHANCE=128, STRONG_CHANCE=256,
        WEAK_CHERRY=512, STRONG_CHERRY=1024, AT_ENTRY_RED7=2048;
    private static final EnumMap<Role, BitSet> LEGAL = new EnumMap<>(Role.class);
    private final ConcurrentHashMap<Integer, Boolean> safeMemo = new ConcurrentHashMap<>();

    static {
        for (Icon[] strip : STRIPS) if (strip.length != LENGTH) throw new ExceptionInInitializerError("reel length");
        for (Role role : Role.values()) LEGAL.put(role, new BitSet(LENGTH * LENGTH * LENGTH));
        for (int l=0;l<LENGTH;l++) for (int m=0;m<LENGTH;m++) for (int r=0;r<LENGTH;r++) {
            int[] stops={l,m,r};
            int flags=marks(stops);
            int id=id(stops);
            for (Role role : Role.values()) if (legal(role,stops,flags)) LEGAL.get(role).set(id);
        }
        for (Role role : Role.values()) if (LEGAL.get(role).isEmpty())
            throw new ExceptionInInitializerError("No physical stops for "+role);
    }

    private static Icon[] parse(String strip) {
        return Arrays.stream(strip.split(" ")).map(Icon::valueOf).toArray(Icon[]::new);
    }
    public static Icon icon(Reel reel,int index) {
        Objects.requireNonNull(reel);
        return STRIPS[reel.ordinal()][Math.floorMod(index,LENGTH)];
    }
    /** A stopIndex is the MIDDLE row. -1 is TOP, +1 BOTTOM, also at the strip seam. */
    public static Icon visible(Reel reel,int middleStop,int offset) {
        if (middleStop<0||middleStop>=LENGTH||offset<-1||offset>1)throw new IllegalArgumentException("visible");
        return icon(reel,middleStop+offset);
    }
    public static int legalTriplets(Role role) {return LEGAL.get(Objects.requireNonNull(role)).cardinality();}
    public static boolean legalFinal(Role role,int[] stops) {
        if (stops==null||stops.length!=3)throw new IllegalArgumentException("stops");
        for (int s:stops)if(s<0||s>=LENGTH)throw new IllegalArgumentException("stops");
        return LEGAL.get(Objects.requireNonNull(role)).get(id(stops));
    }
    public static int payout(Role role,boolean inAt) {
        return switch (role) {
            case MISS, REPLAY, AT_ENTRY_RED7, CZ_WIN, CZ_LOSE -> 0;
            case GRAPE -> inAt?30:3;
            case WEAK_CHERRY, STRONG_CHERRY -> 1;
            case WATERMELON, CHANCE, STRONG_CHANCE -> 5;
            case BAR -> 15;
        };
    }
    public Choice choose(Role role,SkillStopHistory history,Reel reel,int pressedIndex) {
        Objects.requireNonNull(role);Objects.requireNonNull(history);Objects.requireNonNull(reel);
        if (pressedIndex<0||pressedIndex>=LENGTH||(history.mask()&reel.bit())!=0)
            throw new IllegalArgumentException("stopping conditions");
        int maxSlip=maxSlip(role),i=reel.ordinal();
        int[] stops=history.stops();
        for (int slip=0;slip<=maxSlip;slip++) {
            int target=Math.floorMod(pressedIndex-slip,LENGTH);
            stops[i]=target;
            if (safeFromAnyFutureInput(role,stops))
                return new Choice(pressedIndex,target,slip,ReelMotion.durationMs(slip));
        }
        throw new IllegalStateException("No legal stop: "+role+" "+history+" "+reel+" "+pressedIndex);
    }
    public static int maxSlip(Role role) {
        Objects.requireNonNull(role);
        return role==Role.MISS||role==Role.GRAPE||role==Role.REPLAY?4:20;
    }
    private boolean safeFromAnyFutureInput(Role role,int[] stops) {
        int key=role.ordinal()*10648+(stops[0]+1)*484+(stops[1]+1)*22+stops[2]+1;
        Boolean cached=safeMemo.get(key);
        if(cached!=null)return cached;
        int unstopped=0;
        for(int s:stops)if(s<0)unstopped++;
        if(unstopped==0) return LEGAL.get(role).get(id(stops));
        boolean safe=true;
        int max=maxSlip(role);
        for(int reel=0;reel<3&&safe;reel++) {
            if(stops[reel]>=0)continue;
            for(int pressed=0;pressed<LENGTH&&safe;pressed++) {
                boolean possible=false;
                for(int slip=0;slip<=max;slip++) {
                    stops[reel]=Math.floorMod(pressed-slip,LENGTH);
                    if(safeFromAnyFutureInput(role,stops)) { possible=true;break; }
                }
                stops[reel]=-1;
                if(!possible)safe=false;
            }
        }
        safeMemo.putIfAbsent(key,safe);
        return safe;
    }
    private static int id(int[] s) {return s[0]*441+s[1]*21+s[2];}
    private static boolean middle(int[] s,Icon a,Icon b,Icon c) {
        return STRIPS[0][s[0]]==a&&STRIPS[1][s[1]]==b&&STRIPS[2][s[2]]==c;
    }
    private static int marks(int[] s) {
        int flags=0;
        for (int[] row:LINES) {
            Icon a=STRIPS[0][Math.floorMod(s[0]+row[0],LENGTH)];
            if (a!=STRIPS[1][Math.floorMod(s[1]+row[1],LENGTH)] ||
                a!=STRIPS[2][Math.floorMod(s[2]+row[2],LENGTH)])continue;
            flags|=switch(a) {
                case GRAPE -> GRAPE; case REPLAY -> REPLAY; case WATERMELON -> WATERMELON;
                case CHERRY -> CHERRY; case BAR -> BAR; case RAINBOW7 -> RAINBOW7; case RED7 -> RED7;
            };
        }
        Icon left=STRIPS[0][s[0]],center=STRIPS[1][s[1]],right=STRIPS[2][s[2]];
        if(left==Icon.RED7&&center==Icon.BAR&&right==Icon.RED7) flags|=CHANCE;
        if(left==Icon.RED7&&center==Icon.BAR&&right==Icon.RAINBOW7) flags|=STRONG_CHANCE;
        if(left==Icon.CHERRY&&center==Icon.CHERRY&&right!=Icon.CHERRY)flags|=WEAK_CHERRY;
        if(middle(s,Icon.CHERRY,Icon.CHERRY,Icon.CHERRY))flags|=STRONG_CHERRY;
        if(middle(s,Icon.RED7,Icon.RED7,Icon.RED7))flags|=AT_ENTRY_RED7;
        return flags;
    }
    private static boolean legal(Role role,int[] s,int flags) {
        return switch(role) {
            case MISS -> flags==0;
            case GRAPE -> flags==GRAPE;
            case REPLAY -> flags==REPLAY;
            case WATERMELON -> flags==WATERMELON;
            case WEAK_CHERRY -> flags==WEAK_CHERRY;
            case STRONG_CHERRY -> flags==(STRONG_CHERRY|CHERRY);
            case CHANCE -> flags==CHANCE;
            case STRONG_CHANCE -> flags==STRONG_CHANCE;
            case BAR -> flags==BAR&&middle(s,Icon.BAR,Icon.BAR,Icon.BAR);
            case AT_ENTRY_RED7 -> flags==(AT_ENTRY_RED7|RED7)&&middle(s,Icon.RED7,Icon.RED7,Icon.RED7);
            case CZ_WIN -> flags==RAINBOW7&&middle(s,Icon.RAINBOW7,Icon.RAINBOW7,Icon.RAINBOW7);
            case CZ_LOSE -> flags==0&&STRIPS[0][s[0]]==Icon.RAINBOW7&&STRIPS[2][s[2]]==Icon.RAINBOW7&&STRIPS[1][s[1]]!=Icon.RAINBOW7;
        };
    }
    public HyperDogReelControl(){}
}
