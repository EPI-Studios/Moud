package com.moud.client.api.service;

import com.moud.client.rendering.PostProcessingManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RenderingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderingService.class);
    private final PostProcessingManager postProcessingManager = new PostProcessingManager();
    private final Map<String, Value> renderHandlers = new ConcurrentHashMap<>();

    private Context jsContext;

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void applyPostEffect(String effectId) {
        postProcessingManager.applyEffect(effectId);
    }

    public void removePostEffect(String effectId) {
        postProcessingManager.removeEffect(effectId);
    }

    public void on(String eventName, Value callback) {
        if (!callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be an executable function.");
        }
        renderHandlers.put(eventName, callback);
    }

    public void triggerRenderEvents() {

        triggerRenderEvent("beforeWorldRender", System.currentTimeMillis() / 1000.0f);
    }

    public void triggerRenderEvent(String eventName, Object data) {
        Value handler = renderHandlers.get(eventName);
        if (handler == null) {
            return;
        }

        if (jsContext == null) {
            LOGGER.warn("Cannot trigger render event '{}': JavaScript context is not initialized.", eventName);
            return;
        }

        jsContext.enter();
        try {
            if (handler.canExecute()) {
                handler.execute(data);
            }
        } catch (PolyglotException e) {
            LOGGER.error("Error executing JavaScript render handler for event '{}': {}", eventName, e.getMessage());
            if (e.isGuestException()) {
                LOGGER.error("Guest stack trace:", e);
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error executing JavaScript render handler for event '{}'", eventName, e);
        } finally {
            jsContext.leave();
        }
    }

    public void cleanUp() {
        postProcessingManager.clearAllEffects();
        renderHandlers.clear();
        jsContext = null;
        LOGGER.info("RenderingService cleaned up.");
    }
}