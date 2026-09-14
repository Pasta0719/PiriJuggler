package jp.pirijuggler.paper.reel;
import jp.pirijuggler.common.reel.Reel;
public enum Payline {
    L1_CENTER(0,0,0), L2_TOP(-1,-1,-1), L3_BOTTOM(1,1,1), L4_DOWN(-1,0,1), L5_UP(1,0,-1);
    private final int left,center,right;
    Payline(int left,int center,int right){this.left=left;this.center=center;this.right=right;}
    public int row(Reel reel){return switch(reel){case LEFT->left;case CENTER->center;case RIGHT->right;};}
}
