package jp.pirijuggler.paper.game.god;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Objects;

/** Hidden cabinet-owned runtime. It survives seat changes and server restarts. */
public record GodMachineRuntime(
        GodFrontMode frontMode,
        int normalGamesSinceGg,
        int blue7History,
        int yellow7History,
        GodGaiaMode gaiaMode,
        int gaiaBellCount,
        int gaiaTarget,
        boolean gaiaStageActive,
        int gaiaStageGuarantee,
        int ceilingTarget,
        long totalNormalGames,
        GodSessionState gameplay,
        String forcedRole
) {
    private static final Gson GSON = new Gson();

    public GodMachineRuntime {
        Objects.requireNonNull(frontMode);
        if(gaiaMode==null)gaiaMode=GodGaiaMode.LOW;
        if(gameplay==null)gameplay=GodSessionState.initial();
        if(forcedRole!=null&&forcedRole.isBlank())forcedRole=null;
        if(normalGamesSinceGg<0||blue7History<0||yellow7History<0||gaiaBellCount<0||gaiaTarget<0||gaiaStageGuarantee<0||ceilingTarget<0||totalNormalGames<0)
            throw new IllegalArgumentException("negative GOD runtime counter");
    }

    /** Compatibility constructor: after the reset-start cycle, the normal ceiling is 1480G. */
    public GodMachineRuntime(GodFrontMode frontMode,int normalGamesSinceGg,int blue7History,int yellow7History,
                             GodGaiaMode gaiaMode,int gaiaBellCount,int gaiaTarget,boolean gaiaStageActive,
                             int gaiaStageGuarantee,long totalNormalGames,GodSessionState gameplay){
        this(frontMode,normalGamesSinceGg,blue7History,yellow7History,gaiaMode,gaiaBellCount,gaiaTarget,
                gaiaStageActive,gaiaStageGuarantee,GodProductionSpec.NORMAL_CEILING_GAMES,totalNormalGames,gameplay,null);
    }

    public GodMachineRuntime(GodFrontMode frontMode,int normalGamesSinceGg,int blue7History,int yellow7History,
                             GodGaiaMode gaiaMode,int gaiaBellCount,int gaiaTarget,boolean gaiaStageActive,
                             int gaiaStageGuarantee,int ceilingTarget,long totalNormalGames,GodSessionState gameplay){
        this(frontMode,normalGamesSinceGg,blue7History,yellow7History,gaiaMode,gaiaBellCount,gaiaTarget,
                gaiaStageActive,gaiaStageGuarantee,ceilingTarget,totalNormalGames,gameplay,null);
    }

    public static GodMachineRuntime initial(){
        // ceilingTarget=0 means "draw the reset ceiling on the first normal game".
        return new GodMachineRuntime(GodFrontMode.LOW_A,0,0,0,GodGaiaMode.LOW,0,0,false,0,0,0,GodSessionState.initial(),null);
    }

    public GodMachineRuntime withGameplay(GodSessionState state){
        return new GodMachineRuntime(frontMode,normalGamesSinceGg,blue7History,yellow7History,gaiaMode,gaiaBellCount,gaiaTarget,
                gaiaStageActive,gaiaStageGuarantee,ceilingTarget,totalNormalGames,state,forcedRole);
    }

    public GodMachineRuntime withForcedRole(String role){
        return new GodMachineRuntime(frontMode,normalGamesSinceGg,blue7History,yellow7History,gaiaMode,gaiaBellCount,gaiaTarget,
                gaiaStageActive,gaiaStageGuarantee,ceilingTarget,totalNormalGames,gameplay,role);
    }

    public String toJsonString(){return GSON.toJson(this);}
    public JsonObject toJson(){return GSON.toJsonTree(this).getAsJsonObject();}

    public static GodMachineRuntime fromJson(String json){
        if(json==null||json.isBlank())return initial();
        GodMachineRuntime d=GSON.fromJson(json,GodMachineRuntime.class);
        return d==null?initial():d;
    }
}
