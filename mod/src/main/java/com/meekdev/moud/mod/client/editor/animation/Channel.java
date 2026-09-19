package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.Vector3;
import org.jspecify.annotations.Nullable;

public enum Channel {
    ROTATION("rotation", Vector3.ZERO, 1.0),
    POSITION("position", Vector3.ZERO, 1.0 / 16.0),
    SCALE("scale", Vector3.ONE, 0.05);

    private final String key;
    private final Vector3 rest;
    private final double step;

    Channel(String key, Vector3 rest, double step) {
        this.key = key;
        this.rest = rest;
        this.step = step;
    }

    public String key() {
        return key;
    }

    public Vector3 rest() {
        return rest;
    }

    public double step() {
        return step;
    }

    public String label() {
        return switch (this) {
            case ROTATION -> "Rotation";
            case POSITION -> "Position";
            case SCALE -> "Scale";
        };
    }

    public static @Nullable Channel named(String key) {
        for (Channel channel : values()) {
            if (channel.key.equals(key)) return channel;
        }
        return null;
    }
}
