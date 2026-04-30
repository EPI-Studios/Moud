package com.moud.client.fabric.scene.interp;

import com.moud.core.interp.InterpPolicy;
import com.moud.core.interp.InterpProperty;

public final class NodeInterpolator {

    private final PropertySampleBuffer[] buffers = new PropertySampleBuffer[InterpProperty.COUNT];

    private InterpPolicy policy;
    private long lastUpdateNanos;

    public NodeInterpolator(InterpPolicy initial) {
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = new PropertySampleBuffer();
        }
        this.policy = initial;
    }

    public InterpPolicy policy() {
        return policy;
    }

    public void setPolicy(InterpPolicy next) {
        if (next != null) {
            this.policy = next;
        }
    }

    public long lastUpdateNanos() {
        return lastUpdateNanos;
    }

    public void push(InterpProperty property, float value, long nowNanos) {
        buffers[property.ordinal()].push(nowNanos, value);
        lastUpdateNanos = nowNanos;
    }

    public boolean has(InterpProperty property) {
        return !buffers[property.ordinal()].isEmpty();
    }

    public float sample(InterpProperty property, long sampleNanos) {
        PropertySampleBuffer buf = buffers[property.ordinal()];
        if (buf.isEmpty()) {
            return Float.NaN;
        }
        if (!policy.smooths()) {
            return buf.lastValue();
        }
        return buf.sample(sampleNanos, policy.mode());
    }

    public void fill(SampledTransform out, long sampleNanos) {
        long lagNanos = (long) policy.lagMs() * 1_000_000L;
        long target = sampleNanos - lagNanos;
        if (!policy.smooths()) {
            target = sampleNanos;
        }

        out.hasX = sampleInto(out, InterpProperty.X, target);
        out.hasY = sampleInto(out, InterpProperty.Y, target);
        out.hasZ = sampleInto(out, InterpProperty.Z, target);
        out.hasRx = sampleInto(out, InterpProperty.RX, target);
        out.hasRy = sampleInto(out, InterpProperty.RY, target);
        out.hasRz = sampleInto(out, InterpProperty.RZ, target);
        out.hasSx = sampleInto(out, InterpProperty.SX, target);
        out.hasSy = sampleInto(out, InterpProperty.SY, target);
        out.hasSz = sampleInto(out, InterpProperty.SZ, target);
    }

    public void reset() {
        for (PropertySampleBuffer buf : buffers) {
            buf.reset();
        }
        lastUpdateNanos = 0L;
    }

    private boolean sampleInto(SampledTransform out, InterpProperty p, long target) {
        PropertySampleBuffer buf = buffers[p.ordinal()];
        if (buf.isEmpty()) {
            return false;
        }
        float value = policy.smooths()
                ? buf.sample(target, policy.mode())
                : buf.lastValue();
        switch (p) {
            case X -> out.x = value;
            case Y -> out.y = value;
            case Z -> out.z = value;
            case RX -> out.rx = value;
            case RY -> out.ry = value;
            case RZ -> out.rz = value;
            case SX -> out.sx = value;
            case SY -> out.sy = value;
            case SZ -> out.sz = value;
        }
        return true;
    }
}
