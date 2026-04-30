package com.moud.net.protocol;

import com.moud.core.tween.EasingMode;
import com.moud.core.tween.TweenLoopMode;
import java.util.List;

public record TweenStart(
        long tweenId,
        long nodeId,
        List<TweenChannel> channels,
        float durationSeconds,
        EasingMode easing,
        TweenLoopMode loopMode,
        long startTimeMillis
) implements Message {

    public record TweenChannel(String propertyKey, float fromValue, float toValue) {
    }

    @Override
    public MessageType type() {
        return MessageType.TWEEN_START;
    }
}
