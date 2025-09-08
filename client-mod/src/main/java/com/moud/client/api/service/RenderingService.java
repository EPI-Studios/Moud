package com.moud.client.api.service;

import com.moud.client.rendering.PostProcessingManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RenderingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderingService.class);
    private final PostProcessingManager postProcessingManager = new PostProcessingManager();
    private final Map<String, Value> renderHandlers = new ConcurrentHashMap<>();
    private final ExecutorService scriptExecutor;

    private Context jsContext;

    public RenderingService() {
        this.scriptExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "RenderingService-Script");
            t.setDaemon(true);
            return t;
        });
    }

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

        scriptExecutor.execute(() -> {
            try {
                jsContext.enter();
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
                try {
                    jsContext.leave();
                } catch (Exception e) {
                    LOGGER.warn("Error leaving script context", e);
                }
            }
        });
    }

    public void cleanUp() {
        postProcessingManager.clearAllEffects();
        renderHandlers.clear();

        if (scriptExecutor != null) {
            scriptExecutor.shutdown();
        }

        jsContext = null;
        LOGGER.info("RenderingService cleaned up.");
    }
}