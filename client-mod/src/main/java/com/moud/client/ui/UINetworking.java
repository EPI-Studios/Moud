package com.moud.client.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.client.network.ClientNetworkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class UINetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger(UINetworking.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void sendUIInteraction(String elementId, String action, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("elementId", elementId);
        payload.put("action", action);
        payload.put("data", data);

        try {
            String jsonData = OBJECT_MAPPER.writeValueAsString(payload);
            ClientNetworkManager.sendToServer("ui:interaction", jsonData);
            LOGGER.debug("Sent UI interaction: {} - {}", elementId, action);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize UI interaction data", e);
        }
    }

    public static void sendUIClick(String elementId, double mouseX, double mouseY, int button) {
        Map<String, Object> data = new HashMap<>();
        data.put("mouseX", mouseX);
        data.put("mouseY", mouseY);
        data.put("button", button);
        sendUIInteraction(elementId, "click", data);
    }

    public static void sendUIValue(String elementId, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put("value", value);
        sendUIInteraction(elementId, "value_change", data);
    }
}