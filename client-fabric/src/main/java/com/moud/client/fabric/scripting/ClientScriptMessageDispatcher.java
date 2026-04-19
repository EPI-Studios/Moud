package com.moud.client.fabric.scripting;

import com.moud.client.fabric.render.scene.subrender.particle.ParticleApi;
import com.moud.client.fabric.scripting.api.MessagingApi;
import com.moud.net.protocol.ScriptMessage;
import com.moud.net.script.ScriptPayload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientScriptMessageDispatcher {
    private static final ConcurrentHashMap<Long, MessagingApi> byNodeId = new ConcurrentHashMap<>();

    private ClientScriptMessageDispatcher() {
    }

    public static void register(long nodeId, MessagingApi api) {
        if (nodeId > 0L && api != null) byNodeId.put(nodeId, api);
    }

    public static void unregister(long nodeId) {
        byNodeId.remove(nodeId);
    }

    public static void clear() {
        byNodeId.clear();
    }

    public static void dispatch(ScriptMessage msg) {
        if (msg == null) return;
        if ("__particle".equals(msg.topic())) {
            dispatchParticle(msg);
            return;
        }
        MessagingApi api = byNodeId.get(msg.nodeId());
        if (api != null) api.dispatchInbound(msg);
    }

    private static void dispatchParticle(ScriptMessage msg) {
        Object decoded;
        try {
            decoded = ScriptPayload.decode(msg.payload());
        } catch (RuntimeException ignored) {
            return;
        }
        if (!(decoded instanceof Map<?, ?> map)) return;
        Object action = map.get("action");
        if (!(action instanceof String actionStr)) return;
        long nodeId = msg.nodeId();
        switch (actionStr) {
            case "emit" -> {
                int count = asInt(map.get("count"), 0);
                if (count > 0) ParticleApi.emit(nodeId, count);
            }
            case "burst" -> ParticleApi.burst(nodeId);
            case "restart" -> ParticleApi.restart(nodeId);
            case "set_emitting" -> {
                Object emitting = map.get("emitting");
                if (emitting instanceof Boolean b) ParticleApi.setEmitting(nodeId, b);
            }
            case "set_rate" -> ParticleApi.setRate(nodeId, (float) asDouble(map.get("rate"), 0));
            case "set_lifetime" -> ParticleApi.setLifetime(nodeId, (float) asDouble(map.get("lifetime"), 1));
            case "move_to" -> ParticleApi.moveTo(nodeId,
                    asDouble(map.get("x"), 0),
                    asDouble(map.get("y"), 0),
                    asDouble(map.get("z"), 0));
            case "clear_move" -> ParticleApi.clearMoveTo(nodeId);
            case "emit_at" -> {
                int count = asInt(map.get("count"), 0);
                if (count > 0) ParticleApi.emitAt(nodeId,
                        asDouble(map.get("x"), 0),
                        asDouble(map.get("y"), 0),
                        asDouble(map.get("z"), 0),
                        count);
            }
            default -> {
            }
        }
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        return fallback;
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        return fallback;
    }
}
