package com.moud.client.api.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.moud.client.network.ClientNetworkManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkService.class);
    private static final Gson GSON = new Gson();

    private final Map<String, Value> eventHandlers = new ConcurrentHashMap<>();
    private Context jsContext;
    private LightingService lightingService;

    public NetworkService() {

    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
        LOGGER.debug("NetworkService received new GraalVM Context.");
    }

    public void setLightingService(LightingService lightingService) {
        this.lightingService = lightingService;
    }

    @HostAccess.Export
    public void sendToServer(String eventName, Object data) {
        String serializedData = serializeData(data);
        ClientNetworkManager.sendToServer(eventName, serializedData);
    }

    @HostAccess.Export
    public void on(String eventName, Value callback) {
        validateCallback(callback);
        eventHandlers.put(eventName, callback);
    }

    public void triggerEvent(String eventName, String eventData) {
        if (eventName.startsWith("lighting:") && lightingService != null) {
            lightingService.handleNetworkEvent(eventName, eventData);
            return;
        }

        Value handler = eventHandlers.get(eventName);
        if (handler != null && handler.canExecute()) {
            if (jsContext != null) {
                jsContext.enter();
                try {
                    Object finalData;

                    if (eventData == null || eventData.isEmpty()) {
                        finalData = null;
                    } else {
                        try {
                            finalData = GSON.fromJson(eventData, Object.class);
                        } catch (JsonSyntaxException e) {
                            LOGGER.warn("Failed to parse JSON for event '{}'. Passing raw string instead. JSON: {}", eventName, eventData, e);
                            finalData = eventData;
                        }
                    }
                    handler.execute(finalData);
                } catch (PolyglotException e) {
                    LOGGER.error("Error executing JavaScript network handler for event '{}': {}", eventName, e.getMessage());
                    if (e.isGuestException()) {
                        LOGGER.error("Guest stack trace:\n{}", (Object) e.getStackTrace());
                    }
                } catch (Exception e) {
                    LOGGER.error("Unexpected error executing JavaScript network handler for event '{}'", eventName, e);
                } finally {
                    jsContext.leave();
                }
            } else {
                LOGGER.warn("Cannot trigger network event '{}': JavaScript context is not initialized in NetworkService.", eventName);
            }
        }
    }

    private void validateCallback(Value callback) {
        if (!callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
        }
    }

    private String serializeData(Object data) {
        if (data == null) return "";

        return data.toString();
    }

    public void cleanUp() {
        eventHandlers.clear();
        jsContext = null;
        lightingService = null;
        LOGGER.info("NetworkService cleaned up.");
    }
}