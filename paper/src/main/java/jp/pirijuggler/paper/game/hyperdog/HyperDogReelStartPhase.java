package jp.pirijuggler.paper.game.hyperdog;

import jp.pirijuggler.paper.session.Session;

import java.util.Map;

/**
 * Carry the previous three stopped reel positions into the next spin.
 * Never infer a phase from incomplete stops or untrusted snapshot fields.
 */
public final class HyperDogReelStartPhase {
    private static final double[] FIRST_SPIN={0,7,14};
    private HyperDogReelStartPhase() {}

    public static double[] fromSession(Session session) {
        if(session==null)return FIRST_SPIN.clone();
        Map<String,Object> values=session.snapshot();
        if(number(values.get("stopped_mask"))!=7)return FIRST_SPIN.clone();
        String[] names={"display_left_stop","display_center_stop","display_right_stop"};
        double[] result=new double[3];
        for(int i=0;i<3;i++){
            Object stop=values.get(names[i]);
            if(!(stop instanceof Number))return FIRST_SPIN.clone();
            long n=number(stop);
            if(n<0||n>=21)return FIRST_SPIN.clone();
            result[i]=n;
        }
        return result;
    }

    private static long number(Object value){
        return value instanceof Number n?n.longValue():Long.MIN_VALUE;
    }
}
