package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.reel.InternalRole;
import java.util.*;

/** Server-only Phase06 premium selection. Client never receives the hidden selection itself. */
public final class PremiumPolicy {
    public enum Type { A, B, C, D, E, F }

    private final long chance;
    private final long denominator;
    private final EnumMap<Type,Long> weights=new EnumMap<>(Type.class);

    public PremiumPolicy(Map<String,Object> config){
        Map<String,Object> premium=map(config.get("premium"));
        chance=number(premium.get("big_chance_weight"));
        denominator=number(premium.get("denominator"));
        Map<String,Object> w=map(premium.get("weights"));
        weights.put(Type.A,number(w.get("reverse")));
        weights.put(Type.B,number(w.get("middle_cherry")));
        weights.put(Type.C,number(w.get("sound_first_peka")));
        weights.put(Type.D,number(w.get("strong_after_peka")));
        weights.put(Type.E,number(w.get("five_notice_blink")));
        weights.put(Type.F,number(w.get("fake_tenpai")));
    }

    public Optional<Type> draw(InternalRole role,SplittableRandom random){
        if(!bigFamily(role)||chance==0||random.nextLong(denominator)>=chance)return Optional.empty();
        EnumSet<Type> eligible=role==InternalRole.CHERRY_BIG?EnumSet.allOf(Type.class):EnumSet.of(Type.A,Type.C,Type.D,Type.E,Type.F);
        long total=0;for(Type type:eligible)total=Math.addExact(total,weights.get(type));
        if(total<=0)throw new IllegalStateException("No eligible premium weight");
        long roll=random.nextLong(total),sum=0;
        for(Type type:Type.values())if(eligible.contains(type)){sum=Math.addExact(sum,weights.get(type));if(roll<sum)return Optional.of(type);}
        throw new IllegalStateException("Premium draw overflow");
    }

    public static boolean bigFamily(InternalRole role){return role==InternalRole.BIG||role==InternalRole.CHERRY_BIG||role==InternalRole.PIERO_BIG;}
    public static boolean regFamily(InternalRole role){return role==InternalRole.REG||role==InternalRole.CHERRY_REG||role==InternalRole.PIERO_REG;}

    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private static long number(Object value){return ((Number)value).longValue();}
}
