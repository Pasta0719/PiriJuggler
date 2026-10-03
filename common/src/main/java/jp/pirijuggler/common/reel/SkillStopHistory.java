package jp.pirijuggler.common.reel;

import java.util.Arrays;

/** Immutable authoritative inputs and stops; survives a suspended/recovered round. */
public record SkillStopHistory(int[] inputs, int[] stops, int[] order) {
    public SkillStopHistory {
        inputs = inputs.clone(); stops = stops.clone(); order = order.clone();
        if (inputs.length != 3 || stops.length != 3 || order.length > 3) throw new IllegalArgumentException("history");
        int mask = 0;
        for (int reel : order) {
            if (reel < 0 || reel > 2 || (mask & (1 << reel)) != 0 || inputs[reel] < 0 || inputs[reel] > 20 || stops[reel] < 0 || stops[reel] > 20) throw new IllegalArgumentException("history");
            mask |= 1 << reel;
        }
        for (int reel = 0; reel < 3; reel++) if ((mask & (1 << reel)) == 0 && (inputs[reel] != -1 || stops[reel] != -1)) throw new IllegalArgumentException("history");
    }
    @Override public int[] inputs() { return inputs.clone(); }
    @Override public int[] stops() { return stops.clone(); }
    @Override public int[] order() { return order.clone(); }
    public int input(int reel) { return inputs[reel]; }
    public int stop(int reel) { return stops[reel]; }
    public int count() { return order.length; }
    public int first() { if (order.length == 0) throw new IllegalStateException("no first stop"); return order[0]; }
    public int mask() { int mask = 0; for (int r : order) mask |= 1 << r; return mask; }
    public boolean bit(int reel) { return inputs[reel] >= 0 && inputs[reel] == stops[reel]; }
    public SkillStopHistory append(Reel reel, int input, int stop) {
        if ((mask() & reel.bit()) != 0) throw new IllegalArgumentException("already stopped");
        int[] p = inputs.clone(), t = stops.clone(), next = Arrays.copyOf(order, order.length + 1);
        p[reel.ordinal()] = input; t[reel.ordinal()] = stop; next[order.length] = reel.ordinal();
        return new SkillStopHistory(p, t, next);
    }
    public static SkillStopHistory empty() { return new SkillStopHistory(new int[]{-1,-1,-1}, new int[]{-1,-1,-1}, new int[0]); }
}
