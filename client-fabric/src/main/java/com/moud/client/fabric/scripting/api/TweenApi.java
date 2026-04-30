package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.scene.tween.ClientTweenPlayer;
import com.moud.core.tween.EasingMode;
import com.moud.core.tween.TweenLoopMode;
import com.moud.net.protocol.TweenCancel;
import com.moud.net.protocol.TweenStart;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class TweenApi {

    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    public void tween(long nodeId, Map<String, Double> targets, double durationSeconds, String easing) {
        tween(nodeId, targets, durationSeconds, easing, "once");
    }

    public void tween(long nodeId, Map<String, Double> targets, double durationSeconds, String easing, String loopMode) {
        if (nodeId <= 0L || targets == null || targets.isEmpty()) {
            return;
        }
        TweenStart packet = build(nodeId, targets, durationSeconds, easing, loopMode);
        ClientTweenPlayer.get().onStart(packet);
    }

    public void cancel(long nodeId) {
        ClientTweenPlayer.get().onCancel(new TweenCancel(nodeId, List.of()));
    }

    public void cancel(long nodeId, String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) {
            cancel(nodeId);
            return;
        }
        ClientTweenPlayer.get().onCancel(new TweenCancel(nodeId, List.of(propertyKey)));
    }

    public boolean isTweening(long nodeId) {
        return ClientTweenPlayer.get().isTweening(nodeId, null);
    }

    public boolean isTweening(long nodeId, String propertyKey) {
        return ClientTweenPlayer.get().isTweening(nodeId, propertyKey);
    }

    private static TweenStart build(long nodeId, Map<String, Double> targets, double duration, String easing, String loopMode) {
        var channels = new java.util.ArrayList<TweenStart.TweenChannel>(targets.size());
        for (Map.Entry<String, Double> entry : targets.entrySet()) {
            String key = entry.getKey();
            Double value = entry.getValue();
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) {
                continue;
            }
            channels.add(new TweenStart.TweenChannel(key, 0.0f, value.floatValue()));
        }
        return new TweenStart(
                NEXT_ID.getAndIncrement(),
                nodeId,
                channels,
                (float) Math.max(1.0e-3, duration),
                EasingMode.parse(easing, EasingMode.LINEAR),
                TweenLoopMode.parse(loopMode, TweenLoopMode.ONCE),
                System.currentTimeMillis()
        );
    }
}
