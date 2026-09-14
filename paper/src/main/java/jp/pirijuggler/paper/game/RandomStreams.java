package jp.pirijuggler.paper.game;

import java.security.SecureRandom;
import java.util.*;

/** Main-thread factory. No seed is exposed in a public API, packet or log. */
public final class RandomStreams {
    private final long master;
    private final SplittableRandom events, simulation;
    private final Map<Integer,SplittableRandom> machines = new HashMap<>();
    public static RandomStreams production() { return new RandomStreams(new SecureRandom().nextLong()); }
    // Package-private injection is used only by tests in this package.
    RandomStreams(long master) {
        this.master=master;
        events=new SplittableRandom(mix(master ^ 0x4556454e5453L));
        simulation=new SplittableRandom(mix(master ^ 0x53494d554cL));
    }
    public SplittableRandom gameplay(int machine) {
        if(machine<1)throw new IllegalArgumentException("Machine ID");
        return machines.computeIfAbsent(machine,id->new SplittableRandom(mix(master ^ 0x47414d4500000000L ^ id)));
    }
    public SplittableRandom eventAllocation() { return events; }
    public SplittableRandom runtimeSimulation() {
        return new SplittableRandom(simulation.nextLong() ^ new SecureRandom().nextLong());
    }
    private static long mix(long value) {
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }
}
