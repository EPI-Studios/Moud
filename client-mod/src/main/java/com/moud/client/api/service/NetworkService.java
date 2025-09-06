package com.moud.client.api.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.moud.client.network.ClientNetworkManager;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkService.class);
    private static final Gson GSON = new Gson();

    private final Map<String, Value> eventHandlers = new ConcurrentHashMap<>();

    public void sendToServer(String eventName, Object data) {
        String serializedData = serializeData(data);
        ClientNetworkManager.sendToServer(eventName, serializedData);
    }

    public void on(String eventName, Value callback) {
        validateCallback(callback);
        eventHandlers.put(eventName, callback);
    }

    /**
     * Triggers a network event and passes the data to the corresponding JavaScript handler.
     * This method automatically parses the JSON data into JavaScript objects.
     *
     * @param eventName The name of the event.
     * @param eventData The raw JSON string received from the server.
     */
    public void triggerEvent(String eventName, String eventData) {
        Value handler = eventHandlers.get(eventName);
        if (handler != null && handler.canExecute()) {
            Object finalData;

            if (eventData == null || eventData.isEmpty()) {
                finalData = null;
            } else {
                try {
                    // parsing json chain to a java generic object
                    finalData = GSON.fromJson(eventData, Object.class);
                } catch (JsonSyntaxException e) {
                    LOGGER.warn("Failed to parse JSON for event '{}'. Passing raw string instead. JSON: {}", eventName, eventData, e);
                    finalData = eventData;
                }
            }
            handler.execute(finalData);
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
}