package com.moud.server.rendering;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PostEffectStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostEffectStateManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final PostEffectStateManager INSTANCE = new PostEffectStateManager();
    private final Map<String, Map<String, Object>> uniformsByEffect = new ConcurrentHashMap<>();

    private PostEffectStateManager() {
    }

    public static PostEffectStateManager getInstance() {
        return INSTANCE;
    }

    public void apply(String effectId, Map<String, Object> uniforms) {
        if (effectId == null || effectId.isBlank()) {
            return;
        }
        uniformsByEffect.put(effectId, uniforms == null ? Collections.emptyMap() : new ConcurrentHashMap<>(uniforms));
        broadcastApply(effectId, uniformsByEffect.get(effectId));
    }

    public void remove(String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return;
        }
        uniformsByEffect.remove(effectId);
        broadcastRemove(effectId);
    }

    public void syncToPlayer(Player player) {
        if (player == null) return;
        uniformsByEffect.forEach((effectId, uniforms) -> sendState(player, effectId, uniforms));
    }

    private void broadcastApply(String effectId, Map<String, Object> uniforms) {
        ServerNetworkManager manager = ServerNetworkManager.getInstance();
        if (manager == null) return;
        manager.broadcast(new com.moud.network.MoudPackets.ClientboundScriptEventPacket(
                "rendering:post:apply", encode(Map.of("id", effectId))
        ));
        manager.broadcast(new com.moud.network.MoudPackets.ClientboundScriptEventPacket(
                "rendering:post:set_uniforms", encode(Map.of("id", effectId, "uniforms", uniforms))
        ));
    }

    private void broadcastRemove(String effectId) {
        ServerNetworkManager manager = ServerNetworkManager.getInstance();
        if (manager == null) return;
        manager.broadcast(new com.moud.network.MoudPackets.ClientboundScriptEventPacket(
                "rendering:post:remove", encode(Map.of("id", effectId))
        ));
    }

    private void sendState(Player player, String effectId, Map<String, Object> uniforms) {
        ServerNetworkManager manager = ServerNetworkManager.getInstance();
        if (manager == null) return;
        manager.sendScriptEvent(player, "rendering:post:apply", encode(Map.of("id", effectId)));
        manager.sendScriptEvent(player, "rendering:post:set_uniforms", encode(Map.of("id", effectId, "uniforms", uniforms)));
    }

    private String encode(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            LOGGER.error("Failed to encode post-effect payload", e);
            return "";
        }
    }
}
