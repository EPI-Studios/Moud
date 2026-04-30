package com.moud.client.fabric.scene.tween;

import com.moud.client.fabric.scene.interp.NodeInterpolatorRegistry;
import com.moud.core.interp.InterpProperty;
import com.moud.core.tween.Easing;
import com.moud.core.tween.EasingMode;
import com.moud.core.tween.TweenLoopMode;
import com.moud.net.protocol.TweenCancel;
import com.moud.net.protocol.TweenStart;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientTweenPlayer {

    private static final ClientTweenPlayer INSTANCE = new ClientTweenPlayer();

    private final Object2ObjectOpenHashMap<Long, ActiveTween> activeById = new Object2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Set<Long>> tweenIdsByNode = new Long2ObjectOpenHashMap<>();
    private final Object lock = new Object();

    private ClientTweenPlayer() {
    }

    public static ClientTweenPlayer get() {
        return INSTANCE;
    }

    public void onStart(TweenStart packet) {
        if (packet == null) {
            return;
        }
        List<TweenStart.TweenChannel> declared = packet.channels();
        if (declared == null || declared.isEmpty()) {
            return;
        }

        TweenChannelState[] channels = new TweenChannelState[declared.size()];
        for (int i = 0; i < declared.size(); i++) {
            TweenStart.TweenChannel ch = declared.get(i);
            channels[i] = new TweenChannelState(ch.propertyKey(), ch.fromValue(), ch.toValue());
        }

        ActiveTween tween = new ActiveTween(
                packet.tweenId(),
                packet.nodeId(),
                channels,
                packet.durationSeconds(),
                packet.easing(),
                packet.loopMode(),
                System.nanoTime()
        );

        synchronized (lock) {
            activeById.put(packet.tweenId(), tween);
            tweenIdsByNode.computeIfAbsent(packet.nodeId(), k -> new HashSet<>()).add(packet.tweenId());
        }
    }

    public void onCancel(TweenCancel packet) {
        if (packet == null) {
            return;
        }
        long nodeId = packet.nodeId();
        synchronized (lock) {
            Set<Long> ids = tweenIdsByNode.get(nodeId);
            if (ids == null || ids.isEmpty()) {
                return;
            }
            if (packet.cancelsAll()) {
                for (Long id : new ArrayList<>(ids)) {
                    activeById.remove(id);
                }
                tweenIdsByNode.remove(nodeId);
                return;
            }
            Set<String> keys = new HashSet<>(packet.propertyKeys());
            ArrayList<Long> toRemove = new ArrayList<>();
            for (Long id : ids) {
                ActiveTween tween = activeById.get(id);
                if (tween == null) {
                    toRemove.add(id);
                    continue;
                }
                if (touchesAny(tween, keys)) {
                    activeById.remove(id);
                    toRemove.add(id);
                }
            }
            ids.removeAll(toRemove);
            if (ids.isEmpty()) {
                tweenIdsByNode.remove(nodeId);
            }
        }
    }

    public boolean isTweening(long nodeId, String propertyKey) {
        synchronized (lock) {
            Set<Long> ids = tweenIdsByNode.get(nodeId);
            if (ids == null || ids.isEmpty()) {
                return false;
            }
            for (Long id : ids) {
                ActiveTween tween = activeById.get(id);
                if (tween == null) {
                    continue;
                }
                if (propertyKey == null) {
                    return true;
                }
                for (TweenChannelState ch : tween.channels()) {
                    if (propertyKey.equals(ch.propertyKey())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public void tick(long nowNanos) {
        ArrayList<ActiveTween> snapshot;
        synchronized (lock) {
            if (activeById.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(activeById.values());
        }
        ArrayList<Long> finished = new ArrayList<>();
        for (ActiveTween tween : snapshot) {
            float elapsed = tween.elapsedSeconds(nowNanos);
            float duration = tween.durationSeconds();
            float t01 = elapsed / duration;
            boolean done = false;

            switch (tween.loopMode()) {
                case ONCE -> {
                    if (t01 >= 1.0f) {
                        t01 = 1.0f;
                        done = true;
                    }
                }
                case LOOP -> t01 = (float) (t01 - Math.floor(t01));
                case PING_PONG -> {
                    float wrapped = (float) (t01 - 2.0 * Math.floor(t01 * 0.5));
                    t01 = wrapped > 1.0f ? 2.0f - wrapped : wrapped;
                }
            }

            float eased = Easing.apply(tween.easing(), t01);
            for (TweenChannelState ch : tween.channels()) {
                ch.update(eased);
                applyToInterpolator(tween.nodeId(), ch, nowNanos);
            }

            if (done) {
                finished.add(tween.tweenId());
            }
        }

        if (!finished.isEmpty()) {
            synchronized (lock) {
                for (Long id : finished) {
                    ActiveTween tween = activeById.remove(id);
                    if (tween == null) {
                        continue;
                    }
                    Set<Long> ids = tweenIdsByNode.get(tween.nodeId());
                    if (ids != null) {
                        ids.remove(id);
                        if (ids.isEmpty()) {
                            tweenIdsByNode.remove(tween.nodeId());
                        }
                    }
                }
            }
        }
    }

    public void clear() {
        synchronized (lock) {
            activeById.clear();
            tweenIdsByNode.clear();
        }
    }

    private static boolean touchesAny(ActiveTween tween, Set<String> keys) {
        for (TweenChannelState ch : tween.channels()) {
            if (keys.contains(ch.propertyKey())) {
                return true;
            }
        }
        return false;
    }

    private static void applyToInterpolator(long nodeId, TweenChannelState ch, long nowNanos) {
        InterpProperty mapped = InterpProperty.fromKey(ch.propertyKey());
        if (mapped == null) {
            return;
        }
        NodeInterpolatorRegistry registry = NodeInterpolatorRegistry.get();
        registry.pushTweenedSample(nodeId, mapped, ch.currentValue(), nowNanos);
    }
}
