package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.*;
import java.util.*;

/**
 * Transaction-sized owner of NORMAL -> AT -> comeback transitions. Does not
 * register an unfinished HYPER_DOG cabinet in the live machine dispatcher.
 * Persist snapshot() together with authoritative reel stop history after each
 * accepted player transition, using the Phase04 SQLite state store.
 */
public final class HyperDogFullGame {
    private static final double COMEBACK_P=0.006948979383631482; // approved v5 JSON
    private HyperDogNormalGame normal;
    private HyperDogAtGame at;
    private int comebackPaidLeft;
    private long completedAtCount;
    private long lastAtNet;
    private boolean lastExtraComeback;
    private boolean comebackFromUpper;

    public HyperDogFullGame(int setting,long seed,long credits) {
        normal=new HyperDogNormalGame(setting,seed,credits);
    }
    private HyperDogFullGame() {}

    public String phase() {
        return at!=null?at.stage().name():comebackPaidLeft>0?"COMEBACK":"NORMAL";
    }
    public long credit(){return at==null?normal.credits():at.credit();}
    public long totalIn(){return at==null?normal.totalBet():at.totalIn();}
    public long totalOut(){return at==null?normal.totalPayout():at.totalOut();}
    public int comebackPaidLeft(){return comebackPaidLeft;}
    public long completedAtCount(){return completedAtCount;}
    public long lastAtNet(){return lastAtNet;}
    public boolean lastExtraComeback(){return lastExtraComeback;}
    public boolean comebackFromUpper(){return comebackFromUpper;}
    public boolean waitingForBet(){return at==null?normal.canBet():at.waitingForBet();}
    public boolean canLever(){return at==null?normal.canLever():at.canLever();}
    public HyperDogNormalGame normal(){return normal;}
    public HyperDogAtGame at(){return at;}

    public void bet(){
        if(at==null)normal.bet();
        else at.bet();
    }
    public void confirmRed7(){
        if(at==null)throw new IllegalStateException("not in AT");
        at.confirmRed7();
    }
    public int stopRainbowSeven(int reel){
        if(at==null)throw new IllegalStateException("no active AT");
        return at.stopRainbowSeven(reel);
    }
    public String lever(){
        if(at==null)return normal.lever().role().name();
        return at.lever().role().name();
    }
    String leverForcedNormal(HyperDogNormalGame.Role role){
        if(at!=null)throw new IllegalStateException("normal only");
        return normal.leverForced(role).role().name();
    }
    /** Internal testing seam. No role override is available on client APIs. */
    String leverForced(HyperDogAtGame.Role role){
        if(at==null)throw new IllegalStateException("AT only");
        return at.leverForced(role).role().name();
    }
    /**
     * Called exactly once per real physical third stop. AT entry confirmation
     * occurs afterwards and consumes no paid/free games.
     */
    public void finish(){
        if(at!=null){
            at.finish();
            if(at.stage()==HyperDogAtGame.Stage.COMPLETE)endAt();
            return;
        }
        HyperDogNormalGame.Outcome game=normal.finish();
        lastExtraComeback=false;
        if(game.entersAt()){
            beginAt(game.hit()==HyperDogNormalGame.Hit.BAR);
            return;
        }
        if(comebackPaidLeft>0){
            if(normal.extraComebackHit(COMEBACK_P)){
                lastExtraComeback=true;
                beginAt(false);
                return;
            }
            if(game.bet()==3 && --comebackPaidLeft==0)comebackFromUpper=false;
        }
    }
    private void beginAt(boolean directUpper){
        if(at!=null||!normal.waitingForAt())throw new IllegalStateException("invalid AT entry");
        JsonObject n=normal.snapshot();
        int setting=n.get("setting").getAsInt();
        long seed=Long.parseUnsignedLong(n.get("rngState").getAsString());
        boolean upperComeback=comebackPaidLeft>0&&comebackFromUpper;
        at=new HyperDogAtGame(setting,seed,normal.credits(),normal.totalBet(),normal.totalPayout(),
                directUpper,upperComeback);
        comebackPaidLeft=0;comebackFromUpper=false;
    }
    private void endAt(){
        if(at==null||at.stage()!=HyperDogAtGame.Stage.COMPLETE||!normal.waitingForAt())
            throw new IllegalStateException("invalid completed AT");
        // All changes are computed on a fresh NORMAL clone before commitment.
        JsonObject saved=normal.snapshot();
        saved.addProperty("credits",at.credit());
        saved.addProperty("in",at.totalIn());
        saved.addProperty("out",at.totalOut());
        saved.addProperty("rngState",at.rngState());
        HyperDogNormalGame restored=HyperDogNormalGame.restore(saved);
        restored.resumeAfterAt();
        long count=Math.addExact(completedAtCount,1);
        long net=at.atNet();
        boolean endedUpper=at.completedFromUpper();
        normal=restored;at=null;
        completedAtCount=count;lastAtNet=net;
        comebackPaidLeft=32;comebackFromUpper=endedUpper;
    }

    /**
     * A safe public projection, entirely separate from the private snapshot.
     * No mode, RNG, selected role, BAR stocks, or winning CZ bit is sent.
     */
    public JsonObject publicState(){
        if(at==null){
            JsonObject j=normal.publicState();
            j.addProperty("comebackDisplay",comebackPaidLeft>0);
            return j;
        }
        JsonObject j=new JsonObject();
        j.addProperty("gameState",at.pending()!=null?"AT_SPINNING":
                at.stage()==HyperDogAtGame.Stage.RED7_CONFIRM?"AT_RED7_CONFIRM":
                at.stage()==HyperDogAtGame.Stage.RAINBOW7_CONFIRM?"AT_RAINBOW7_CONFIRM":
                at.waitingForBet()?"AT_READY":at.stage().name());
        j.addProperty("credit",at.credit());
        j.addProperty("bet",at.pending()==null?0:at.pending().bet());
        j.addProperty("pay",0);
        j.addProperty("atRemaining",at.remaining());
        j.addProperty("atStage",at.stage().name());
        j.addProperty("atInitialTotal",at.stage()==HyperDogAtGame.Stage.UPPER_INITIAL?
                at.upperInitialTotal():at.stage()==HyperDogAtGame.Stage.LOWER_AT?
                at.lowerInitialTotal():0L);
        if(at.stage()==HyperDogAtGame.Stage.RAINBOW7_CONFIRM)
            j.addProperty("rainbowStoppedMask",at.rainbowStopMask());
        j.addProperty("comebackDisplay",false);
        return j;
    }

    /** Store only with a server-owned lock and atomic SQLite transaction. */
    public JsonObject snapshot(){
        JsonObject j=new JsonObject();
        j.addProperty("schema",1);
        j.add("normal",normal.snapshot());
        if(at!=null)j.add("at",at.snapshot());
        j.addProperty("comebackPaidLeft",comebackPaidLeft);
        j.addProperty("completedAtCount",completedAtCount);
        j.addProperty("lastAtNet",lastAtNet);
        j.addProperty("lastExtraComeback",lastExtraComeback);
        j.addProperty("comebackFromUpper",comebackFromUpper);
        return j;
    }
    public static HyperDogFullGame restore(JsonObject j){
        if(j==null||j.get("schema").getAsInt()!=1)throw new IllegalArgumentException("schema");
        HyperDogFullGame f=new HyperDogFullGame();
        f.normal=HyperDogNormalGame.restore(j.getAsJsonObject("normal"));
        f.at=j.has("at")?HyperDogAtGame.restore(j.getAsJsonObject("at")):null;
        f.comebackPaidLeft=j.get("comebackPaidLeft").getAsInt();
        f.completedAtCount=j.get("completedAtCount").getAsLong();
        f.lastAtNet=j.get("lastAtNet").getAsLong();
        f.lastExtraComeback=j.get("lastExtraComeback").getAsBoolean();
        f.comebackFromUpper=j.has("comebackFromUpper")&&j.get("comebackFromUpper").getAsBoolean();
        if(f.comebackPaidLeft<0||f.comebackPaidLeft>32||f.completedAtCount<0||
           f.comebackFromUpper&&f.comebackPaidLeft==0||
           f.at!=null&&!f.normal.waitingForAt()||
           f.at==null&&f.normal.waitingForAt()||
           f.at!=null&&f.comebackPaidLeft!=0)
            throw new IllegalArgumentException("inconsistent game snapshot");
        return f;
    }
}
