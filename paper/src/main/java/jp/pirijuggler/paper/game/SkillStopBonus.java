package jp.pirijuggler.paper.game;

import com.google.gson.JsonObject;
import jp.pirijuggler.paper.session.Session;
import java.util.random.RandomGenerator;

/** Persisted remaining games and the lever-fixed challenge target. */
public record SkillStopBonus(int remaining, Target target) {
    public enum Target {
        AUTO(0), BAR(64), BELL(4), PIERO(8);
        private final int pattern;
        Target(int pattern){this.pattern=pattern;}
        public int pattern(){return pattern;}
    }
    public SkillStopBonus {
        if(remaining<0||target==null)throw new IllegalArgumentException("bonus state");
    }
    public static int initial(String type){return switch(type){case "BIG"->20;case "REG"->8;default->throw new IllegalArgumentException("bonus type");};}
    public static SkillStopBonus read(Session session){
        JsonObject state=session.machineState();
        if(state!=null&&state.has("skillRemaining"))return new SkillStopBonus(state.get("skillRemaining").getAsInt(),state.has("skillChallenge")?Target.valueOf(state.get("skillChallenge").getAsString()):Target.AUTO);
        // Phase02 snapshots used cumulative 14-medal payouts; migrate without a redraw.
        String type=session.text("bonus_type");
        return new SkillStopBonus(type==null?0:Math.max(0,initial(type)-Math.toIntExact(session.number("bonus_payout_count")/14)),Target.AUTO);
    }
    public SkillStopBonus lever(String type,RandomGenerator random,Target forced){
        if(remaining==0)throw new IllegalStateException("bonus exhausted");
        Target next=forced;
        if(next==null)next=random.nextInt("BIG".equals(type)?15:9)==0?Target.values()[1+random.nextInt(3)]:Target.AUTO;
        return new SkillStopBonus(remaining,next);
    }
    public SkillStopBonus finish(boolean success){
        if(remaining==0||success&&target==Target.AUTO)throw new IllegalStateException("invalid bonus completion");
        return new SkillStopBonus(Math.addExact(remaining-1,success?3:0),Target.AUTO);
    }
    public void write(JsonObject state){state.addProperty("skillRemaining",remaining);state.addProperty("skillChallenge",target.name());}
}
