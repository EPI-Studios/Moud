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
        this.lightingService.initialize();
        LOGGER.info("LightingService initialized");
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void handleNetworkEvent(String eventName, String eventData) {
        LOGGER.debug("Handling lighting event: {} with data: {}", eventName, eventData);

        try {
            Map<String, Object> data = MAPPER.readValue(eventData, MAP_TYPE_REFERENCE);

            switch (eventName) {
                case "lighting:operation": {
                    String operation = (String) data.get("operation");
                    Map<String, Object> lightData = (Map<String, Object>) data.get("light");

                    LOGGER.info("Processing lighting operation: {} with light data: {}", operation, lightData);

                    if (operation == null || lightData == null) {
                        LOGGER.warn("Invalid lighting operation data - operation: {}, lightData: {}", operation, lightData);
                        return;
                    }

                    switch (operation) {
                        case "create", "update" -> {
                            LOGGER.info("Creating/updating light with data: {}", lightData);
                            lightingService.handleCreateOrUpdateLight(lightData);
                        }
                        case "remove" -> {
                            long id = Conversion.toLong(lightData.get("id"));
                            LOGGER.info("Removing light with id: {}", id);
                            lightingService.handleRemoveLight(id);
                        }
                        default -> LOGGER.warn("Unknown lighting operation: {}", operation);
                    }
                    break;
                }
                case "lighting:sync":
                    LOGGER.info("Syncing lights with data: {}", data);
                    lightingService.handleLightSync(data);
                    break;
                default:
                    LOGGER.warn("Unknown lighting event: {}", eventName);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to handle lighting network event: {} with data: {}", eventName, eventData, e);
        }
    }

    public void tick() {
        try {
            lightingService.tick();
        } catch (Exception e) {
            LOGGER.error("Error during lighting service tick", e);
        }
    }

    public void cleanUp() {
        try {
            lightingService.cleanup();
        } catch (Exception e) {
            LOGGER.error("Error during lighting service cleanup", e);
        }
        jsContext = null;
        LOGGER.info("LightingService cleaned up");
    }
}