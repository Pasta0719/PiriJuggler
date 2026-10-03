package jp.pirijuggler.common.protocol;

import com.google.gson.JsonObject;
import java.util.*;

/** Public remaining games and the already-selected challenge image. No draw information. */
public record SkillStopPresentation(int remaining, Target target) {
    public enum Target {
        AUTO(null), BAR("bar"), BELL("bell"), PIERO("piero");
        private final String symbol;
        Target(String symbol){this.symbol=symbol;}
        public String texture(){return symbol==null?null:"symbols/"+symbol+".png";}
    }
    public static final SkillStopPresentation EMPTY=new SkillStopPresentation(0,Target.AUTO);
    public SkillStopPresentation {
        if(remaining<0||target==null)throw new IllegalArgumentException("skill presentation");
        if(remaining==0&&target!=Target.AUTO)throw new IllegalArgumentException("challenge without bonus");
    }
    public static SkillStopPresentation read(JsonObject body){
        if(!body.has("skillRemaining")&&!body.has("skillChallenge"))return EMPTY;
        if(!body.has("skillRemaining")||!body.has("skillChallenge"))throw new IllegalArgumentException("incomplete skill presentation");
        var number=body.get("skillRemaining");
        if(!number.isJsonPrimitive()||!number.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("skillRemaining");
        int remaining=number.getAsBigDecimal().intValueExact();
        return new SkillStopPresentation(remaining,Target.valueOf(body.get("skillChallenge").getAsString()));
    }
    public void write(JsonObject body){body.addProperty("skillRemaining",remaining);body.addProperty("skillChallenge",target.name());}
    /** Bounded deduplication of successful-third-stop sound events, including packet replay. */
    public static final class NoticeGate {
        private final Set<UUID> played=new LinkedHashSet<>();
        public boolean accept(String spin){
            UUID id=UUID.fromString(spin);
            if(!played.add(id))return false;
            if(played.size()>128)played.remove(played.iterator().next());
            return true;
        }
        public void clear(){played.clear();}
    }
}
