package jp.pirijuggler.paper.data;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LttbTest {
    @Test void atMostThresholdKeepsEveryPointIncludingDuplicateX(){
        var input=java.util.List.of(new Lttb.Point(0,0),new Lttb.Point(1,-3),new Lttb.Point(1,77),new Lttb.Point(2,74));
        assertEquals(input,Lttb.downsample(input,300));
    }

    @Test void overThresholdUsesExactly300KeepsEndpointsAndAcceptsDuplicateXDeterministically(){
        var input=new ArrayList<Lttb.Point>();
        for(int i=0;i<401;i++)input.add(new Lttb.Point(i==200?199:i,(i%17)*31L-(i%5)*97L));
        var first=Lttb.downsample(input,300);var second=Lttb.downsample(input,300);
        assertEquals(300,first.size());assertEquals(input.getFirst(),first.getFirst());assertEquals(input.getLast(),first.getLast());assertEquals(first,second);
        assertTrue(input.stream().filter(p->p.x()==199).count()>=2,"fixture must contain duplicate x values");
    }

    @Test void equalAreaTieUsesEarlierOriginalIndex(){
        var input=java.util.List.of(new Lttb.Point(0,0),new Lttb.Point(1,1),new Lttb.Point(2,2),new Lttb.Point(3,3),new Lttb.Point(4,4));
        var sampled=Lttb.downsample(input,3);
        assertEquals(input.getFirst(),sampled.getFirst());assertEquals(input.getLast(),sampled.getLast());
        assertEquals(input.get(1),sampled.get(1),"zero-area tie must retain the earliest candidate");
    }
}
