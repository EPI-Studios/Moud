package com.meekdev.moud.core.interp;

import com.meekdev.moud.core.clazz.PropertyType;

public final class Track {

    public static final double MAX_AUTO_WINDOW = 0.1;

    private final PropertyType type;
    private Curve curve = Curve.LINEAR;

    private Object from;
    private Object to;
    private double window;
    private double elapsed;
    private double sinceWrite;

    public Track(PropertyType type, Object value) {
        this.type = type;
        this.from = value;
        this.to = value;
        this.window = 0;
        this.elapsed = 0;
    }

    public Track curve(Curve curve) {
        this.curve = curve;
        return this;
    }

    public void beginLeg() {
        from = to;
    }

    public void to(Object value) {
        to = value;
    }

    public boolean isStill() {
        return from == to || from.equals(to);
    }

    public Object sampleAt(double alpha) {
        if (alpha >= 1.0 || isStill()) return to;
        if (alpha <= 0.0) return from;
        return Blend.of(type, from, to, alpha);
    }

    public void write(Object value) {
        write(value, Math.min(sinceWrite, MAX_AUTO_WINDOW));
    }

    public void write(Object value, double window) {
        from = sample();
        to = value;
        this.window = window;
        elapsed = 0;
        sinceWrite = 0;
    }

    public boolean isSettled() {
        return alpha() >= 1.0;
    }

    public double secondsSinceWrite() {
        return sinceWrite;
    }

    public void advance(double dt) {
        elapsed += dt;
        sinceWrite += dt;
    }

    public double alpha() {
        if (window <= 0) return 1.0;
        return curve.clamped(elapsed / window);
    }

    public Object sample() {
        double a = alpha();
        if (a >= 1.0) return to;
        return Blend.of(type, from, to, a);
    }

    public Object target() {
        return to;
    }
}
