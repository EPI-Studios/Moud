package com.moud.server.minestom.scripting.api.modules;

import com.moud.core.scripts.luau.LuauExport;
import com.moud.core.tween.EasingMode;
import com.moud.core.tween.TweenLoopMode;
import com.moud.net.protocol.TweenCancel;
import com.moud.net.protocol.TweenStart;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.scripting.player.PlayerNetworkSink;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@LuauExport(name = "TweenApi", doc = "Server-authoritative tween primitive. Server emits one packet per tween; clients play it out at full render rate.")
public final class TweenApi {

    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    private final ServerScene scene;
    private final PlayerNetworkSink sink;

    public TweenApi(ServerScene scene, PlayerNetworkSink sink) {
        this.scene = scene;
        this.sink = sink;
    }

    @HostAccess.Export
    @LuauExport
    public long tween(long nodeId, Value targets, double durationSeconds, String easing, String loopMode) {
        if (nodeId <= 0L || targets == null || !targets.hasMembers()) {
            return 0L;
        }
        List<TweenStart.TweenChannel> channels = readChannels(targets);
        if (channels.isEmpty()) {
            return 0L;
        }
        long tweenId = NEXT_ID.getAndIncrement();
        TweenStart packet = new TweenStart(
                tweenId,
                nodeId,
                channels,
                (float) Math.max(1.0e-3, durationSeconds),
                EasingMode.parse(easing, EasingMode.LINEAR),
                TweenLoopMode.parse(loopMode, TweenLoopMode.ONCE),
                System.currentTimeMillis()
        );
        if (sink != null) {
            sink.broadcastTweenStart(scene, packet);
        }
        return tweenId;
    }

    @HostAccess.Export
    @LuauExport
    public void cancel(long nodeId) {
        if (sink != null) {
            sink.broadcastTweenCancel(scene, new TweenCancel(nodeId, List.of()));
        }
    }

    @HostAccess.Export
    @LuauExport
    public void cancelProperty(long nodeId, String propertyKey) {
        if (sink == null) {
            return;
        }
        if (propertyKey == null || propertyKey.isBlank()) {
            cancel(nodeId);
            return;
        }
        sink.broadcastTweenCancel(scene, new TweenCancel(nodeId, List.of(propertyKey)));
    }

    private static List<TweenStart.TweenChannel> readChannels(Value targets) {
        ArrayList<TweenStart.TweenChannel> channels = new ArrayList<>();
        for (String key : targets.getMemberKeys()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Value member = targets.getMember(key);
            if (member == null || !member.isNumber()) {
                continue;
            }
            float to = (float) member.asDouble();
            if (!Float.isFinite(to)) {
                continue;
            }
            channels.add(new TweenStart.TweenChannel(key, 0.0f, to));
        }
        return channels;
    }
}
