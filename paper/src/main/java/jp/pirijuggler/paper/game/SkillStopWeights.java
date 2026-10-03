package jp.pirijuggler.paper.game;

import com.google.gson.*;
import jp.pirijuggler.common.reel.SkillStopRole;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.random.RandomGenerator;

/** Approved integer draw table, isolated from all existing machine probabilities. */
public final class SkillStopWeights {
    private final int[][] weights=new int[6][SkillStopRole.values().length];
    public SkillStopWeights(){
        try(var in=SkillStopWeights.class.getResourceAsStream("/skill-stop-weights.json")){
            if(in==null)throw new IllegalStateException("Missing skill stop weights");
            JsonArray settings=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("proposedWeights");
            for(JsonElement e:settings){JsonObject row=e.getAsJsonObject();int setting=row.get("setting").getAsInt();JsonObject w=row.getAsJsonObject("weights");long total=0;
                for(SkillStopRole role:SkillStopRole.values()){
                    String key=role.oneMedal()?role.name():role.name().toLowerCase(java.util.Locale.ROOT);int value=w.get(key).getAsInt();
                    if(value<0)throw new IllegalArgumentException("negative weight");weights[setting-1][role.ordinal()]=value;total+=value;
                }
                if(total!=1_000_000_000L)throw new IllegalArgumentException("weight sum");
            }
        }catch(IOException ex){throw new UncheckedIOException(ex);}
    }
    public SkillStopRole draw(int setting,RandomGenerator random){
        if(setting<1||setting>6)throw new IllegalArgumentException("setting");int roll=random.nextInt(1_000_000_000),sum=0;
        for(SkillStopRole role:SkillStopRole.values()){sum+=weights[setting-1][role.ordinal()];if(roll<sum)return role;}
        throw new IllegalStateException("uncovered draw");
    }
    public static SkillStopRole pending(RandomGenerator random){
        int n=random.nextInt(81920);
        if(n<320)return SkillStopRole.CHERRY;
        if(n<8512)return SkillStopRole.GRAPE;
        if(n<16704)return SkillStopRole.REPLAY;
        if(n<16709)return SkillStopRole.BELL;
        if(n<16714)return SkillStopRole.PIERO;
        return SkillStopRole.MISS;
    }
}
