package com.moud.server.minestom.scripting.runtime;

public final class PendingTween {
    final long nodeId;
    final String prop;
    final float fromValue;
    final float toValue;
    final double duration;
    double elapsed;

    public PendingTween(long nodeId, String prop, float fromValue, float toValue, double duration) {
        this.nodeId = nodeId;
        this.prop = prop;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.duration = duration;
    }
}
