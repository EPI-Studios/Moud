package com.moud.server.minestom.scripting.api.modules;

import com.moud.core.scripts.luau.LuauExport;
import com.moud.net.script.ScriptPayload;
import com.moud.server.minestom.script.MapSchema;
import com.moud.server.minestom.script.ScriptMessageRouter;
import com.moud.server.minestom.script.ScriptMessageSchema;
import com.moud.server.minestom.scripting.ScriptCallback;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

@LuauExport(name = "NetApi", doc = "Low-level networking helpers for scripts: register typed handlers, send to peers, and broadcast.")
public final class NetApi {
    private final ScriptMessageRouter router;
    private final long selfId;
    private final Iterable<UUID> allPlayers;

    public NetApi(ScriptMessageRouter router, long selfId, Iterable<UUID> allPlayers) {
        this.router = router;
        this.selfId = selfId;
        this.allPlayers = allPlayers;
    }

    @HostAccess.Export
    @LuauExport
    public void on(String topic, Map<String, Object> schema, ScriptCallback handler) {
        if (router == null || topic == null || topic.isBlank() || handler == null) return;
        ScriptMessageSchema validator = schema == null || schema.isEmpty()
                ? ScriptMessageSchema.ANY
                : new MapSchema(schemaToTypes(schema));
        router.register(selfId, topic, validator, (senderUuid, nodeId, t, bytes) -> {
            Object payload;
            try { payload = ScriptPayload.decode(bytes); } catch (Exception e) { return; }
            try { handler.invoke(senderUuid.toString(), payload); } catch (Exception ignored) {}
        });
    }

    @HostAccess.Export
    @LuauExport
    public void on(String topic, Value schema, Value handler) {
        if (router == null || topic == null || topic.isBlank() || handler == null || !handler.canExecute()) return;
        ScriptMessageSchema validator = schema == null || schema.isNull() || !schema.hasMembers()
                ? ScriptMessageSchema.ANY
                : new MapSchema(valueSchemaToTypes(schema));
        router.register(selfId, topic, validator, (senderUuid, nodeId, t, bytes) -> {
            Object payload;
            try { payload = ScriptPayload.decode(bytes); } catch (Exception e) { return; }
            try { handler.executeVoid(senderUuid.toString(), payload); } catch (Exception ignored) {}
        });
    }

    @HostAccess.Export
    @LuauExport
    public void sendTo(String uuid, String topic, Map<String, Object> payload, Map<String, Object> opts) {
        if (router == null || uuid == null || topic == null || topic.isBlank()) return;
        boolean reliable = readReliableMap(opts, true);
        byte[] bytes = ScriptPayload.encode(payload);
        try {
            router.sendToClient(UUID.fromString(uuid), selfId, topic, bytes, reliable);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @HostAccess.Export
    @LuauExport
    public void sendTo(String uuid, String topic, Value payload, Value opts) {
        if (router == null || uuid == null || topic == null || topic.isBlank()) return;
        boolean reliable = readReliableValue(opts, true);
        byte[] bytes = ScriptPayload.encode(valueToJava(payload));
        try {
            router.sendToClient(UUID.fromString(uuid), selfId, topic, bytes, reliable);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @HostAccess.Export
    @LuauExport
    public void broadcast(String topic, Map<String, Object> payload, Map<String, Object> opts) {
        if (router == null || topic == null || topic.isBlank() || allPlayers == null) return;
        boolean reliable = readReliableMap(opts, true);
        byte[] bytes = ScriptPayload.encode(payload);
        for (UUID uuid : allPlayers) router.sendToClient(uuid, selfId, topic, bytes, reliable);
    }

    @HostAccess.Export
    @LuauExport
    public void broadcast(String topic, Value payload, Value opts) {
        if (router == null || topic == null || topic.isBlank() || allPlayers == null) return;
        boolean reliable = readReliableValue(opts, true);
        byte[] bytes = ScriptPayload.encode(valueToJava(payload));
        for (UUID uuid : allPlayers) router.sendToClient(uuid, selfId, topic, bytes, reliable);
    }

    @HostAccess.Export
    @LuauExport
    public void clear(String topic) {
        if (router == null || topic == null) return;
        router.unregister(selfId, topic);
    }

    private static boolean readReliableMap(Map<String, Object> opts, boolean defaultValue) {
        if (opts == null || !opts.containsKey("reliable")) return defaultValue;
        Object v = opts.get("reliable");
        return v instanceof Boolean b ? b : defaultValue;
    }

    private static boolean readReliableValue(Value opts, boolean defaultValue) {
        if (opts == null || opts.isNull() || !opts.hasMembers()) return defaultValue;
        Value v = opts.getMember("reliable");
        return v == null ? defaultValue : v.asBoolean();
    }

    private static Map<String, String> schemaToTypes(Map<String, Object> schema) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> e : schema.entrySet()) {
            if (e.getValue() instanceof String type) result.put(e.getKey(), type);
        }
        return result;
    }

    private static Map<String, String> valueSchemaToTypes(Value schema) {
        Map<String, String> result = new HashMap<>();
        for (String key : schema.getMemberKeys()) {
            Value v = schema.getMember(key);
            if (v != null && v.isString()) result.put(key, v.asString());
        }
        return result;
    }

    private static Object valueToJava(Value v) {
        if (v == null || v.isNull()) return null;
        if (v.isBoolean()) return v.asBoolean();
        if (v.isString()) return v.asString();
        if (v.isNumber()) return v.fitsInLong() ? v.asLong() : v.asDouble();
        if (v.hasArrayElements()) {
            int n = (int) v.getArraySize();
            List<Object> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(valueToJava(v.getArrayElement(i)));
            return out;
        }
        if (v.hasMembers()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String k : v.getMemberKeys()) out.put(k, valueToJava(v.getMember(k)));
            return out;
        }
        return v.toString();
    }
}
