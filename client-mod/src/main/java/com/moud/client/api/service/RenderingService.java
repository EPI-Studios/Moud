package com.moud.client.api.service;

import com.moud.client.rendering.PostProcessingManager;
import com.moud.client.runtime.ClientScriptingRuntime;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RenderingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderingService.class);
    private final PostProcessingManager postProcessingManager = new PostProcessingManager();
    private final Map<String, Value> renderHandlers = new ConcurrentHashMap<>();
    private final Map<String, Value> animationFrameCallbacks = new ConcurrentHashMap<>();

    private ClientScriptingRuntime scriptingRuntime;
    private volatile Context jsContext;
    private final AtomicBoolean contextValid = new AtomicBoolean(false);

    public RenderingService() {
    }

    public void setRuntime(ClientScriptingRuntime runtime) {
        this.scriptingRuntime = runtime;
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
        this.contextValid.set(jsContext != null);
        LOGGER.debug("RenderingService received new GraalVM Context, valid: {}", contextValid.get());
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

    public String requestAnimationFrame(Value callback) {
        if (!callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be an executable function.");
        }
        String id = "raf_" + System.nanoTime();
        animationFrameCallbacks.put(id, callback);
        return id;
    }

    public void cancelAnimationFrame(String id) {
        animationFrameCallbacks.remove(id);
    }

    public void triggerRenderEvents() {
        if (!contextValid.get()) {
            return;
        }
        float deltaTime = System.currentTimeMillis() / 1000.0f;
        triggerRenderEvent("beforeWorldRender", deltaTime);
        processAnimationFrames(deltaTime);
    }

    private void processAnimationFrames(float deltaTime) {
        if (animationFrameCallbacks.isEmpty() || !contextValid.get()) {
            return;
        }

        Map<String, Value> currentCallbacks = new ConcurrentHashMap<>(animationFrameCallbacks);
        animationFrameCallbacks.clear();

        if (scriptingRuntime == null || !scriptingRuntime.isInitialized()) {
            return;
        }

        scriptingRuntime.getExecutor().execute(() -> {
            if (jsContext == null || !contextValid.get()) {
                return;
            }

            try {
                jsContext.enter();
                try {
                    for (Value callback : currentCallbacks.values()) {
                        if (callback.canExecute()) {
                            callback.execute(deltaTime);
                        }
                    }
                } finally {
                    jsContext.leave();
                }
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && (e.getMessage().contains("Context is already closed") ||
                        e.getMessage().contains("not entered explicitly") ||
                        e.getMessage().contains("Multi threaded access"))) {
                    LOGGER.debug("Context access error during animation frame processing: {}", e.getMessage());
                    contextValid.set(false);
                } else {
                    LOGGER.error("State error executing animation frame callbacks", e);
                }
            } catch (PolyglotException e) {
                LOGGER.error("Error executing animation frame callbacks: {}", e.getMessage());
                if (e.isGuestException()) {
                    LOGGER.error("Guest stack trace:", e);
                }
            } catch (Exception e) {
                LOGGER.error("Unexpected error executing animation frame callbacks", e);
            }
        });
    }

    public void triggerRenderEvent(String eventName, Object data) {
        Value handler = renderHandlers.get(eventName);
        if (handler == null || !contextValid.get()) {
            return;
        }

        if (scriptingRuntime == null || !scriptingRuntime.isInitialized()) {
            return;
        }

        scriptingRuntime.getExecutor().execute(() -> {
            if (jsContext == null || !contextValid.get()) {
                contextValid.set(false);
                return;
            }

            try {
                jsContext.enter();
                try {
                    if (handler.canExecute()) {
                        handler.execute(data);
                    }
                } finally {
                    jsContext.leave();
                }
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && (e.getMessage().contains("Context is already closed") ||
                        e.getMessage().contains("not entered explicitly") ||
                        e.getMessage().contains("Multi threaded access"))) {
                    LOGGER.debug("Context access error during render event execution: {}", e.getMessage());
                    contextValid.set(false);
                } else {
                    LOGGER.error("State error executing render handler for event '{}'", eventName, e);
                }
            } catch (PolyglotException e) {
                LOGGER.error("Error executing JavaScript render handler for event '{}': {}", eventName, e.getMessage());
                if (e.isGuestException()) {
                    LOGGER.error("Guest stack trace:", e);
                }
            } catch (Exception e) {
                LOGGER.error("Unexpected error executing JavaScript render handler for event '{}'", eventName, e);
            }
        });
    }

    public void cleanUp() {
        contextValid.set(false);
        postProcessingManager.clearAllEffects();
        renderHandlers.clear();
        animationFrameCallbacks.clear();
        scriptingRuntime = null;
        jsContext = null;
        LOGGER.info("RenderingService cleaned up.");
    }
}