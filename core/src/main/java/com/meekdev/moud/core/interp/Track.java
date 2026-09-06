package com.meekdev.moud.core.interp;

import com.meekdev.moud.core.clazz.PropertyType;

// one value moving toward another over a window. the window is however long it took the last two
// writes to arrive, which is what lets a tick written value and a frame written value share a path
public final class Track {

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

    // the new leg starts wherever the last one had got to, so a write mid flight does not jump
    public void write(Object value) {
        from = sample();
        to = value;
        window = sinceWrite;
        elapsed = 0;
        sinceWrite = 0;
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
