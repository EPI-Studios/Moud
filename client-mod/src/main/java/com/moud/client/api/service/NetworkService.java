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
    private AudioService audioService;

    public NetworkService() {

    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
        LOGGER.debug("NetworkService received new GraalVM Context.");
    }

    public void setLightingService(LightingService lightingService) {
        this.lightingService = lightingService;
    }

    public void setAudioService(AudioService audioService) {
        this.audioService = audioService;
    }

    @HostAccess.Export
    public void sendToServer(String eventName) {
        ClientNetworkManager.sendToServer(eventName, "");
    }

    @HostAccess.Export
    public void sendToServer(String eventName, Value data) {
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
        if (eventName.startsWith("audio:") && audioService != null) {
            audioService.handleNetworkEvent(eventName, eventData);
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

    private String serializeData(Value data) {
        if (data == null || data.isNull()) {
            return "";
        }

        if (data.isString()) {
            return data.asString();
        }

        if ("undefined".equals(data.toString())) {
            return "";
        }

        Object javaObj = convertValueToJava(data);
        return GSON.toJson(javaObj);
    }

    private Object convertValueToJava(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            } else if (value.fitsInLong()) {
                return value.asLong();
            } else {
                return value.asDouble();
            }
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.hasArrayElements()) {
            int size = (int) value.getArraySize();
            java.util.List<Object> list = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(convertValueToJava(value.getArrayElement(i)));
            }
            return list;
        }
        if (value.hasMembers()) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, convertValueToJava(value.getMember(key)));
            }
            return map;
        }
        return value.toString();
    }

    public void cleanUp() {
        eventHandlers.clear();
        jsContext = null;
        lightingService = null;
        audioService = null;
        LOGGER.info("NetworkService cleaned up.");
    }
}
