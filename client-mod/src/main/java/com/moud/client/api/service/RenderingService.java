package com.moud.client.api.service;

import com.moud.client.rendering.AnimationManager;
import com.moud.client.rendering.PostProcessingManager;
import com.moud.client.rendering.ThemeManager;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RenderingService {

    private final PostProcessingManager postProcessingManager = new PostProcessingManager();
    private final AnimationManager animationManager = new AnimationManager();
    private final ThemeManager themeManager = new ThemeManager();
    private final Map<String, Value> renderHandlers = new ConcurrentHashMap<>();


    /**
     * Applies a post-processing effect to the screen.
     *
     * @param effectId The ID of the post-processing pipeline (e.g., "mygame:damage_effect").
     */
    public void applyPostEffect(String effectId) {
        postProcessingManager.applyEffect(effectId);
    }
    /**
     * Removes a post-processing effect.
     *
     * @param effectId The ID of the effect to remove.
     */
    public void removePostEffect(String effectId) {
        postProcessingManager.removeEffect(effectId);
    }

    /**
     * Registers a callback for a render event.
     *
     * @param eventName The name of the event (e.g., 'beforeWorldRender', 'afterWorldRender').
     * @param callback The JavaScript function to execute.
     */
    public void on(String eventName, Value callback) {
        if (!callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be an executable function.");
        }
        renderHandlers.put(eventName, callback);
    }

    /**
     * Triggers a render event and executes the corresponding JavaScript callback.
     * Called from the Fabric/Minecraft render loop.
     *
     * @param eventName The name of the event to trigger.
     * @param data The event data (e.g., deltaTime).
     */
    public void triggerRenderEvent(String eventName, Object data) {
        Value handler = renderHandlers.get(eventName);
        if (handler != null && handler.canExecute()) {
            handler.execute(data);
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
    }
}