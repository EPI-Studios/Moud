package com.meekdev.moud.core.effect;

public final class Tally {

    private int seen;

    public Tally(int seen) {
        this.seen = seen;
    }

    public int take(int count) {
        int grown = Math.max(0, count - seen);
        seen = count;
        return grown;
    }
}
