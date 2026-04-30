package com.moud.client.fabric.scene.interp;

import com.moud.core.interp.InterpMode;

public final class PropertySampleBuffer {

    private static final int CAPACITY = 8;

    private final long[] timestamps = new long[CAPACITY];
    private final float[] values = new float[CAPACITY];

    private int head;
    private int size;
    private float lastWritten = Float.NaN;

    public void push(long nowNanos, float value) {
        if (size > 0) {
            int latestIdx = indexOf(size - 1);
            if (timestamps[latestIdx] == nowNanos) {
                values[latestIdx] = value;
                lastWritten = value;
                return;
            }
            if (Float.compare(values[latestIdx], value) == 0) {
                return;
            }
        }
        int idx = (head + size) % CAPACITY;
        if (size == CAPACITY) {
            head = (head + 1) % CAPACITY;
            idx = (head + CAPACITY - 1) % CAPACITY;
        } else {
            size++;
        }
        timestamps[idx] = nowNanos;
        values[idx] = value;
        lastWritten = value;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public float lastValue() {
        if (size == 0) {
            return Float.NaN;
        }
        return values[indexOf(size - 1)];
    }

    public float lastWrittenValue() {
        return lastWritten;
    }

    public float sample(long sampleNanos, InterpMode mode) {
        if (size == 0) {
            return Float.NaN;
        }
        if (size == 1 || mode == InterpMode.SNAP) {
            return values[indexOf(size - 1)];
        }

        int idx0 = -1;
        int idx1 = -1;
        for (int i = 0; i < size; i++) {
            int slot = indexOf(i);
            if (timestamps[slot] <= sampleNanos) {
                idx0 = slot;
            } else {
                idx1 = slot;
                break;
            }
        }

        if (idx0 == -1) {
            return values[indexOf(0)];
        }
        if (idx1 == -1) {
            return values[indexOf(size - 1)];
        }

        long t0 = timestamps[idx0];
        long t1 = timestamps[idx1];
        if (t1 == t0) {
            return values[idx1];
        }
        float alpha = (float) ((double) (sampleNanos - t0) / (double) (t1 - t0));
        if (alpha < 0.0f) {
            alpha = 0.0f;
        } else if (alpha > 1.0f) {
            alpha = 1.0f;
        }

        float v0 = values[idx0];
        float v1 = values[idx1];

        return switch (mode) {
            case SNAP -> v1;
            case LINEAR, SLERP -> v0 + (v1 - v0) * alpha;
            case HERMITE -> hermite(v0, v1, alpha);
        };
    }

    public void reset() {
        head = 0;
        size = 0;
        lastWritten = Float.NaN;
    }

    private int indexOf(int logical) {
        return (head + logical) % CAPACITY;
    }

    private static float hermite(float v0, float v1, float t) {
        float h = t * t * (3.0f - 2.0f * t);
        return v0 + (v1 - v0) * h;
    }
}
