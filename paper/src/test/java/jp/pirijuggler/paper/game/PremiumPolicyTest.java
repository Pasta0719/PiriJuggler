package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.reel.InternalRole;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PremiumPolicyTest {
    private static Map<String,Object> config(long a,long b,long c,long d,long e,long f){
        return Map.of("premium",Map.of("big_chance_weight",1,"denominator",1,"weights",Map.of(
            "reverse",a,"middle_cherry",b,"sound_first_peka",c,"strong_after_peka",d,"five_notice_blink",e,"fake_tenpai",f)));
    }
    @Test void regFamilyCanNeverReceivePremium(){
        var policy=new PremiumPolicy(config(1,1,1,1,1,1));var rng=new SplittableRandom(1);
        for(var role:List.of(InternalRole.REG,InternalRole.CHERRY_REG,InternalRole.PIERO_REG))for(int i=0;i<100;i++)assertTrue(policy.draw(role,rng).isEmpty());
    }
    @Test void premiumBIsOnlyEligibleForCherryBig(){
        var onlyB=new PremiumPolicy(config(0,1,0,0,0,0));assertEquals(PremiumPolicy.Type.B,onlyB.draw(InternalRole.CHERRY_BIG,new SplittableRandom(1)).orElseThrow());
        assertThrows(IllegalStateException.class,()->onlyB.draw(InternalRole.BIG,new SplittableRandom(1)));
        assertThrows(IllegalStateException.class,()->onlyB.draw(InternalRole.PIERO_BIG,new SplittableRandom(1)));
    }
    @Test void everyNonBPremiumIsAvailableToAllBigFamilyRoles(){
        var roles=List.of(InternalRole.BIG,InternalRole.CHERRY_BIG,InternalRole.PIERO_BIG);
        long[][] rows={{1,0,0,0,0,0},{0,0,1,0,0,0},{0,0,0,1,0,0},{0,0,0,0,1,0},{0,0,0,0,0,1}};
        PremiumPolicy.Type[] types={PremiumPolicy.Type.A,PremiumPolicy.Type.C,PremiumPolicy.Type.D,PremiumPolicy.Type.E,PremiumPolicy.Type.F};
        for(int i=0;i<rows.length;i++){var r=rows[i];var policy=new PremiumPolicy(config(r[0],r[1],r[2],r[3],r[4],r[5]));for(var role:roles)assertEquals(types[i],policy.draw(role,new SplittableRandom(1)).orElseThrow());}
    }
}
