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

    private final ClientLightingService internalLightingService;
    private Context jsContext;

    public LightingService() {
        this.internalLightingService = ClientLightingService.getInstance();
        this.internalLightingService.initialize();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void handleNetworkEvent(String eventName, String eventData) {
        MinecraftClient.getInstance().execute(() -> {
            try {
                Map<String, Object> data = MAPPER.readValue(eventData, MAP_TYPE_REFERENCE);

                switch (eventName) {
                    case "lighting:operation": {
                        String operation = (String) data.get("operation");
                        Map<String, Object> lightData = (Map<String, Object>) data.get("light");

                        if (operation == null || lightData == null) {
                            LOGGER.warn("Invalid lighting operation data.");
                            return;
                        }

                        switch (operation) {
                            case "create", "update" -> internalLightingService.handleCreateOrUpdateLight(lightData);
                            case "remove" -> {
                                long id = Conversion.toLong(lightData.get("id"));
                                internalLightingService.handleRemoveLight(id);
                            }
                            default -> LOGGER.warn("Unknown lighting operation: {}", operation);
                        }
                        break;
                    }
                    case "lighting:sync": {
                        internalLightingService.handleLightSync(data);
                        break;
                    }
                    default:
                        LOGGER.warn("Unknown lighting event: {}", eventName);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to handle lighting network event: {} with data: {}", eventName, eventData, e);
            }
        });
    }

    public void tick() {

        internalLightingService.tick();
    }

    public void cleanUp() {
        internalLightingService.cleanup();
        jsContext = null;
    }
}
