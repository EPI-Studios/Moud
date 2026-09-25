package com.meekdev.moud.core.time;

public final class Clock {

    public static final double MAX_STEP = 0.25;
    public static final double MAX_WALL_STEP = 5;

    private final Source source;
    private final double limit;
    private long last;

    public Clock() {
        this(System::nanoTime, MAX_STEP);
    }

    public Clock(double limit) {
        this(System::nanoTime, limit);
    }

    public Clock(Source source) {
        this(source, MAX_STEP);
    }

    public Clock(Source source, double limit) {
        this.source = source;
        this.limit = limit;
        this.last = source.nanos();
    }

    public double tick() {
        long now = source.nanos();
        double seconds = (now - last) * 1e-9;
        last = now;
        return seconds < 0 ? 0 : Math.min(seconds, limit);
    }

    @FunctionalInterface
    public interface Source {
        long nanos();
    }
}
