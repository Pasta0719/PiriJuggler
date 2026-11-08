package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.*;
import jp.pirijuggler.common.reel.HyperDogReelControl;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Server-only, independently seeded NORMAL stage of HYPER_DOG.
 * Does not use existing JUGGLER paylines, RNG, GameEngine or machine economy.
 * Phase04 will own AT and economy integration; this core never fabricates an AT payout.
 */
public final class HyperDogNormalGame {
    public enum Mode { LOW_A, LOW_B, NORMAL_A, NORMAL_B, HIGH, SUPER_HIGH }
    public enum Role { MISS, REPLAY, GRAPE, WEAK_CHERRY, WATERMELON, CHANCE,
                       STRONG_CHERRY, STRONG_CHANCE, BAR }
    public enum Hit { NONE, SINGLE, LINKED, BAR }
    public record Outcome(long serial, Role role, int bet, int payout, Mode modeBefore,
                          Mode modeAfter, String trigger, boolean singleHit, boolean linkedHit,
                          Hit hit, Mode savedEntryMode) {
        public Outcome {
            Objects.requireNonNull(role);Objects.requireNonNull(modeBefore);
            Objects.requireNonNull(modeAfter);Objects.requireNonNull(hit);
            if(serial<=0||bet<0||payout<0)throw new IllegalArgumentException("outcome");
        }
        public boolean entersAt(){return hit!=Hit.NONE;}
        public HyperDogReelControl.Role reelRole(){
            return HyperDogReelControl.Role.valueOf(role.name());
        }
    }
    private static final String MODEL_SHA256="af1ae6f88e6593116692f987534c406673fbbfa73cb25f52615a7f1674c20ab3";
    private static final JsonObject MODEL=loadModel();
    private static final JsonObject NORMAL=MODEL.getAsJsonObject("normal_mode");
    private static final JsonObject COMMON=MODEL.getAsJsonObject("normal_and_at_common_role_draw");
    private static final JsonObject PAY=MODEL.getAsJsonObject("fixed_requirements")
            .getAsJsonObject("payout_regular_or_at");
    private static final Role[] DRAW_ORDER=Role.values();
    private static final long WEYL=0x9e3779b97f4a7c15L;
    private final int setting;
    private long rngState;
    private Mode mode, savedHitMode;
    private Role streakRole;
    private int streak, replayStreak, grapeStreak, weakStreak, watermelonStreak;
    private boolean replayFree, postedBet, waitingForAt;
    private int postedBetAmount;
    private long credits, in, out, games;
    private Outcome pending;

    public HyperDogNormalGame(int setting,long seed,long initialCredits){
        if(setting<1||setting>6||initialCredits<0)throw new IllegalArgumentException("setting/credit");
        this.setting=setting;credits=initialCredits;rngState=seed;
        mode=choose(NORMAL.getAsJsonObject("startup_initial_mode_weights_percent"));
        savedHitMode=mode;
    }
    private HyperDogNormalGame(int setting,long seed){this.setting=setting;this.rngState=seed;}

    /** No client, packet, screenshot or remote registry may receive this snapshot. */
    public JsonObject snapshot(){
        JsonObject j=new JsonObject();
        j.addProperty("schema",1);j.addProperty("setting",setting);
        j.addProperty("rngState",Long.toUnsignedString(rngState));
        j.addProperty("mode",mode.name());j.addProperty("savedHitMode",savedHitMode.name());
        j.addProperty("streakRole",streakRole==null?null:streakRole.name());
        j.addProperty("streak",streak);j.addProperty("replayStreak",replayStreak);
        j.addProperty("grapeStreak",grapeStreak);j.addProperty("weakStreak",weakStreak);
        j.addProperty("watermelonStreak",watermelonStreak);
        j.addProperty("replayFree",replayFree);j.addProperty("postedBet",postedBet);
        j.addProperty("postedBetAmount",postedBetAmount);
        j.addProperty("waitingForAt",waitingForAt);
        j.addProperty("credits",credits);j.addProperty("in",in);j.addProperty("out",out);
        j.addProperty("games",games);
        if(pending!=null)j.add("pending",outcomeJson(pending));
        return j;
    }
    public static HyperDogNormalGame restore(JsonObject saved){
        if(saved==null || saved.get("schema").getAsInt()!=1)throw new IllegalArgumentException("schema");
        int setting=saved.get("setting").getAsInt();
        if(setting<1||setting>6)throw new IllegalArgumentException("setting");
        HyperDogNormalGame g=new HyperDogNormalGame(setting,
                Long.parseUnsignedLong(saved.get("rngState").getAsString()));
        g.mode=Mode.valueOf(saved.get("mode").getAsString());
        g.savedHitMode=Mode.valueOf(saved.get("savedHitMode").getAsString());
        g.streakRole=saved.get("streakRole").isJsonNull()?null:Role.valueOf(saved.get("streakRole").getAsString());
        g.streak=saved.get("streak").getAsInt();g.replayStreak=saved.get("replayStreak").getAsInt();
        g.grapeStreak=saved.get("grapeStreak").getAsInt();g.weakStreak=saved.get("weakStreak").getAsInt();
        g.watermelonStreak=saved.get("watermelonStreak").getAsInt();
        g.replayFree=saved.get("replayFree").getAsBoolean();
        g.postedBet=saved.get("postedBet").getAsBoolean();
        g.postedBetAmount=saved.get("postedBetAmount").getAsInt();
        g.waitingForAt=saved.get("waitingForAt").getAsBoolean();
        g.credits=saved.get("credits").getAsLong();g.in=saved.get("in").getAsLong();
        g.out=saved.get("out").getAsLong();g.games=saved.get("games").getAsLong();
        if(saved.has("pending"))g.pending=outcomeFromJson(saved.getAsJsonObject("pending"));
        if(g.credits<0||g.in<0||g.out<0||g.games<0 || g.streak<0 || g.streak>Integer.MAX_VALUE/2 ||
           g.postedBetAmount<0||g.postedBetAmount>3||
           g.postedBet && (g.replayFree||g.postedBetAmount!=3||g.pending!=null||g.waitingForAt)||
           g.pending!=null && (g.postedBet||g.waitingForAt||g.pending.serial()!=g.games+1)||
           g.waitingForAt&&g.replayFree)
            throw new IllegalArgumentException("corrupt HYPER_DOG normal snapshot");
        return g;
    }
    public static String parameterSha256(){return MODEL_SHA256;}
    public int setting(){return setting;}
    public Mode mode(){return mode;}
    public Mode savedHitMode(){return savedHitMode;}
    public boolean replayFree(){return replayFree;}
    public boolean waitingForAt(){return waitingForAt;}
    public boolean betPosted(){return postedBet;}
    public long credits(){return credits;}
    public long games(){return games;}
    public long totalBet(){return in;}
    public long totalPayout(){return out;}
    public int consecutive(Role role){return switch(role){
        case REPLAY->replayStreak; case GRAPE->grapeStreak;
        case WEAK_CHERRY->weakStreak;case WATERMELON->watermelonStreak;
        default->streakRole==role?streak:0;
    };}
    public Outcome pending(){return pending;}
    public boolean canBet(){return pending==null&&!waitingForAt&&!postedBet&&!replayFree&&credits>=3;}
    public boolean canLever(){return pending==null&&!waitingForAt&&(postedBet||replayFree);}

    /** Distinct BET and lever transitions, like existing physical slot machines. */
    public void bet(){
        if(!canBet())throw new IllegalStateException("BET denied");
        credits=Math.subtractExact(credits,3);
        in=Math.addExact(in,3);
        postedBet=true;postedBetAmount=3;
    }
    public Outcome lever(){return leverInternal(null);}
    /** Non-public deterministic role injection only accessible to package-local unit tests. */
    Outcome leverForced(Role forcedRole){return leverInternal(Objects.requireNonNull(forcedRole));}
    private Outcome leverInternal(Role forcedRole){
        if(!canLever())throw new IllegalStateException("lever denied");
        int bet=postedBet?postedBetAmount:0;
        postedBet=false;postedBetAmount=0;replayFree=false;
        Role role=forcedRole==null?drawRole():forcedRole;
        applyStreak(role);
        String event=event(role);
        Mode before=mode;
        double chance=Math.min(.95,
                NORMAL.getAsJsonObject("single_hit_probability_multiplier_by_setting")
                    .get(Integer.toString(setting)).getAsDouble()/
                NORMAL.getAsJsonObject("base_single_at_hit_denominator_by_mode")
                    .get(before.name()).getAsDouble());
        boolean single=roll(chance);
        double linkedProbability=event==null?0:percent(NORMAL.getAsJsonObject("role_linked_at_hit_percent"),event);
        boolean linked=event!=null&&roll(linkedProbability);
        JsonObject selectors=NORMAL.getAsJsonObject("mode_up_table_selector");
        if(event!=null && selectors.has(event)){
            if(roll(percent(NORMAL.getAsJsonObject("mode_up_roll_percent_by_event"),event))){
                String tier=selectors.get(event).getAsString();
                mode=choose(NORMAL.getAsJsonObject("mode_up_destination_weights_percent_by_tier")
                        .getAsJsonObject(tier).getAsJsonObject(mode.name()));
            }
        } else if(role==Role.MISS) {
            JsonObject drop=NORMAL.getAsJsonObject("miss_mode_drop").getAsJsonObject(mode.name());
            if(roll(drop.get("probability_on_miss").getAsDouble())){
                mode=choose(drop.getAsJsonObject("destinations"));
            }
        }
        Hit hit=role==Role.BAR?Hit.BAR:single?Hit.SINGLE:linked?Hit.LINKED:Hit.NONE;
        Mode originalAtEntry=hit==Hit.NONE?null:mode;
        pending=new Outcome(games+1,role,bet,payout(role),before,mode,
                event==null?"NONE":event,single,linked,hit,originalAtEntry);
        return pending;
    }
    /** Must be called exactly once after physical third stop, not at lever. */
    public Outcome finish(){
        if(pending==null)throw new IllegalStateException("no unfinished spin");
        Outcome finished=pending;
        out=Math.addExact(out,finished.payout());
        credits=Math.addExact(credits,finished.payout());
        games=Math.addExact(games,1);
        pending=null;
        replayFree=finished.role()==Role.REPLAY;
        if(finished.entersAt()){
            waitingForAt=true;savedHitMode=finished.savedEntryMode();
            replayFree=false;streakRole=null;streak=0;
            replayStreak=grapeStreak=weakStreak=watermelonStreak=0;
        }
        return finished;
    }
    /** Phase04 handoff: only return-to-normal distribution is applied here; no AT awards. */
    public Mode resumeAfterAt(){
        if(!waitingForAt||pending!=null)throw new IllegalStateException("no completed AT entry");
        mode=choose(NORMAL.getAsJsonObject("at_exit_mode_weights_percent_by_saved_at_entry_mode")
                .getAsJsonObject(savedHitMode.name()));
        waitingForAt=false;replayFree=false;
        return mode;
    }
    /** Safe client projection: never includes mode, RNG, role, AT saved mode or streaks. */
    public JsonObject publicState(){
        JsonObject j=new JsonObject();
        j.addProperty("gameState",pending!=null?"NORMAL_SPINNING":
                waitingForAt?"AT_PENDING":postedBet?"NORMAL_BETTED":replayFree?"REPLAY_READY":"SEATED_READY");
        j.addProperty("credit",credits);
        j.addProperty("bet",postedBet?3:pending==null?0:pending.bet());
        j.addProperty("pay",0);
        j.addProperty("gameCount",games);
        return j;
    }
    private void applyStreak(Role role){
        if(role==streakRole)streak=Math.addExact(streak,1);
        else{streakRole=role;streak=1;}
        replayStreak=role==Role.REPLAY?streak:0;
        grapeStreak=role==Role.GRAPE?streak:0;
        weakStreak=role==Role.WEAK_CHERRY?streak:0;
        watermelonStreak=role==Role.WATERMELON?streak:0;
    }
    private String event(Role role){
        return switch(role){
            case REPLAY -> replayStreak>=3?"REPLAY_STREAK_"+Math.min(replayStreak,5):null;
            case GRAPE -> grapeStreak>=3?"GRAPE_STREAK_"+Math.min(grapeStreak,5):null;
            case WEAK_CHERRY -> weakStreak>=2?"WEAK_CHERRY_2PLUS_CONSECUTIVE":"WEAK_CHERRY_SINGLE";
            case WATERMELON -> watermelonStreak>=2?"WATERMELON_2PLUS_CONSECUTIVE":"WATERMELON_SINGLE";
            case STRONG_CHERRY,CHANCE,STRONG_CHANCE -> role.name();
            default -> null;
        };
    }
    private Role drawRole(){
        double u=nextDouble();
        double acc=0;
        for(Role r:DRAW_ORDER){
            double p;
            if(r==Role.MISS)p=COMMON.getAsJsonObject("miss_probability_per_game").get("NORMAL").getAsDouble();
            else if(r==Role.GRAPE)p=COMMON.getAsJsonObject("grape_probability_per_game").get("NORMAL").getAsDouble();
            else p=COMMON.getAsJsonObject("exclusive_base_role_probability_per_game").get(r.name()).getAsDouble();
            acc+=p;
            if(u<acc)return r;
        }
        return Role.MISS; // rounding protection only
    }
    public static int payout(Role role){return PAY.getAsJsonObject(role.name()).get("NORMAL").getAsInt();}
    public static double singleHitChance(Mode mode,int setting) {
        if(setting<1||setting>6)throw new IllegalArgumentException("setting");
        return Math.min(.95,NORMAL.getAsJsonObject("single_hit_probability_multiplier_by_setting")
                .get(Integer.toString(setting)).getAsDouble()/
                NORMAL.getAsJsonObject("base_single_at_hit_denominator_by_mode").get(mode.name()).getAsDouble());
    }
    private static double percent(JsonObject table,String key){
        return table.has(key)?table.get(key).getAsDouble()/100.:0.;
    }
    private boolean roll(double probability){return nextDouble()<probability;}
    private Mode choose(JsonObject weights){
        if(weights==null||weights.size()==0)throw new IllegalArgumentException("empty mode transition");
        double total=0;
        for(Mode m:Mode.values())if(weights.has(m.name()))total+=weights.get(m.name()).getAsDouble();
        if(Math.abs(total-100)>1e-8)throw new IllegalStateException("mode weights !=100: "+total);
        double x=nextDouble()*100,acc=0;
        for(Mode m:Mode.values()){
            if(weights.has(m.name())){
                acc+=weights.get(m.name()).getAsDouble();
                if(x<acc)return m;
            }
        }
        return Mode.SUPER_HIGH;
    }
    private double nextDouble(){
        rngState+=WEYL;
        long z=rngState;
        z=(z^(z>>>30))*0xbf58476d1ce4e5b9L;
        z=(z^(z>>>27))*0x94d049bb133111ebL;
        z=z^(z>>>31);
        return (z>>>11)*0x1.0p-53;
    }
    private static JsonObject outcomeJson(Outcome o){
        JsonObject j=new JsonObject();
        j.addProperty("serial",o.serial());j.addProperty("role",o.role().name());
        j.addProperty("bet",o.bet());j.addProperty("payout",o.payout());
        j.addProperty("modeBefore",o.modeBefore().name());
        j.addProperty("modeAfter",o.modeAfter().name());
        j.addProperty("trigger",o.trigger());
        j.addProperty("singleHit",o.singleHit());j.addProperty("linkedHit",o.linkedHit());
        j.addProperty("hit",o.hit().name());
        j.addProperty("savedEntryMode",o.savedEntryMode()==null?null:o.savedEntryMode().name());
        return j;
    }
    private static Outcome outcomeFromJson(JsonObject j){
        return new Outcome(j.get("serial").getAsLong(),Role.valueOf(j.get("role").getAsString()),
                j.get("bet").getAsInt(),j.get("payout").getAsInt(),
                Mode.valueOf(j.get("modeBefore").getAsString()),Mode.valueOf(j.get("modeAfter").getAsString()),
                j.get("trigger").getAsString(),j.get("singleHit").getAsBoolean(),
                j.get("linkedHit").getAsBoolean(),Hit.valueOf(j.get("hit").getAsString()),
                j.get("savedEntryMode").isJsonNull()?null:Mode.valueOf(j.get("savedEntryMode").getAsString()));
    }
    private static JsonObject loadModel(){
        try(InputStream stream=HyperDogNormalGame.class.getClassLoader().getResourceAsStream("hyperdog/v5.json")){
            if(stream==null)throw new IllegalStateException("HYPER_DOG v5 model not packaged");
            byte[] bytes=stream.readAllBytes();
            StringBuilder sha=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))
                sha.append(String.format(Locale.ROOT,"%02x",b&255));
            if(!MODEL_SHA256.equals(sha.toString()))throw new IllegalStateException("HYPER_DOG v5 sha mismatch");
            return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
}
