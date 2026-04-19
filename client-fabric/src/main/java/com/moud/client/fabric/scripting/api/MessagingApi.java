package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.net.ClientSessionBus;
import com.moud.net.protocol.ScriptMessage;
import com.moud.net.script.ScriptPayload;
import com.moud.net.session.Session;
import com.moud.net.transport.Lane;
import java.util.HashMap;
import java.util.Map;

public final class MessagingApi {

    public interface Handler {
        void handle(long nodeId, String topic, byte[] payload);
    }

    private final long selfId;
    private final Map<String, Handler> handlers = new HashMap<>();

    public MessagingApi(long selfId) {
        this.selfId = selfId;
    }

    public void onMessage(String topic, Handler handler) {
        if (topic == null || topic.isBlank() || handler == null) return;
        handlers.put(topic, handler);
    }

    public void clearHandler(String topic) {
        if (topic == null) return;
        handlers.remove(topic);
    }

    public byte[] encode(Object value) {
        return ScriptPayload.encode(value);
    }

    public Object decode(byte[] payload) {
        return ScriptPayload.decode(payload);
    }

    public void send(String topic, Object payload, boolean reliable) {
        sendToServer(topic, ScriptPayload.encode(payload), reliable);
    }

    public void sendToServer(String topic, byte[] payload, boolean reliable) {
        Session session = ClientSessionBus.get();
        if (session == null || topic == null || topic.isBlank()) return;
        int flags = reliable ? ScriptMessage.FLAG_RELIABLE : 0;
        byte[] safe = payload == null ? new byte[0] : payload;
        if (safe.length > ScriptMessage.MAX_PAYLOAD_BYTES) return;
        session.send(
                reliable ? Lane.EVENTS : Lane.INPUT,
                new ScriptMessage(ScriptMessage.DIR_C2S, selfId, topic, flags, safe)
        );
    }

    public void dispatchInbound(ScriptMessage msg) {
        if (msg == null || msg.nodeId() != selfId) return;
        Handler h = handlers.get(msg.topic());
        if (h != null) h.handle(msg.nodeId(), msg.topic(), msg.payload());
    }
}
