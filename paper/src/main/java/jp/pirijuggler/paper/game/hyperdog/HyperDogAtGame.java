package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * HYPER_DOG Phase04 authoritative AT/CZ economy.
 * Only Paper owns this instance and its private snapshot. One completed spin
 * is accounted for exactly once; unfinished spins can be saved and restored.
 */
public final class HyperDogAtGame {
    public enum Stage { RED7_CONFIRM, LOWER_INITIAL, LOWER_AT, UPPER_INITIAL, UPPER_AT, UPPER_CZ, COMPLETE }
    public enum Role { MISS, REPLAY, GRAPE, WEAK_CHERRY, WATERMELON, CHANCE, STRONG_CHERRY, STRONG_CHANCE, BAR }
    public record Round(long serial, Stage stage, Role role, int bet, int payout,
                        long award, int ticket, boolean premium, boolean czSuccess) {
        public boolean special() { return stage==Stage.LOWER_INITIAL || stage==Stage.UPPER_INITIAL || stage==Stage.UPPER_CZ; }
    }
    private static final String MODEL_HASH="af1ae6f88e6593116692f987534c406673fbbfa73cb25f52615a7f1674c20ab3";
    private static final JsonObject MODEL=loadModel();
    private static final JsonObject FIX=MODEL.getAsJsonObject("fixed_requirements");
    private static final JsonObject COMMON=MODEL.getAsJsonObject("normal_and_at_common_role_draw");
    private static final JsonObject LOWER=MODEL.getAsJsonObject("lower_at");
    private static final JsonObject UPPER=MODEL.getAsJsonObject("upper_at");
    private static final JsonObject CZ=MODEL.getAsJsonObject("upper_cz");
    private static final long WEYL=0x9e3779b97f4a7c15L;

    private final int setting;
    private long rng, credit, totalIn, totalOut, paidGames, freeGames, serial;
    private Stage stage;
    private long remaining, upperInitialTotal, lowerInitialTotal, totalBonuses, atNet;
    private int tickets, upperBarCharges, futureRareCharges, forcedRareSlots;
    private int initialIndex, czGame, czForcedGame, rareStreak, specialStreak;
    private boolean czFromUpper, barGuaranteed, replayFree, betPosted;
    private Role streakRole, specialStreakRole;
    private int postedBet;
    private Round pending;

    public HyperDogAtGame(int setting, long seed, long credit, long totalIn, long totalOut,
                          boolean barDirect) {
        if(setting<1||setting>6||credit<0||totalIn<0||totalOut<0)
            throw new IllegalArgumentException("invalid AT opening ledger");
        this.setting=setting;this.rng=seed;this.credit=credit;
        this.totalIn=totalIn;this.totalOut=totalOut;
        this.remaining=barDirect?MODEL.getAsJsonObject("bar_special").getAsJsonObject("normal_direct_upper")
                .get("dedicated_bonus_medals").getAsLong():0L;
        this.stage=Stage.RED7_CONFIRM;
        this.initialIndex=barDirect?-1:0; // -1 marks direct-upper entry before confirmation
    }
    private HyperDogAtGame(int setting) { this.setting=setting; }
    public static String parameterSha256(){return MODEL_HASH;}
    public Stage stage(){return stage;}
    public long remaining(){return remaining;}
    public long credit(){return credit;}
    public long totalIn(){return totalIn;}
    public long totalOut(){return totalOut;}
    public long paidGames(){return paidGames;}
    public long freeGames(){return freeGames;}
    public long upperInitialTotal(){return upperInitialTotal;}
    public long lowerInitialTotal(){return lowerInitialTotal;}
    public long totalBonuses(){return totalBonuses;}
    public long atNet(){return atNet;}
    public int czTickets(){return tickets;}
    public int barCharges(){return upperBarCharges;}
    public boolean replayFree(){return replayFree;}
    public boolean waitingForBet(){return (stage==Stage.LOWER_AT||stage==Stage.UPPER_AT)
            &&pending==null&&!betPosted&&!replayFree;}
    public boolean canLever(){return pending==null&&(stage==Stage.LOWER_INITIAL||stage==Stage.UPPER_INITIAL||
            stage==Stage.UPPER_CZ||((stage==Stage.LOWER_AT||stage==Stage.UPPER_AT)&&(betPosted||replayFree)));}
    public Round pending(){return pending;}
    public int czGame(){return czGame;}
    public long serial(){return serial;}
    public String rngState(){return Long.toUnsignedString(rng);}

    /** AT red-seven confirmation is a non-paying presentation, never a game. */
    public void confirmRed7(){
        if(stage!=Stage.RED7_CONFIRM||pending!=null)throw new IllegalStateException("red7 confirmation state");
        stage=initialIndex<0?Stage.UPPER_INITIAL:Stage.LOWER_INITIAL;
        initialIndex=0;
    }
    public void bet(){
        if(!waitingForBet()||credit<3)throw new IllegalStateException("BET denied");
        long nextCredit=Math.subtractExact(credit,3);
        long nextIn=Math.addExact(totalIn,3);
        credit=nextCredit;totalIn=nextIn;postedBet=true;postedBet=3;
    }
    public Round lever(){return leverForced(null);}
    /** Role override for deterministic package-local tests; never expose to a network packet. */
    Round leverForced(Role forced){
        if(!canLever())throw new IllegalStateException("lever denied");
        HyperDogAtGame next=restore(snapshot());
        Round round=next.calculate(forced);
        copyFrom(next);
        return round;
    }
    private Round calculate(Role forced) {
        Stage source=stage;
        int bet=postedBet?postedBet:0;
        if(source==Stage.LOWER_AT||source==Stage.UPPER_AT) {
            if(!betPosted&&!replayFree)throw new IllegalStateException("paid bet missing");
        }else if(bet!=0||betPosted||replayFree)throw new IllegalStateException("special stage used BET");
        postedBet=0;betPosted=false;replayFree=false;
        String profile=switch(source){
            case LOWER_AT -> "LOWER_AT";
            case UPPER_AT,UPPER_INITIAL -> "UPPER_AT";
            default -> "NORMAL";
        };
        Role role=forced!=null?forced:draw(profile);
        if(source==Stage.LOWER_INITIAL||source==Stage.UPPER_INITIAL||source==Stage.UPPER_CZ) {
            if(role==Role.BAR) {
                if(forced!=null)throw new IllegalArgumentException("BAR not possible in a free stage");
                role=Role.MISS;
            }
        }
        if(source==Stage.UPPER_AT && role==Role.MISS
                && chance(COMMON.getAsJsonObject("upper_miss_to_rare_role_rewrite")
                        .get("conditional_probability_on_miss").getAsDouble()))
            role=Role.valueOf(pickText(COMMON.getAsJsonObject("upper_miss_to_rare_role_rewrite")
                    .getAsJsonObject("destination_percent")));
        if(source==Stage.UPPER_INITIAL) {
            if(initialIndex<Math.min(5,forcedRareSlots)) {
                Role rare=Role.valueOf(pickText(UPPER.getAsJsonObject("initial")
                        .getAsJsonObject("forced_rare_slot_distribution_percent")));
                if(rank(rare)>rank(role))role=rare;
            }
            if(forcedRareSlots>5&&initialIndex<Math.min(5,forcedRareSlots-5)){
                Role strong=Role.valueOf(pickText(UPPER.getAsJsonObject("initial")
                        .getAsJsonObject("overflow_forced_strong_distribution_percent")));
                if(rank(strong)>rank(role))role=strong;
            }
        }
        boolean paid=source==Stage.LOWER_AT||source==Stage.UPPER_AT;
        int payout=paid?FIX.getAsJsonObject("payout_regular_or_at")
                .getAsJsonObject(role.name()).get(profile).getAsInt():0;
        int streak;
        if(source==Stage.UPPER_INITIAL) {
            if(role==specialStreakRole) specialStreak=Math.addExact(specialStreak,1);
            else {specialStreakRole=role;specialStreak=1;}
            streak=specialStreak;
        } else if(paid) {
            if(role==streakRole)rareStreak=Math.addExact(rareStreak,1);
            else {streakRole=role;rareStreak=1;}
            streak=rareStreak;
        } else streak=0;
        long award=0;
        int ticket=0;
        boolean premium=false,czSuccess=false;
        if(source==Stage.LOWER_INITIAL){
            JsonObject initial=LOWER.getAsJsonObject("initial");
            JsonObject bonuses=initial.getAsJsonObject("role_bonus_medals_weights_percent");
            award=Math.addExact(initial.get("base_medals").getAsLong(),
                    pickNumber(bonuses.getAsJsonObject(role.name())));
            award=Math.addExact(award,pickNumber(initial.getAsJsonObject("independent_extra_medals_weights_percent")));
        }else if(source==Stage.UPPER_INITIAL||source==Stage.LOWER_AT||source==Stage.UPPER_AT){
            boolean upper=source==Stage.UPPER_INITIAL||source==Stage.UPPER_AT;
            if(role!=Role.BAR) {
                if(upper && (role==Role.STRONG_CHERRY||role==Role.STRONG_CHANCE)) {
                    String key=role==Role.STRONG_CHERRY?"strong_cherry_2000_override_percent":"strong_chance_2000_override_percent";
                    premium=chance(UPPER.getAsJsonObject("regular_addons").get(key).getAsDouble()/100.0);
                }
                if(premium)award=2000;
                else {
                    JsonObject table=LOWER.getAsJsonObject("rare_role_addon_weights_percent");
                    if(role==Role.WEAK_CHERRY||role==Role.WATERMELON){
                        award=pickNumber(table.getAsJsonObject(role.name()));
                        if(streak>=2)award=Math.addExact(award,
                            pickNumber(table.getAsJsonObject(role.name()+"_SECOND_CONSECUTIVE_EXTRA")));
                    } else if(role==Role.STRONG_CHERRY||role==Role.CHANCE){
                        JsonObject branches=table.getAsJsonObject(role.name());
                        boolean czBranch=chance(branches.getAsJsonObject("cz_award")
                                .get("probability_percent").getAsDouble()/100.);
                        award=pickNumber(branches.getAsJsonObject(czBranch?"cz_award":"ordinary")
                                .getAsJsonObject("medal_weights"));
                        if(czBranch && !upper)ticket=1;
                    } else if(role==Role.STRONG_CHANCE){
                        award=pickNumber(table.getAsJsonObject("STRONG_CHANCE"));
                        if(!upper)ticket=1;
                    }
                    if(upper)award=Math.multiplyExact(award,5);
                }
            }
            if(source==Stage.UPPER_INITIAL)
                award=Math.min(2000,Math.addExact(100,award));
        }else if(source==Stage.UPPER_CZ) {
            czSuccess=chance(CZ.get("independent_p_success_each_game").getAsDouble()) ||
                    (czForcedGame>0 && czGame+1>=czForcedGame);
        }
        pending=new Round(Math.addExact(serial,1),source,role,bet,payout,award,ticket,premium,czSuccess);
        return pending;
    }
    /**
     * Commit a prepared reel outcome only after the third physical stop.
     * Calculate and validate a full copy before assigning it to this instance.
     */
    public Round finish(){
        if(pending==null)throw new IllegalStateException("unfinished spin missing");
        HyperDogAtGame next=restore(snapshot());
        Round settled=next.finishUnchecked();
        copyFrom(next);
        return settled;
    }
    private Round finishUnchecked(){
        Round result=pending;
        Stage source=result.stage();
        long newCredit=Math.addExact(credit,result.payout());
        long newOut=Math.addExact(totalOut,result.payout());
        long newSerial=Math.addExact(serial,1);
        long newBalance=remaining, newBonuses=totalBonuses, newNet=atNet;
        long newPaid=paidGames,newFree=freeGames;
        if(source==Stage.LOWER_AT||source==Stage.UPPER_AT){
            newPaid=Math.addExact(newPaid,1);
            long net=(long)result.payout()-result.bet();
            newNet=Math.addExact(newNet,net);
            newBalance=Math.subtractExact(newBalance,net);
        }else newFree=Math.addExact(newFree,1);
        if(result.award()!=0){
            newBalance=Math.addExact(newBalance,result.award());
            newBonuses=Math.addExact(newBonuses,result.award());
        }
        // All ledger arithmetic above must succeed before any mutation.
        credit=newCredit;totalOut=newOut;serial=newSerial;remaining=newBalance;
        totalBonuses=newBonuses;atNet=newNet;paidGames=newPaid;freeGames=newFree;
        pending=null;
        if(source==Stage.LOWER_INITIAL){
            lowerInitialTotal=result.award();stage=Stage.LOWER_AT;resetPaidBoundary();
        }else if(source==Stage.UPPER_INITIAL){
            upperInitialTotal=Math.addExact(upperInitialTotal,result.award());
            initialIndex++;
            if(initialIndex==5){stage=Stage.UPPER_AT;forcedRareSlots=0;resetPaidBoundary();}
        }else if(source==Stage.UPPER_CZ) {
            czGame++;
            if(result.czSuccess()){
                if(czFromUpper){
                    forcedRareSlots=Math.addExact(upperBarCharges,futureRareCharges);
                    futureRareCharges=0;
                }else forcedRareSlots=Math.max(0,tickets-2);
                tickets=0;barGuaranteed=false;upperBarCharges=0;
                startUpperInitial();
            }else if(czGame==5){
                tickets=0;barGuaranteed=false;upperBarCharges=0;futureRareCharges=0;
                if(czFromUpper){stage=Stage.COMPLETE;resetPaidBoundary();}
                else {stage=Stage.LOWER_INITIAL;resetPaidBoundary();}
            }
        }else if(source==Stage.LOWER_AT||source==Stage.UPPER_AT){
            replayFree=result.role()==Role.REPLAY;
            if(result.role()==Role.BAR){
                if(source==Stage.LOWER_AT){
                    if(tickets>0){
                        barGuaranteed=true;
                        futureRareCharges=Math.addExact(futureRareCharges,tickets-1);
                        tickets=0;
                    }
                    remaining=Math.addExact(remaining,MODEL.getAsJsonObject("bar_special")
                            .getAsJsonObject("normal_direct_upper").get("dedicated_bonus_medals").getAsLong());
                    forcedRareSlots=0;
                    startUpperInitial();
                    return result;
                }
                barGuaranteed=true;
                upperBarCharges=Math.addExact(upperBarCharges,1);
            }
            if(source==Stage.LOWER_AT)tickets=Math.addExact(tickets,result.ticket());
            if(remaining<=0){
                if(source==Stage.UPPER_AT||tickets>0)
                    startCz(source==Stage.UPPER_AT);
                else {stage=Stage.COMPLETE;resetPaidBoundary();}
            }
        }
        return result;
    }
    private void startCz(boolean fromUpper){
        czFromUpper=fromUpper;stage=Stage.UPPER_CZ;czGame=0;
        boolean guaranteed=fromUpper?barGuaranteed:tickets>=2;
        czForcedGame=guaranteed?(int)pickNumber(CZ.getAsJsonObject("guaranteed_success_game_weights_percent")):0;
        resetPaidBoundary();
    }
    private void startUpperInitial(){
        stage=Stage.UPPER_INITIAL;initialIndex=0;upperInitialTotal=0;
        specialStreak=0;specialStreakRole=null;resetPaidBoundary();
    }
    private void resetPaidBoundary(){
        postedBet=0;betPosted=false;replayFree=false;
        rareStreak=0;streakRole=null;
    }
    private Role draw(String profile){
        double u=nextDouble(),acc=0;
        for(Role role:Role.values()){
            double prob=switch(role){
                case MISS -> COMMON.getAsJsonObject("miss_probability_per_game").get(profile).getAsDouble();
                case GRAPE -> COMMON.getAsJsonObject("grape_probability_per_game").get(profile).getAsDouble();
                default -> COMMON.getAsJsonObject("exclusive_base_role_probability_per_game")
                        .get(role.name()).getAsDouble();
            };
            acc+=prob;
            if(u<acc)return role;
        }
        return Role.MISS;
    }
    private static int rank(Role role){return switch(role){
        case WEAK_CHERRY -> 1;case WATERMELON -> 2;case CHANCE -> 3;
        case STRONG_CHERRY -> 4;case STRONG_CHANCE -> 5;default -> 0;
    };}
    private long pickNumber(JsonObject weights){return Long.parseLong(pickText(weights));}
    private String pickText(JsonObject weights){
        if(weights==null||weights.size()==0)throw new IllegalArgumentException("empty lottery");
        double total=0;for(var e:weights.entrySet())total+=e.getValue().getAsDouble();
        if(Math.abs(total-100)>1e-7)throw new IllegalStateException("bad lottery total "+total);
        double x=nextDouble()*100,acc=0;String last=null;
        for(var e:weights.entrySet()){last=e.getKey();acc+=e.getValue().getAsDouble();if(x<acc)return e.getKey();}
        return Objects.requireNonNull(last);
    }
    private boolean chance(double probability){
        if(probability<0||probability>1)throw new IllegalArgumentException("chance");
        return nextDouble()<probability;
    }
    private double nextDouble(){
        rng+=WEYL;long z=rng;
        z=(z^(z>>>30))*0xbf58476d1ce4e5b9L;
        z=(z^(z>>>27))*0x94d049bb133111ebL;
        return ((z^(z>>>31))>>>11)*0x1.0p-53;
    }
    /** Private storage only: never serialize this object directly to Fabric. */
    public JsonObject snapshot(){
        JsonObject j=new JsonObject();
        j.addProperty("schema",1);j.addProperty("setting",setting);
        j.addProperty("rng",Long.toUnsignedString(rng));j.addProperty("stage",stage.name());
        for(var e:Map.ofEntries(Map.entry("credit",credit),Map.entry("in",totalIn),
                Map.entry("out",totalOut),Map.entry("paid",paidGames),Map.entry("free",freeGames),
                Map.entry("serial",serial),Map.entry("remaining",remaining),
                Map.entry("upperInitialTotal",upperInitialTotal),Map.entry("lowerInitialTotal",lowerInitialTotal),
                Map.entry("bonuses",totalBonuses),Map.entry("atNet",atNet)).entrySet())
            j.addProperty(e.getKey(),e.getValue());
        j.addProperty("tickets",tickets);j.addProperty("upperBarCharges",upperBarCharges);
        j.addProperty("futureRareCharges",futureRareCharges);j.addProperty("forcedRareSlots",forcedRareSlots);
        j.addProperty("initialIndex",initialIndex);j.addProperty("czGame",czGame);
        j.addProperty("czForcedGame",czForcedGame);j.addProperty("rareStreak",rareStreak);
        j.addProperty("specialStreak",specialStreak);j.addProperty("czFromUpper",czFromUpper);
        j.addProperty("barGuaranteed",barGuaranteed);j.addProperty("replayFree",replayFree);
        j.addProperty("betPosted",betPosted);j.addProperty("postedBet",postedBet);
        j.addProperty("streakRole",streakRole==null?null:streakRole.name());
        j.addProperty("specialStreakRole",specialStreakRole==null?null:specialStreakRole.name());
        if(pending!=null){
            JsonObject p=new JsonObject();
            p.addProperty("serial",pending.serial());p.addProperty("stage",pending.stage().name());
            p.addProperty("role",pending.role().name());p.addProperty("bet",pending.bet());
            p.addProperty("payout",pending.payout());p.addProperty("award",pending.award());
            p.addProperty("ticket",pending.ticket());p.addProperty("premium",pending.premium());
            p.addProperty("czSuccess",pending.czSuccess());j.add("pending",p);
        }
        return j;
    }
    public static HyperDogAtGame restore(JsonObject j){
        if(j==null||j.get("schema").getAsInt()!=1)throw new IllegalArgumentException("schema");
        int setting=j.get("setting").getAsInt();
        if(setting<1||setting>6)throw new IllegalArgumentException("setting");
        HyperDogAtGame a=new HyperDogAtGame(setting);
        a.rng=Long.parseUnsignedLong(j.get("rng").getAsString());
        a.stage=Stage.valueOf(j.get("stage").getAsString());
        a.credit=j.get("credit").getAsLong();a.totalIn=j.get("in").getAsLong();
        a.totalOut=j.get("out").getAsLong();a.paidGames=j.get("paid").getAsLong();
        a.freeGames=j.get("free").getAsLong();a.serial=j.get("serial").getAsLong();
        a.remaining=j.get("remaining").getAsLong();
        a.upperInitialTotal=j.get("upperInitialTotal").getAsLong();
        a.lowerInitialTotal=j.get("lowerInitialTotal").getAsLong();
        a.totalBonuses=j.get("bonuses").getAsLong();a.atNet=j.get("atNet").getAsLong();
        a.tickets=j.get("tickets").getAsInt();a.upperBarCharges=j.get("upperBarCharges").getAsInt();
        a.futureRareCharges=j.get("futureRareCharges").getAsInt();
        a.forcedRareSlots=j.get("forcedRareSlots").getAsInt();
        a.initialIndex=j.get("initialIndex").getAsInt();a.czGame=j.get("czGame").getAsInt();
        a.czForcedGame=j.get("czForcedGame").getAsInt();a.rareStreak=j.get("rareStreak").getAsInt();
        a.specialStreak=j.get("specialStreak").getAsInt();
        a.czFromUpper=j.get("czFromUpper").getAsBoolean();
        a.barGuaranteed=j.get("barGuaranteed").getAsBoolean();
        a.replayFree=j.get("replayFree").getAsBoolean();
        a.betPosted=j.get("betPosted").getAsBoolean();a.postedBet=j.get("postedBet").getAsInt();
        a.streakRole=j.get("streakRole").isJsonNull()?null:Role.valueOf(j.get("streakRole").getAsString());
        a.specialStreakRole=j.get("specialStreakRole").isJsonNull()?null:Role.valueOf(j.get("specialStreakRole").getAsString());
        if(j.has("pending")){
            JsonObject p=j.getAsJsonObject("pending");
            a.pending=new Round(p.get("serial").getAsLong(),Stage.valueOf(p.get("stage").getAsString()),
                Role.valueOf(p.get("role").getAsString()),p.get("bet").getAsInt(),p.get("payout").getAsInt(),
                p.get("award").getAsLong(),p.get("ticket").getAsInt(),
                p.get("premium").getAsBoolean(),p.get("czSuccess").getAsBoolean());
        }
        if(a.credit<0||a.totalIn<0||a.totalOut<0||a.paidGames<0||a.freeGames<0||
           a.serial<0||a.totalBonuses<0||a.tickets<0||a.upperBarCharges<0||
           a.futureRareCharges<0||a.forcedRareSlots<0||a.forcedRareSlots>Integer.MAX_VALUE/2||
           a.initialIndex< -1||a.initialIndex>5||a.czGame<0||a.czGame>5||
           a.czForcedGame<0||a.czForcedGame>5||a.postedBet<0||a.postedBet>3||
           a.betPosted!=(a.postedBet==3)||a.pending!=null&&
                (a.pending.stage()!=a.stage||a.pending.serial()!=a.serial+1||a.betPosted)||
           a.betPosted&&(a.replayFree||a.stage!=Stage.LOWER_AT&&a.stage!=Stage.UPPER_AT)||
           a.replayFree&&(a.stage!=Stage.LOWER_AT&&a.stage!=Stage.UPPER_AT))
            throw new IllegalArgumentException("invalid AT snapshot");
        return a;
    }
    private void copyFrom(HyperDogAtGame x){
        rng=x.rng;credit=x.credit;totalIn=x.totalIn;totalOut=x.totalOut;paidGames=x.paidGames;
        freeGames=x.freeGames;serial=x.serial;stage=x.stage;remaining=x.remaining;
        upperInitialTotal=x.upperInitialTotal;lowerInitialTotal=x.lowerInitialTotal;
        totalBonuses=x.totalBonuses;atNet=x.atNet;tickets=x.tickets;upperBarCharges=x.upperBarCharges;
        futureRareCharges=x.futureRareCharges;forcedRareSlots=x.forcedRareSlots;
        initialIndex=x.initialIndex;czGame=x.czGame;czForcedGame=x.czForcedGame;
        rareStreak=x.rareStreak;specialStreak=x.specialStreak;
        czFromUpper=x.czFromUpper;barGuaranteed=x.barGuaranteed;replayFree=x.replayFree;
        betPosted=x.betPosted;postedBet=x.postedBet;streakRole=x.streakRole;
        specialStreakRole=x.specialStreakRole;pending=x.pending;
    }
    private static JsonObject loadModel(){
        try(InputStream in=HyperDogAtGame.class.getClassLoader().getResourceAsStream("hyperdog/v5.json")){
            if(in==null)throw new IllegalStateException("v5 model not found");
            String data=new String(in.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
            StringBuilder hex=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)))
                hex.append(String.format(Locale.ROOT,"%02x",b&255));
            if(!MODEL_HASH.equals(hex.toString()))throw new IllegalStateException("v5 hash mismatch");
            return JsonParser.parseString(data).getAsJsonObject();
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
}
