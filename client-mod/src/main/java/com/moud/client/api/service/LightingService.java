package com.moud.client.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.api.math.Conversion;
import com.moud.client.lighting.ClientLightingService;
import net.minecraft.client.MinecraftClient;
import org.graalvm.polyglot.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class LightingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LightingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};

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
            Map<String, Object> data = MAPPER.readValue(eventData, MAP_TYPE_REFERENCE);

            switch (eventName) {
                case "lighting:operation": {
                    String operation = (String) data.get("operation");
                    Map<String, Object> lightData = (Map<String, Object>) data.get("light");
                    if (operation == null || lightData == null) return;

                    switch (operation) {
                        case "create", "update" -> lightingService.handleCreateOrUpdateLight(lightData);
                        case "remove" -> {
                            long id = Conversion.toLong(lightData.get("id"));
                            lightingService.handleRemoveLight(id);
                        }
                    }
                    break;
                }
                case "lighting:sync":
                    lightingService.handleLightSync(data);
                    break;
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