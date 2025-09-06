package com.moud.client.api.service;

import org.graalvm.polyglot.Value;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class RenderingService {
    private final Map<String, Value> renderHandlers = new ConcurrentHashMap<>();
    private final Map<String, String> activeEffects = new ConcurrentHashMap<>();

    public void applyPostEffect(String effectName) {
        activeEffects.put(effectName, effectName);
    }

    public void removePostEffect(String effectName) {
        activeEffects.remove(effectName);
    }

    public void on(String eventName, Value callback) {
        validateCallback(callback);
        renderHandlers.put(eventName, callback);
    }

    public void triggerRenderEvent(String eventName, Object data) {
        Value handler = renderHandlers.get(eventName);
        if (handler != null && handler.canExecute()) {
            handler.execute(data);
        }
    }

    private void validateCallback(Value callback) {
        if (!callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
        }
    }
}