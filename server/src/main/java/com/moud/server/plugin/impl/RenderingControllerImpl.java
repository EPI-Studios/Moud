package com.moud.server.plugin.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.RenderingController;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class RenderingControllerImpl implements RenderingController {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderingControllerImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String encode(Map<String, Object> payload) {
        try {
            return payload.isEmpty() ? "" : MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to encode rendering payload", e);
            return "";
        }
    }

    private void broadcast(String event, Map<String, Object> payload) {
        ServerNetworkManager manager = ServerNetworkManager.getInstance();
        if (manager != null) {
            manager.broadcast(new com.moud.network.MoudPackets.ClientboundScriptEventPacket(event, encode(payload)));
        }
    }

    private void send(PlayerContext playerContext, String event, Map<String, Object> payload) {
        ServerNetworkManager manager = ServerNetworkManager.getInstance();
        Player player = playerContext.player();
        if (manager != null && player != null) {
            manager.sendScriptEvent(player, event, encode(payload));
        }
    }

    @Override
    public void applyPostEffect(String effectId) {
        broadcast("rendering:post:apply", Map.of("id", effectId));
    }

    @Override
    public void removePostEffect(String effectId) {
        broadcast("rendering:post:remove", Map.of("id", effectId));
    }

    @Override
    public void clearPostEffects() {
        broadcast("rendering:post:clear", Map.of());
    }

    @Override
    public void toast(PlayerContext player, String title, String body) {
        send(player, "ui:toast", new HashMap<>(Map.of(
                "title", title == null ? "" : title,
                "body", body == null ? "" : body
        )));
    }
}
