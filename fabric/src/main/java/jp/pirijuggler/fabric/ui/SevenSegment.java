package jp.pirijuggler.fabric.ui;
public final class SevenSegment {
    public static final int[][] RECTANGLES={{8,0,24,8},{32,8,8,24},{32,40,8,24},{8,64,24,8},{0,40,8,24},{0,8,8,24},{8,32,24,8}};
    public static final String[] DIGITS={"ABCDEF","BC","ABDEG","ABCDG","BCFG","ACDFG","ACDEFG","ABC","ABCDEFG","ABCDFG"};
    public static boolean active(char digit,int segment){return digit>='0'&&digit<='9'&&DIGITS[digit-'0'].indexOf('A'+segment)>=0||digit=='-'&&segment==6;}
    private SevenSegment(){}
}
