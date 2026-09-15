package jp.pirijuggler.paper.data;

import java.util.ArrayList;
import java.util.List;

/** SPEC 123 fixed Largest-Triangle-Three-Buckets implementation. */
public final class Lttb {
    public record Point(long x,long y) {}

    public static List<Point> downsample(List<Point> input,int threshold) {
        if(input==null)throw new IllegalArgumentException("input");
        if(threshold<3)throw new IllegalArgumentException("threshold");
        int n=input.size();
        if(n<=threshold)return List.copyOf(input);

        double every=(n-2.0)/(threshold-2.0);
        var out=new ArrayList<Point>(threshold);
        int a=0;
        out.add(input.get(a));

        for(int i=0;i<threshold-2;i++){
            int avgStart=(int)Math.floor((i+1)*every)+1;
            int avgEnd=(int)Math.floor((i+2)*every)+1;
            if(avgEnd>n)avgEnd=n;
            if(avgStart>=n)avgStart=n-1;
            if(avgEnd<=avgStart)avgEnd=Math.min(n,avgStart+1);

            double avgX=0,avgY=0;
            int avgCount=avgEnd-avgStart;
            for(int j=avgStart;j<avgEnd;j++){
                Point p=input.get(j);avgX+=p.x();avgY+=p.y();
            }
            avgX/=avgCount;avgY/=avgCount;

            int rangeStart=(int)Math.floor(i*every)+1;
            int rangeEnd=(int)Math.floor((i+1)*every)+1;
            if(rangeEnd>n-1)rangeEnd=n-1;
            if(rangeEnd<=rangeStart)rangeEnd=Math.min(n-1,rangeStart+1);

            Point ap=input.get(a);
            double maxArea=-1;
            int nextA=rangeStart;
            for(int j=rangeStart;j<rangeEnd;j++){
                Point p=input.get(j);
                double area=Math.abs((ap.x()-avgX)*(p.y()-ap.y())-(ap.x()-p.x())*(avgY-ap.y()));
                if(!Double.isFinite(area))throw new IllegalArgumentException("Non-finite LTTB area");
                // Strictly greater keeps the lower original index on equal-area ties.
                if(area>maxArea){maxArea=area;nextA=j;}
            }
            a=nextA;out.add(input.get(a));
        }
        out.add(input.get(n-1));
        return List.copyOf(out);
    }

    private Lttb() {}
}
