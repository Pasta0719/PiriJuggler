package jp.pirijuggler.paper.game;

import java.util.random.RandomGenerator;

/** Shared post-bonus state transition for live JUGGLER_GOD play and forced recovery. */
public final class JugglerGodTransitions {
    private static final int[] CONTINUATION_PERCENT={0,75,78,80,82,85,90};

    public static JugglerGodRuntime afterBonus(
            JugglerGodRuntime state,int setting,RandomGenerator rng,
            long normalBigToHeavenPpm,long normalRegToHeavenPpm,long heavenToHeavenPpm
    ){
        return afterBonus(state,setting,rng,normalBigToHeavenPpm,normalRegToHeavenPpm,heavenToHeavenPpm,CONTINUATION_PERCENT);
    }

    public static JugglerGodRuntime afterBonus(
            JugglerGodRuntime state,int setting,RandomGenerator rng,
            long normalBigToHeavenPpm,long normalRegToHeavenPpm,long heavenToHeavenPpm,
            int[] continuationPercent
    ){
        if(state==null||rng==null||setting<1||setting>6||continuationPercent==null||continuationPercent.length<7)
            throw new IllegalArgumentException("JUGGLER_GOD transition args");

        if("GOD_CHAIN".equals(state.bonusOrigin())||state.mode()==JugglerGodRuntime.Mode.GOD_CHAIN){
            if(state.guaranteedRemaining()>0){
                return state.core(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,
                        state.guaranteedRemaining()-1,true,false,"NONE",
                        state.godBigCount(),false,"GOD_GUARANTEED_NEXT");
            }
            if(rng.nextInt(100)<continuationPercent[setting]){
                return state.core(JugglerGodRuntime.Mode.GOD_CHAIN,0,0,
                        0,true,true,"NONE",state.godBigCount(),false,"GOD_CONTINUE");
            }
            return enterHeaven(state,rng.nextInt(32)+1,"GOD_END_HEAVEN");
        }

        if("HEAVEN".equals(state.bonusOrigin())){
            if(rng.nextLong(1_000_000)<heavenToHeavenPpm)
                return enterHeaven(state,rng.nextInt(32)+1,"HEAVEN_CONTINUE");
            return state.core(JugglerGodRuntime.Mode.NORMAL,0,0,0,false,false,
                    "NONE",state.godBigCount(),false,"HEAVEN_END");
        }

        long chance="BIG".equals(state.bonusOrigin())?normalBigToHeavenPpm:
                "REG".equals(state.bonusOrigin())?normalRegToHeavenPpm:0;
        if(rng.nextLong(1_000_000)<chance)
            return enterHeaven(state,rng.nextInt(32)+1,"NORMAL_TO_HEAVEN");
        return state.core(JugglerGodRuntime.Mode.NORMAL,0,0,0,false,false,
                "NONE",state.godBigCount(),false,"NORMAL");
    }

    private static JugglerGodRuntime enterHeaven(JugglerGodRuntime state,int target,String event){
        return state.core(JugglerGodRuntime.Mode.HEAVEN,target,0,0,false,false,
                "NONE",state.godBigCount(),false,event);
    }

    private JugglerGodTransitions(){}
}
