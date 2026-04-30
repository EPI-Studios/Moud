package com.moud.client.fabric.scene.tween;

public final class TweenChannelState {

    private final String propertyKey;
    private final float fromValue;
    private final float toValue;
    private float currentValue;

    public TweenChannelState(String propertyKey, float fromValue, float toValue) {
        this.propertyKey = propertyKey;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.currentValue = fromValue;
    }

    public String propertyKey() {
        return propertyKey;
    }

    public float fromValue() {
        return fromValue;
    }

    public float toValue() {
        return toValue;
    }

    public float currentValue() {
        return currentValue;
    }

    public void update(float alpha) {
        if (alpha <= 0.0f) {
            currentValue = fromValue;
        } else if (alpha >= 1.0f) {
            currentValue = toValue;
        } else {
            currentValue = fromValue + (toValue - fromValue) * alpha;
        }
    }
}
