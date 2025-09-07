package com.moud.client.api.service;

import com.moud.client.rendering.AnimationManager;
import com.moud.client.rendering.PostProcessingManager;
import com.moud.client.rendering.ThemeManager;
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
    private final AnimationManager animationManager = new AnimationManager();
    private final ThemeManager themeManager = new ThemeManager();
    private final Map<String, Value> renderHandlers = new ConcurrentHashMap<>();

    private Context jsContext;

    public RenderingService() {

    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
        LOGGER.debug("RenderingService received new GraalVM Context.");
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

    public void triggerRenderEvent(String eventName, Object data) {
        Value handler = renderHandlers.get(eventName);
        if (handler != null && handler.canExecute()) {
            if (jsContext != null) {

                jsContext.enter();
                try {
                    handler.execute(data);
                } catch (PolyglotException e) {
                    LOGGER.error("Error executing JavaScript render handler for event '{}': {}", eventName, e.getMessage());
                    if (e.isGuestException()) {
                        LOGGER.error("Guest stack trace:\n{}", e.getStackTrace());
                    }
                } catch (Exception e) {
                    LOGGER.error("Unexpected error executing JavaScript render handler for event '{}'", eventName, e);
                } finally {
                    jsContext.leave();
                }
            } else {
                LOGGER.warn("Cannot trigger render event '{}': JavaScript context is not initialized in RenderingService.", eventName);
            }
        }
    }

    public void triggerRenderEvents() {
        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime / 1000.0f);

        triggerRenderEvent("beforeWorldRender", deltaTime);
        triggerRenderEvent("beforeRender", deltaTime);
        triggerRenderEvent("render", deltaTime);
    }

    public void cleanUp() {
        postProcessingManager.clearAllEffects();
        renderHandlers.clear();
        jsContext = null;
        LOGGER.info("RenderingService cleaned up.");
    }
}