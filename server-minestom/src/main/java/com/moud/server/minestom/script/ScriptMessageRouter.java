package com.moud.server.minestom.script;

import com.moud.core.scene.Node;
import com.moud.net.protocol.ScriptMessage;
import com.moud.server.minestom.engine.ServerScene;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptMessageRouter {

    public interface Handler {
        void handle(UUID senderUuid, long nodeId, String topic, byte[] payload);
    }

    public interface AuthorityCheck {
        boolean canSend(UUID senderUuid, Node node, String topic);
    }

    public interface OutboundSink {
        void send(UUID target, ScriptMessage message);
    }

    private record Key(long nodeId, String topic) {}

    private static final double DEFAULT_RATE_PER_SEC = 20.0;
    private static final double DEFAULT_BURST = 40.0;

    private final ConcurrentHashMap<Key, Handler> handlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, ScriptMessageSchema> schemas = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Bucket.K, Bucket> buckets = new ConcurrentHashMap<>();
    private final AuthorityCheck authority;
    private final OutboundSink outbound;

    private double ratePerSec = DEFAULT_RATE_PER_SEC;
    private double burst = DEFAULT_BURST;

    public ScriptMessageRouter(AuthorityCheck authority, OutboundSink outbound) {
        this.authority = authority;
        this.outbound = outbound;
    }

    public void setDefaultRate(double perSec, double burst) {
        this.ratePerSec = Math.max(0.1, perSec);
        this.burst = Math.max(1.0, burst);
    }

    public void register(long nodeId, String topic, ScriptMessageSchema schema, Handler handler) {
        if (nodeId <= 0L || topic == null || topic.isBlank() || handler == null) return;
        Key k = new Key(nodeId, topic);
        handlers.put(k, handler);
        schemas.put(k, schema == null ? ScriptMessageSchema.ANY : schema);
    }

    public void unregister(long nodeId, String topic) {
        Key k = new Key(nodeId, topic);
        handlers.remove(k);
        schemas.remove(k);
    }

    public void unregisterAll(long nodeId) {
        handlers.keySet().removeIf(k -> k.nodeId() == nodeId);
        schemas.keySet().removeIf(k -> k.nodeId() == nodeId);
    }

    public void clearPlayer(UUID uuid) {
        buckets.keySet().removeIf(k -> uuid.equals(k.uuid()));
    }

    public void clear() {
        handlers.clear();
        schemas.clear();
        buckets.clear();
    }

    public void sendToClient(UUID target, long nodeId, String topic, byte[] payload, boolean reliable) {
        if (target == null || outbound == null) return;
        int flags = reliable ? ScriptMessage.FLAG_RELIABLE : 0;
        outbound.send(target, new ScriptMessage(ScriptMessage.DIR_S2C, nodeId, topic, flags, payload));
    }

    public void onInbound(UUID senderUuid, ServerScene scene, ScriptMessage msg) {
        if (msg == null || senderUuid == null) return;
        if (msg.direction() != ScriptMessage.DIR_C2S) return;

        Node node = scene == null ? null : scene.engine().sceneTree().getNode(msg.nodeId());
        if (node == null) return;

        if (authority != null && !authority.canSend(senderUuid, node, msg.topic())) {
            return;
        }

        Bucket.K bk = new Bucket.K(senderUuid, msg.topic());
        Bucket bucket = buckets.computeIfAbsent(bk, k -> new Bucket(ratePerSec, burst));
        if (!bucket.tryConsume(1.0)) return;

        Key k = new Key(msg.nodeId(), msg.topic());
        ScriptMessageSchema schema = schemas.getOrDefault(k, ScriptMessageSchema.NONE);
        if (!schema.validate(msg.payload())) return;

        Handler handler = handlers.get(k);
        if (handler == null) return;

        handler.handle(senderUuid, msg.nodeId(), msg.topic(), msg.payload());
    }

    private static final class Bucket {
        record K(UUID uuid, String topic) {}

        private final double ratePerSec;
        private final double capacity;
        private double tokens;
        private long lastNanos;

        Bucket(double ratePerSec, double capacity) {
            this.ratePerSec = ratePerSec;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastNanos = System.nanoTime();
        }

        synchronized boolean tryConsume(double cost) {
            long now = System.nanoTime();
            double elapsed = (now - lastNanos) * 1e-9;
            lastNanos = now;
            tokens = Math.min(capacity, tokens + elapsed * ratePerSec);
            if (tokens >= cost) {
                tokens -= cost;
                return true;
            }
            return false;
        }
    }
}
