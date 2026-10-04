package jp.pirijuggler.common.reel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static jp.pirijuggler.common.reel.Symbol.*;

/** Deterministic controller shared by the plugin, mod and exhaustive acceptance tests. */
public final class SkillStopControl {
    public enum Premium { NONE, A, B, C, D, E, F }
    public enum Mode { NORMAL, PENDING, BONUS_AUTO, CHALLENGE }
    public record Context(SkillStopRole role, String carriedBonus, Premium premium, Mode mode, int challengePattern) {
        public Context {
            Objects.requireNonNull(role); Objects.requireNonNull(premium); Objects.requireNonNull(mode);
            if (carriedBonus != null && !Set.of("BIG", "REG").contains(carriedBonus)) throw new IllegalArgumentException("bonus");
            if (premium != Premium.NONE && (!role.bigFamily() || mode != Mode.NORMAL)) throw new IllegalArgumentException("premium");
            if (premium == Premium.B && role != SkillStopRole.CHERRY_BIG) throw new IllegalArgumentException("premium B");
            if (mode == Mode.CHALLENGE && challengePattern != 4 && challengePattern != 8 && challengePattern != 64) throw new IllegalArgumentException("challenge");
            if (mode == Mode.BONUS_AUTO && role != SkillStopRole.GRAPE && role != SkillStopRole.CHERRY) throw new IllegalArgumentException("bonus display");
        }
        public String bonus() { return carriedBonus == null ? role.bonus() : carriedBonus; }
        public static Context normal(SkillStopRole role, Premium premium) { return new Context(role, null, premium, Mode.NORMAL, 0); }
        public static Context pending(SkillStopRole small, String bonus) { return new Context(small, bonus, Premium.NONE, Mode.PENDING, 0); }
        public static Context challenge(int pattern) { return new Context(SkillStopRole.MISS, null, Premium.NONE, Mode.CHALLENGE, pattern); }
        public static Context bonusDisplay(SkillStopRole role) { return new Context(role, null, Premium.NONE, Mode.BONUS_AUTO, 0); }
    }
    public record Choice(int pressedIndex, int stopIndex, int slip, int durationMs) {}
    public record Outcome(int payout, boolean replay, String entryBonus, boolean challengeSuccess, int patterns) {}
    private record Key(Context context, int mask, int left, int center, int right, int bitMask, boolean entry) {}
    private static final int[][] LINES = {{-1,-1,-1},{0,0,0},{1,1,1},{-1,0,1},{1,0,-1}};
    private static final Symbol[][] PATTERNS = {{REPLAY,REPLAY,REPLAY},{GRAPE,GRAPE,GRAPE},{BELL,BELL,BELL},{PIERO,PIERO,PIERO},
            {SEVEN,SEVEN,SEVEN},{SEVEN,SEVEN,BAR},{BAR,BAR,BAR},{SEVEN,CHERRY,BAR},{BELL,SEVEN,BELL},
            {GRAPE,GRAPE,PIERO},{PIERO,GRAPE,PIERO},{PIERO,PIERO,SEVEN},{PIERO,BAR,PIERO}};
    private static final int[] PATTERN_MASK = new int[9261];
    private static final boolean[] LEFT_MIDDLE_CHERRY = new boolean[21], LEFT_CORNER_CHERRY = new boolean[21];
    static {
        for (int l=0;l<21;l++) {
            LEFT_MIDDLE_CHERRY[l] = SkillStopReels.row(Reel.LEFT,l,0)==CHERRY;
            LEFT_CORNER_CHERRY[l] = SkillStopReels.row(Reel.LEFT,l,-1)==CHERRY || SkillStopReels.row(Reel.LEFT,l,1)==CHERRY;
            for(int m=0;m<21;m++) for(int r=0;r<21;r++) {
                int[] t={l,m,r}; int mask=0;
                for(int p=0;p<PATTERNS.length;p++) for(int[] line:LINES) {
                    boolean match=true;
                    for(int k=0;k<3;k++) if(SkillStopReels.row(Reel.values()[k],t[k],line[k])!=PATTERNS[p][k]) { match=false; break; }
                    if(match) {mask |= 1<<p; break;}
                }
                PATTERN_MASK[l*441+m*21+r]=mask;
            }
        }
    }
    private final Map<Key,int[]> cache = new ConcurrentHashMap<>();
    public Choice choose(Context context, SkillStopHistory history, Reel reel, int pressed) {
        if(pressed<0||pressed>20||(history.mask()&reel.bit())!=0) throw new IllegalArgumentException("STOP");
        int target;
        if(context.mode()==Mode.BONUS_AUTO) target=auto(context,history,reel,pressed);
        else if(history.count()==0) target=first(context,history,reel,pressed);
        else if(history.count()==1) {
            int[] options=second(context,history,reel);
            if(options==null) throw new IllegalStateException("No safe second stop");
            target=options[pressed];
        } else target=last(context,history,reel)[pressed];
        int slip=ReelMotion.slip(target,pressed);
        if(context.mode()!=Mode.BONUS_AUTO && slip>4) throw new IllegalStateException("More than four symbols");
        return new Choice(pressed,target,slip,ReelMotion.durationMs(slip));
    }
    public Outcome outcome(Context context, SkillStopHistory history) {
        if(history.count()!=3) throw new IllegalArgumentException("incomplete");
        int mask=patterns(history.stops());
        if(context.mode()==Mode.CHALLENGE) return new Outcome(14,false,null,(mask&context.challengePattern())!=0,mask);
        if(context.mode()==Mode.BONUS_AUTO) return new Outcome(14,false,null,false,mask);
        boolean entry=(mask & bonusBit(context))!=0;
        if(!legal(context,history.stops(),history.bit(0),entry && entryEligible(context,history))) throw new IllegalStateException("Forbidden final result");
        int pay=(mask&2)!=0?8:(mask&4)!=0?14:(mask&8)!=0?10:0;
        if(context.role().cherry()&&(LEFT_CORNER_CHERRY[history.stop(0)]||LEFT_MIDDLE_CHERRY[history.stop(0)]))pay+=4;
        if(context.role().oneMedal()&&(mask&context.role().pattern())!=0)pay+=1;
        return new Outcome(pay,(mask&1)!=0,entry?context.bonus():null,false,mask);
    }
    private int first(Context c, SkillStopHistory h, Reel reel, int pressed) {
        Key key=new Key(c,0,0,0,reel.ordinal(),0,false);
        int[] choices=cache.get(key);
        if(choices==null){
            int[] answer=new int[21];
            for(int p=0;p<21;p++) {
                int forced=forcedFirst(c,reel,p),pick=-1,onePick=-1;
                for(int d=0;d<=4;d++) {
                    int target=Math.floorMod(p-d,21);
                    if(forced>=0&&target!=forced)continue;
                    if(!leftPartial(c,reel,target,d==0))continue;
                    SkillStopHistory next=h.append(reel,p,target); boolean safe=true;
                    for(Reel other:Reel.values())if(other!=reel&&second(c,next,other)==null){safe=false;break;}
                    if(!safe)continue;
                    if(pick<0)pick=target;
                    if(c.role().oneMedal()&&onePick<0&&onePossible(c,next))onePick=target;
                    if(d==0&&c.role().oneMedal()&&showsSeven(reel,p)){pick=target;onePick=target;break;}
                }
                if(pick<0)throw new IllegalStateException("No first stop: "+c+" "+reel+" "+p);
                answer[p]=onePick>=0?onePick:pick;
            }
            choices=answer;cache.put(key,answer);
        }
        return choices[pressed];
    }
    /** Public stop laws override generic bonus retention in pending and premium games. */
    public static int forcedFirst(Context c, Reel reel, int pressed) {
        if(c.mode()==Mode.CHALLENGE||c.mode()==Mode.BONUS_AUTO)return -1;
        int top=SkillStopReels.topNumber(pressed);
        if(reel==Reel.CENTER&&(top==20||top==21)) {
            int t=c.role()==SkillStopRole.GRAPE?2:c.role()==SkillStopRole.PIERO||c.role()==SkillStopRole.PIERO_BIG||c.role()==SkillStopRole.PIERO_REG?3:
                    c.role().cherry()?21:c.role()==SkillStopRole.REPLAY?1:c.bonus()!=null||c.role()==SkillStopRole.BELL?top:1;
            return SkillStopReels.stopIndex(t);
        }
        if(reel==Reel.RIGHT&&top==21) {
            int t=c.role()==SkillStopRole.BELL?1:c.role()==SkillStopRole.PIERO||c.role()==SkillStopRole.PIERO_BIG||c.role()==SkillStopRole.PIERO_REG?3:
                    c.bonus()!=null||c.role()==SkillStopRole.GRAPE?21:1;
            return SkillStopReels.stopIndex(t);
        }
        return -1;
    }
    private int[] second(Context c, SkillStopHistory h, Reel reel) {
        Key key=key(c,h,reel.ordinal());
        int[] cached=cache.get(key);if(cached!=null)return cached[0]<0?null:cached;
        int[] answer=new int[21];
        for(int p=0;p<21;p++) {
            int pick=-1,onePick=-1;
            for(int d=0;d<=4;d++) {
                int target=Math.floorMod(p-d,21);
                if(!leftPartial(c,reel,target,d==0))continue;
                SkillStopHistory next=h.append(reel,p,target);
                if(c.premium()==Premium.F&&sevenTenpai(next)!=0)continue;
                Reel last=Arrays.stream(Reel.values()).filter(r->(next.mask()&r.bit())==0).findFirst().orElseThrow();
                int[] finalChoices=lastOrNull(c,next,last);if(finalChoices==null)continue;
                if(pick<0)pick=target;
                if(c.role().oneMedal()&&onePick<0&&onePossible(c,next))onePick=target;
                if(d==0&&entryEligible(c,next)&&sevenTenpai(next)!=0){pick=target;onePick=target;break;}
            }
            if(pick<0){cache.put(key,new int[]{-1});return null;}
            answer[p]=onePick>=0?onePick:pick;
        }
        cache.put(key,answer);return answer;
    }
    private int[] last(Context c, SkillStopHistory h, Reel reel) {
        int[] options=lastOrNull(c,h,reel);
        if(options==null)throw new IllegalStateException("No legal last stop");return options;
    }
    private int[] lastOrNull(Context c, SkillStopHistory h, Reel reel) {
        Key key=key(c,h,reel.ordinal());
        int[] cached=cache.get(key);if(cached!=null)return cached[0]<0?null:cached;
        int[] answer=new int[21]; int[] tops=h.stops(); boolean entry=entryEligible(c,h);
        for(int p=0;p<21;p++) {
            int pick=-1,onePick=-1;
            for(int d=0;d<=4;d++) {
                int target=Math.floorMod(p-d,21);tops[reel.ordinal()]=target;
                boolean leftBit=reel==Reel.LEFT?d==0:h.bit(0);
                if(!legal(c,tops,leftBit,entry&&d==0))continue;
                if(pick<0)pick=target;
                int mask=patterns(tops);
                if(d==0&&entry&&(mask&bonusBit(c))!=0){pick=target;onePick=target;break;}
                if(c.role().oneMedal()&&onePick<0&&(mask&c.role().pattern())!=0)onePick=target;
            }
            if(pick<0){cache.put(key,new int[]{-1});return null;}
            answer[p]=onePick>=0?onePick:pick;
        }
        cache.put(key,answer);return answer;
    }
    private boolean onePossible(Context c, SkillStopHistory h) {
        if(!c.role().oneMedal())return false;
        int[] t=h.stops();
        for(int l=0;l<21;l++)for(int m=0;m<21;m++)for(int r=0;r<21;r++) {
            if(t[0]>=0&&t[0]!=l||t[1]>=0&&t[1]!=m||t[2]>=0&&t[2]!=r)continue;
            int[] test={l,m,r};
            if((patterns(test)&c.role().pattern())!=0&&legal(c,test,h.bit(0),false))return true;
        }
        return false;
    }
    private static boolean legal(Context c, int[] t, boolean leftBit, boolean entry) {
        int mask=patterns(t);
        if(c.mode()==Mode.CHALLENGE)return (mask&48)==0;
        if(LEFT_MIDDLE_CHERRY[t[0]]&&!(c.premium()==Premium.B&&leftBit))return false;
        if(LEFT_CORNER_CHERRY[t[0]]&&!c.role().cherry())return false;
        if((mask&~(c.role().pattern()|(entry?bonusBit(c):0)))!=0)return false;
        // A drawn standalone small role must actually appear on a visible payline.
        // Do not force this during pending bonus entry or premium overlap games.
        if(c.mode()==Mode.NORMAL && c.bonus()==null){
            if(c.role()==SkillStopRole.REPLAY && (mask&1)==0)return false;
            if(c.role()==SkillStopRole.GRAPE && (mask&2)==0)return false;
            if(c.role()==SkillStopRole.BELL && (mask&4)==0)return false;
            if(c.role()==SkillStopRole.PIERO && (mask&8)==0)return false;
            if(c.role()==SkillStopRole.CHERRY && !LEFT_MIDDLE_CHERRY[t[0]] && !LEFT_CORNER_CHERRY[t[0]])return false;
        }
        return true;
    }
    private static boolean leftPartial(Context c, Reel reel, int target, boolean bit) {
        return c.mode()==Mode.CHALLENGE||reel!=Reel.LEFT||
                (!LEFT_MIDDLE_CHERRY[target]||c.premium()==Premium.B&&bit)&&(!LEFT_CORNER_CHERRY[target]||c.role().cherry());
    }
    private static boolean entryEligible(Context c, SkillStopHistory h) {
        if(c.bonus()==null||c.premium()==Premium.F||h.count()==0)return false;
        int first=h.first();
        if(!h.bit(first)&&forcedFirst(c,Reel.values()[first],h.input(first))!=h.stop(first))return false;
        for(int r:h.order())if(r!=first&&!h.bit(r))return false;
        return true;
    }
    private static int bonusBit(Context c) { return "BIG".equals(c.bonus())?16:"REG".equals(c.bonus())?32:0; }
    private static int patterns(int[] t) { return PATTERN_MASK[t[0]*441+t[1]*21+t[2]]; }
    public static int sevenTenpai(SkillStopHistory h) {
        if(h.count()!=2)return 0;int count=0;
        for(int[] line:LINES) {boolean yes=true;for(int r:h.order())if(SkillStopReels.row(Reel.values()[r],h.stop(r),line[r])!=SEVEN){yes=false;break;}if(yes)count++;}
        return count;
    }
    private static boolean showsSeven(Reel reel,int stop) {for(int row=-1;row<=1;row++)if(SkillStopReels.row(reel,stop,row)==SEVEN)return true;return false;}
    private static Key key(Context c, SkillStopHistory h,int last) {
        int bits=0;for(int r:h.order())if(h.bit(r))bits|=1<<r;
        // The stopped mask identifies the remaining reel(s); first eligibility preserves input history.
        return new Key(c,h.mask(),h.stop(0),h.stop(1),h.stop(2),bits|(last<<3),entryEligible(c,h));
    }
    private int auto(Context c, SkillStopHistory h, Reel reel, int pressed) {
        for(int d=0;d<21;d++) {
            int target=Math.floorMod(pressed-d,21);SkillStopHistory next=h.append(reel,pressed,target);int[] t=next.stops();
            for(int l=0;l<21;l++)for(int m=0;m<21;m++)for(int r=0;r<21;r++) {
                if(t[0]>=0&&t[0]!=l||t[1]>=0&&t[1]!=m||t[2]>=0&&t[2]!=r)continue;
                int[] test={l,m,r};int mask=patterns(test);
                boolean win=c.role()==SkillStopRole.GRAPE?(mask&2)!=0:LEFT_CORNER_CHERRY[l];
                if(win&&legal(c,test,false,false))return target;
            }
        }
        throw new IllegalStateException("No bonus display");
    }
}
