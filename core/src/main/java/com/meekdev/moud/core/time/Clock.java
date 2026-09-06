package com.meekdev.moud.core.time;

public final class Clock {

    // a paused window or a loading hitch would otherwise hand a script a dt of several seconds
    // and teleport whatever it was moving
    public static final double MAX_STEP = 0.25;

    private final Source source;
    private long last;

    public Clock() {
        this(System::nanoTime);
    }

    public Clock(Source source) {
        this.source = source;
        this.last = source.nanos();
    }

    public double tick() {
        long now = source.nanos();
        double seconds = (now - last) * 1e-9;
        last = now;
        return seconds < 0 ? 0 : Math.min(seconds, MAX_STEP);
    }

    @FunctionalInterface
    public interface Source {
        long nanos();
    }
}
