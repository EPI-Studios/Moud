package com.moud.server.minestom.scripting.api.modules;

import com.moud.core.scripts.luau.LuauExport;
import com.moud.net.script.ScriptPayload;
import com.moud.server.minestom.script.ScriptMessageRouter;
import com.moud.server.minestom.script.ScriptMessageSchema;
import java.util.UUID;
import org.graalvm.polyglot.HostAccess;

@LuauExport(name = "MessagingApi", doc = "Inter-script messaging: register handlers and send/broadcast payloads to clients.")
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
    @LuauExport
    public void onMessage(String topic, ScriptMessageRouter.Handler handler) {
        router.register(selfId, topic, ScriptMessageSchema.ANY, handler);
    }

    @HostAccess.Export
    @LuauExport
    public void onMessageWithSchema(String topic, ScriptMessageSchema schema, ScriptMessageRouter.Handler handler) {
        router.register(selfId, topic, schema, handler);
    }

    @HostAccess.Export
    @LuauExport
    public void clearHandler(String topic) {
        router.unregister(selfId, topic);
    }

    @HostAccess.Export
    @LuauExport
    public byte[] encode(Object value) {
        return ScriptPayload.encode(value);
    }

    @HostAccess.Export
    @LuauExport
    public Object decode(byte[] payload) {
        return ScriptPayload.decode(payload);
    }

    @HostAccess.Export
    @LuauExport
    public void send(String uuid, String topic, Object payload, boolean reliable) {
        sendToClient(uuid, topic, ScriptPayload.encode(payload), reliable);
    }

    @HostAccess.Export
    @LuauExport
    public void broadcastValue(String topic, Object payload, boolean reliable) {
        broadcast(topic, ScriptPayload.encode(payload), reliable);
    }

    @HostAccess.Export
    @LuauExport
    public void sendToClient(String uuid, String topic, byte[] payload, boolean reliable) {
        try {
            router.sendToClient(UUID.fromString(uuid), selfId, topic, payload, reliable);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @HostAccess.Export
    @LuauExport
    public void broadcast(String topic, byte[] payload, boolean reliable) {
        if (allPlayers == null) return;
        for (UUID uuid : allPlayers) {
            router.sendToClient(uuid, selfId, topic, payload, reliable);
        }
    }
}
