package com.moud.client.api.service;

import com.moud.client.lighting.ClientLightingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.polyglot.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class LightingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LightingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClientLightingService lightingService;
    private Context jsContext;

    public LightingService() {
        this.lightingService = new ClientLightingService();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void handleNetworkEvent(String eventName, String eventData) {
        try {
            Map<String, Object> data = MAPPER.readValue(eventData, Map.class);
            switch (eventName) {
                case "lighting:operation" -> lightingService.handleLightOperation(data);
                case "lighting:sync" -> lightingService.handleLightSync(data);
                case "lighting:remove" -> lightingService.handleRemoveLight(data);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to handle lighting network event: {}", eventName, e);
        }
    }

    public void tick() {
        lightingService.tick();
    }

    public void cleanUp() {
        lightingService.cleanup();
        jsContext = null;
        LOGGER.info("LightingService cleaned up");
    }
}