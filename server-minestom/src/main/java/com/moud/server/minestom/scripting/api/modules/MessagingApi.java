package com.moud.server.minestom.scripting.api.modules;

import com.moud.net.script.ScriptPayload;
import com.moud.server.minestom.script.ScriptMessageRouter;
import com.moud.server.minestom.script.ScriptMessageSchema;
import java.util.UUID;
import org.graalvm.polyglot.HostAccess;

public final class MessagingApi {
    private final ScriptMessageRouter router;
    private final long selfId;
    private final Iterable<UUID> allPlayers;

    public MessagingApi(ScriptMessageRouter router, long selfId, Iterable<UUID> allPlayers) {
        this.router = router;
        this.selfId = selfId;
        this.allPlayers = allPlayers;
    }

    @HostAccess.Export
    public void onMessage(String topic, ScriptMessageRouter.Handler handler) {
        router.register(selfId, topic, ScriptMessageSchema.ANY, handler);
    }

    @HostAccess.Export
    public void onMessageWithSchema(String topic, ScriptMessageSchema schema, ScriptMessageRouter.Handler handler) {
        router.register(selfId, topic, schema, handler);
    }

    @HostAccess.Export
    public void clearHandler(String topic) {
        router.unregister(selfId, topic);
    }

    @HostAccess.Export
    public byte[] encode(Object value) {
        return ScriptPayload.encode(value);
    }

    @HostAccess.Export
    public Object decode(byte[] payload) {
        return ScriptPayload.decode(payload);
    }

    @HostAccess.Export
    public void send(String uuid, String topic, Object payload, boolean reliable) {
        sendToClient(uuid, topic, ScriptPayload.encode(payload), reliable);
    }

    @HostAccess.Export
    public void broadcastValue(String topic, Object payload, boolean reliable) {
        broadcast(topic, ScriptPayload.encode(payload), reliable);
    }

    @HostAccess.Export
    public void sendToClient(String uuid, String topic, byte[] payload, boolean reliable) {
        try {
            router.sendToClient(UUID.fromString(uuid), selfId, topic, payload, reliable);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @HostAccess.Export
    public void broadcast(String topic, byte[] payload, boolean reliable) {
        if (allPlayers == null) return;
        for (UUID uuid : allPlayers) {
            router.sendToClient(uuid, selfId, topic, payload, reliable);
        }
    }
}
