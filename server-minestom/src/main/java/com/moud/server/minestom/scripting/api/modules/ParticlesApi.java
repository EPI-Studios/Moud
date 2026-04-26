package com.moud.server.minestom.scripting.api.modules;

import com.moud.core.scripts.luau.LuauExport;
import com.moud.net.script.ScriptPayload;
import com.moud.server.minestom.script.ScriptMessageRouter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.graalvm.polyglot.HostAccess;

@LuauExport(name = "ParticlesApi", doc = "Particle effect spawning, burst, and emitter parameter control.")
public final class ParticlesApi {
    public static final String TOPIC = "__particle";
    public static final String ACTION_EMIT = "emit";
    public static final String ACTION_BURST = "burst";
    public static final String ACTION_RESTART = "restart";
    public static final String ACTION_SET_EMITTING = "set_emitting";
    public static final String ACTION_SET_RATE = "set_rate";
    public static final String ACTION_SET_LIFETIME = "set_lifetime";
    public static final String ACTION_MOVE_TO = "move_to";
    public static final String ACTION_CLEAR_MOVE = "clear_move";
    public static final String ACTION_EMIT_AT = "emit_at";

    private final ScriptMessageRouter router;
    private final Iterable<UUID> allPlayers;

    public ParticlesApi(ScriptMessageRouter router, Iterable<UUID> allPlayers) {
        this.router = router;
        this.allPlayers = allPlayers;
    }

    @HostAccess.Export
    @LuauExport
    public void emit(long nodeId, int count) {
        broadcast(nodeId, payload(ACTION_EMIT, "count", Math.max(0, count)));
    }

    @HostAccess.Export
    @LuauExport
    public void burst(long nodeId) {
        broadcast(nodeId, payload(ACTION_BURST, null, 0));
    }

    @HostAccess.Export
    @LuauExport
    public void restart(long nodeId) {
        broadcast(nodeId, payload(ACTION_RESTART, null, 0));
    }

    @HostAccess.Export
    @LuauExport
    public void setEmitting(long nodeId, boolean emitting) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", ACTION_SET_EMITTING);
        data.put("emitting", emitting);
        broadcast(nodeId, ScriptPayload.encode(data));
    }

    @HostAccess.Export
    @LuauExport
    public void setRate(long nodeId, double rate) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", ACTION_SET_RATE);
        data.put("rate", rate);
        broadcast(nodeId, ScriptPayload.encode(data));
    }

    @HostAccess.Export
    @LuauExport
    public void setLifetime(long nodeId, double lifetime) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", ACTION_SET_LIFETIME);
        data.put("lifetime", lifetime);
        broadcast(nodeId, ScriptPayload.encode(data));
    }

    @HostAccess.Export
    @LuauExport
    public void moveTo(long nodeId, double x, double y, double z) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", ACTION_MOVE_TO);
        data.put("x", x);
        data.put("y", y);
        data.put("z", z);
        broadcast(nodeId, ScriptPayload.encode(data));
    }

    @HostAccess.Export
    @LuauExport
    public void emitAt(long nodeId, double x, double y, double z, int count) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", ACTION_EMIT_AT);
        data.put("x", x);
        data.put("y", y);
        data.put("z", z);
        data.put("count", (long) Math.max(0, count));
        broadcast(nodeId, ScriptPayload.encode(data));
    }

    @HostAccess.Export
    @LuauExport
    public void clearMoveTo(long nodeId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", ACTION_CLEAR_MOVE);
        broadcast(nodeId, ScriptPayload.encode(data));
    }

    private void broadcast(long nodeId, byte[] payload) {
        if (router == null || allPlayers == null || nodeId <= 0L) return;
        for (UUID uuid : allPlayers) {
            router.sendToClient(uuid, nodeId, TOPIC, payload, true);
        }
    }

    private static byte[] payload(String action, String extraKey, int extraValue) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", action);
        if (extraKey != null) data.put(extraKey, (long) extraValue);
        return ScriptPayload.encode(data);
    }
}
