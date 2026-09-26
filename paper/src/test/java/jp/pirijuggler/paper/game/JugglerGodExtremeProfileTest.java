package jp.pirijuggler.paper.game;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JugglerGodExtremeProfileTest extends GameFixture {
    @Test void standardAndExtremeProfilesStaySeparated() throws Exception {
        var weights=new RoleWeights(config);
        var random=new RandomStreams(987654321L);

        var standardNormal=new NormalGame(weights,random,SOLVER,main,config,false);
        var standard=new JugglerGodGameEngine(standardNormal,random,weights,config);

        @SuppressWarnings("unchecked")
        Map<String,Object> extreme=(Map<String,Object>)config.get("juggler_god_extreme");
        int big=((Number)extreme.get("big_payout")).intValue();
        int reg=((Number)extreme.get("reg_payout")).intValue();
        var extremeNormal=new NormalGame(weights,random,SOLVER,main,config,false,big,reg);
        var extremeEngine=new JugglerGodGameEngine(extremeNormal,random,weights,config,"juggler_god_extreme");

        assertEquals(8192,intField(standard,"godDenominator"));
        assertEquals(7,intField(standard,"godInGodBigStock"));
        assertEquals(5,intField(standard,"godGuaranteedBigs"));
        assertEquals(266,intField(standard,"bigThreshold"));
        assertEquals(98,intField(standard,"regThreshold"));

        assertEquals(16384,intField(extremeEngine,"godDenominator"));
        assertEquals(10,intField(extremeEngine,"godInGodBigStock"));
        assertEquals(8,intField(extremeEngine,"godGuaranteedBigs"));
        assertEquals(406,intField(extremeEngine,"bigThreshold"));
        assertEquals(154,intField(extremeEngine,"regThreshold"));
        assertEquals(60000,longField(extremeEngine,"normalBigToHeavenPpm"));
        assertEquals(30000,longField(extremeEngine,"normalRegToHeavenPpm"));
        assertEquals(700000,longField(extremeEngine,"heavenToHeavenPpm"));

        int[] continuation=(int[])field(extremeEngine,"continuationPercent");
        assertEquals(75,continuation[1]);
        assertEquals(78,continuation[2]);
        assertEquals(80,continuation[3]);
        assertEquals(82,continuation[4]);
        assertEquals(85,continuation[5]);
        assertEquals(90,continuation[6]);

        assertEquals(406,intField(extremeNormal,"bigThreshold"));
        assertEquals(154,intField(extremeNormal,"regThreshold"));
    }

    private static Object field(Object target,String name) throws Exception {
        Field field=target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int intField(Object target,String name) throws Exception {
        return ((Number)field(target,name)).intValue();
    }

    private static long longField(Object target,String name) throws Exception {
        return ((Number)field(target,name)).longValue();
    }
}
