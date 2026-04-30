package com.moud.client.fabric.scene.tween;

import com.moud.core.tween.EasingMode;
import com.moud.core.tween.TweenLoopMode;

public final class ActiveTween {

    private final long tweenId;
    private final long nodeId;
    private final TweenChannelState[] channels;
    private final float durationSeconds;
    private final EasingMode easing;
    private final TweenLoopMode loopMode;
    private final long startNanos;

    public ActiveTween(long tweenId, long nodeId, TweenChannelState[] channels,
                       float durationSeconds, EasingMode easing, TweenLoopMode loopMode,
                       long startNanos) {
        this.tweenId = tweenId;
        this.nodeId = nodeId;
        this.channels = channels;
        this.durationSeconds = Math.max(1.0e-4f, durationSeconds);
        this.easing = easing == null ? EasingMode.LINEAR : easing;
        this.loopMode = loopMode == null ? TweenLoopMode.ONCE : loopMode;
        this.startNanos = startNanos;
    }

    public long tweenId() {
        return tweenId;
    }

    public long nodeId() {
        return nodeId;
    }

    public TweenChannelState[] channels() {
        return channels;
    }

    public EasingMode easing() {
        return easing;
    }

    public TweenLoopMode loopMode() {
        return loopMode;
    }

    public float elapsedSeconds(long nowNanos) {
        return (nowNanos - startNanos) / 1.0e9f;
    }

    public float durationSeconds() {
        return durationSeconds;
    }
}
