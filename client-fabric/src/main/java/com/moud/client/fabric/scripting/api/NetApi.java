package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.scripting.LuauCallback;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.net.script.ScriptPayload;
import java.util.HashMap;
import java.util.Map;

public final class NetApi {
    private final MessagingApi messaging;
    private final Map<String, LuauCallback> handlersByTopic = new HashMap<>();

    public NetApi(MessagingApi messaging) {
        this.messaging = messaging;
    }

    public void on(String topic, LuauCallback handler) {
        if (messaging == null || topic == null || topic.isBlank() || handler == null) return;
        LuauCallback previous = handlersByTopic.put(topic, handler);
        if (previous != null) previous.release();
        messaging.onMessage(topic, (nodeId, t, bytes) -> {
            Object payload;
            try { payload = ScriptPayload.decode(bytes); } catch (Exception e) {
                ClientDebugLog.error("NetApi", "decode failed for topic " + t + ": " + e.getMessage(), e);
                return;
            }
            LuauCallback current = handlersByTopic.get(t);
            if (current != null) current.invoke(payload);
        });
    }

    public void send(String topic, Map<String, Object> payload, Map<String, Object> opts) {
        if (messaging == null || topic == null || topic.isBlank()) return;
        boolean reliable = opts == null || !opts.containsKey("reliable") || Boolean.TRUE.equals(opts.get("reliable"));
        byte[] bytes;
        try { bytes = ScriptPayload.encode(payload); } catch (Exception e) {
            ClientDebugLog.error("NetApi", "encode failed: " + e.getMessage(), e);
            return;
        }
        messaging.sendToServer(topic, bytes, reliable);
    }

    public void clear(String topic) {
        if (messaging == null || topic == null) return;
        LuauCallback cb = handlersByTopic.remove(topic);
        if (cb != null) cb.release();
        messaging.clearHandler(topic);
    }

    public void dispose() {
        for (LuauCallback cb : handlersByTopic.values()) {
            if (cb != null) cb.release();
        }
        handlersByTopic.clear();
    }
}
