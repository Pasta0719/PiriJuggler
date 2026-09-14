package jp.pirijuggler.common.reel;
/** Shared visual formulas only; the Paper receiver supplies its own time and ping. */
public final class ReelMotion {
    public enum Profile {
        NORMAL(400,350), REVERSE_500MS(800,750), RESUME_NORMAL(200,150);
        private final int clientDelayMs,serverThresholdMs;
        Profile(int clientDelayMs,int serverThresholdMs){this.clientDelayMs=clientDelayMs;this.serverThresholdMs=serverThresholdMs;}
        public int clientDelayMs(){return clientDelayMs;}public int serverThresholdMs(){return serverThresholdMs;}
    }
    public static double wrap(double phase){return (phase%21+21)%21;}
    public static double effectiveMillis(long startNanos,long receiveNanos,int ping){return Math.max(0,(receiveNanos-startNanos)/1_000_000.0-Math.clamp(ping,0,250));}
    public static double delta(Profile profile,double seconds){
        if(!Double.isFinite(seconds))throw new IllegalArgumentException("Non-finite elapsed time");double e=Math.max(0,seconds);
        return switch(profile){case NORMAL ->e<=.150?0:e<.500?-.5*(18/.350)*(e-.150)*(e-.150):-3.15-18*(e-.500);
            case REVERSE_500MS ->e<.500?12*e:e<.800?6-.5*(18/.300)*(e-.500)*(e-.500):3.3-18*(e-.800);
            case RESUME_NORMAL ->-18*e;};
    }
    public static double phase(Profile profile,double start,double elapsedSec){if(!Double.isFinite(start))throw new IllegalArgumentException("Non-finite phase");return wrap(start+delta(profile,elapsedSec));}
    public static int pressedIndex(Profile profile,double start,long startNanos,long receiveNanos,int ping){return (int)Math.floor(phase(profile,start,effectiveMillis(startNanos,receiveNanos,ping)/1000));}
    /** Normal reel travel is index-decreasing, so slip advances only in that direction. */
    public static int slip(int target,int pressed){return Math.floorMod(pressed-target,21);}
    public static int durationMs(int slip){if(slip<0||slip>20)throw new IllegalArgumentException("Invalid slip");return Math.min(1200,80+slip*50);}
    /** Unwrapped normal-direction endpoint. Never chooses the opposite-direction shortcut. */
    public static double normalStopEndpoint(double current,int target){
        if(!Double.isFinite(current)||target<0||target>=21)throw new IllegalArgumentException("Invalid stop endpoint");
        double endpoint=target;while(endpoint>current)endpoint-=21;return endpoint;
    }
    /** Preserve server timing unless packet delay would require a visual speed above the normal 18 symbols/sec. */
    public static int visualDurationMs(double from,double endpoint,int requestedMs){
        if(!Double.isFinite(from)||!Double.isFinite(endpoint)||requestedMs<0||endpoint>from)throw new IllegalArgumentException("Invalid visual stop");
        int continuity=(int)Math.ceil((from-endpoint)/18.0*1000.0);return Math.max(requestedMs,continuity);
    }
    private ReelMotion(){}
}
