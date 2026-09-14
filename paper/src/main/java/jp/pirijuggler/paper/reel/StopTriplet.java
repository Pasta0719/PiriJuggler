package jp.pirijuggler.paper.reel;
import jp.pirijuggler.common.reel.Reel;
public record StopTriplet(int left,int center,int right) {
    public StopTriplet {if(left<0||left>=21||center<0||center>=21||right<0||right>=21)throw new IllegalArgumentException("Stops must be 0..20");}
    public int stop(Reel reel){return switch(reel){case LEFT->left;case CENTER->center;case RIGHT->right;};}
    public int id(){return left*441+center*21+right;}
    public int fixedKey(int mask){return mask<<14|((mask&1)!=0?left*441:0)+((mask&2)!=0?center*21:0)+((mask&4)!=0?right:0);}
    public StopTriplet with(Reel reel,int stop){return switch(reel){case LEFT->new StopTriplet(stop,center,right);case CENTER->new StopTriplet(left,stop,right);case RIGHT->new StopTriplet(left,center,stop);};}
}
