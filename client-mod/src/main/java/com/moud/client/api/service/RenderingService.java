package com.moud.client.api.service;

import com.moud.client.rendering.PostProcessingManager;
import com.moud.client.rendering.CustomRenderTypeManager;
import com.moud.client.runtime.ClientScriptingRuntime;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.util.Identifier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RenderingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderingService.class);
    private static final java.util.List<String> DEFAULT_EFFECTS = java.util.List.of("veil:fog", "veil:height_fog");
    private final PostProcessingManager postProcessingManager = new PostProcessingManager();
    private final CustomRenderTypeManager renderTypeManager = new CustomRenderTypeManager();
    private final Map<String, Value> renderHandlers = new ConcurrentHashMap<>();
    private final Map<String, Value> animationFrameCallbacks = new ConcurrentHashMap<>();
    private final Map<Identifier, Map<String, Object>> pendingUniformUpdates = new ConcurrentHashMap<>();
    private final Queue<Consumer<Double>> pendingJavaCallbacks = new ConcurrentLinkedQueue<>();

    private ClientScriptingRuntime scriptingRuntime;
    private volatile Context jsContext;
    private final AtomicBoolean contextValid = new AtomicBoolean(false);
    private final AtomicBoolean defaultFogApplied = new AtomicBoolean(false);

    public RenderingService() {
    }

    public void setRuntime(ClientScriptingRuntime runtime) {
        this.scriptingRuntime = runtime;
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
        this.contextValid.set(jsContext != null);
        LOGGER.debug("RenderingService received new GraalVM Context, valid: {}", contextValid.get());

        if (contextValid.get() && !pendingJavaCallbacks.isEmpty()) {
            processPendingJavaCallbacks();
        }
    }

    private void processPendingJavaCallbacks() {
        if (scriptingRuntime == null || scriptingRuntime.getExecutor() == null || scriptingRuntime.getExecutor().isShutdown()) {
            return;
        }

        scriptingRuntime.getExecutor().execute(() -> {
            if (!contextValid.get() || jsContext == null) {
                return;
            }

            int processed = 0;
            while (!pendingJavaCallbacks.isEmpty()) {
                Consumer<Double> callback = pendingJavaCallbacks.poll();
                if (callback != null) {
                    registerJavaCallbackDirectly(callback);
                    processed++;
                }
            }

            if (processed > 0) {
                LOGGER.info("Processed {} pending Java animation frame callbacks", processed);
            }
        });
    }

    private void registerJavaCallbackDirectly(Consumer<Double> javaCallback) {
        if (!contextValid.get() || jsContext == null) {
            LOGGER.warn("Context became invalid while processing pending callbacks");
            return;
        }

        final String id = "raf_java_" + System.nanoTime();

        try {
            jsContext.enter();

            ProxyExecutable proxy = (args) -> {
                javaCallback.accept(args.length > 0 ? args[0].asDouble() : 0.0);
                return null;
            };

            Value callbackValue = Value.asValue(proxy);

            if (callbackValue.canExecute()) {
                animationFrameCallbacks.put(id, callbackValue);
                LOGGER.debug("Registered pending callback with id: {}", id);
            } else {
                LOGGER.error("Failed to create executable Value from Java proxy");
            }

        } catch (Exception e) {
            LOGGER.error("Exception while creating animation frame proxy", e);
        } finally {
            jsContext.leave();
        }
    }

    @HostAccess.Export
    public String requestAnimationFrame(Value callback) {
        if (!callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be an executable function");
        }
        String id = "raf_" + System.nanoTime();
        animationFrameCallbacks.put(id, callback);
        return id;
    }

    @HostAccess.Export
    public void cancelAnimationFrame(String id) {
        animationFrameCallbacks.remove(id);
    }

    @HostAccess.Export
    public String createRenderType(Value options) {
        try {
            RenderTypeDefinition definition = new RenderTypeDefinition(options);
            Identifier renderTypeId = renderTypeManager.getOrCreate(definition);
            return renderTypeId.toString();
        } catch (Exception e) {
            LOGGER.error("Failed to create render type", e);
            throw new RuntimeException("Failed to create render type", e);
        }
    }

    @HostAccess.Export
    public void setShaderUniform(String shaderId, String uniformName, Object value) {
        try {
            Identifier shaderIdentifier = Identifier.tryParse(shaderId);
            if (shaderIdentifier == null) {
                LOGGER.error("Invalid shader ID format: {}", shaderId);
                return;
            }

            pendingUniformUpdates
                    .computeIfAbsent(shaderIdentifier, k -> new ConcurrentHashMap<>())
                    .put(uniformName, value);

        } catch (Exception e) {
            LOGGER.error("Failed to queue shader uniform update", e);
        }
    }

    public void applyPendingUniforms() {
        if (pendingUniformUpdates.isEmpty()) {
            return;
        }

        try {
            pendingUniformUpdates.forEach((shaderId, uniforms) -> {
                try {
                    ShaderProgram shader = VeilRenderSystem.setShader(shaderId);
                    if (shader != null) {
                        uniforms.forEach((uniformName, value) -> {
                            try {
                                var uniform = shader.getUniform(uniformName);
                                if (uniform != null) {
                                    if (value instanceof Number number) {
                                        if (value instanceof Float || value instanceof Double) {
                                            uniform.setFloat(number.floatValue());
                                        } else {
                                            uniform.setInt(number.intValue());
                                        }
                                    } else if (value instanceof Boolean bool) {
                                        uniform.setInt(bool ? 1 : 0);
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.error("Failed to set uniform {} on shader {}", uniformName, shaderId, e);
                            }
                        });
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to update shader uniforms for {}", shaderId, e);
                }
            });

            pendingUniformUpdates.clear();

        } catch (Exception e) {
            LOGGER.error("Failed to apply pending uniform updates", e);
        }
    }

    public void triggerRenderEvents() {
        if (!contextValid.get()) {
            return;
        }
        double timestamp = System.nanoTime() / 1_000_000.0;
        triggerRenderEvent("beforeWorldRender", timestamp);
    }

    public void processAnimationFrames(double timestamp) {
        if (animationFrameCallbacks.isEmpty() || !contextValid.get()) {
            return;
        }

        Map<String, Value> currentCallbacks = new ConcurrentHashMap<>(animationFrameCallbacks);
        animationFrameCallbacks.clear();

        if (jsContext == null) return;

        try {
            jsContext.enter();
            for (Value callback : currentCallbacks.values()) {
                if (callback.canExecute()) {
                    callback.execute(timestamp);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error executing animation frame callbacks", e);
        } finally {
            jsContext.leave();
        }
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
        renderTypeManager.clearCache();
        renderHandlers.clear();
        animationFrameCallbacks.clear();
        pendingUniformUpdates.clear();
        pendingJavaCallbacks.clear();
        scriptingRuntime = null;
        jsContext = null;
        LOGGER.info("RenderingService cleaned up");
    }

    public void applyPostEffect(String effectId) {
        postProcessingManager.applyEffect(effectId);
    }

    public void removePostEffect(String effectId) {
        postProcessingManager.removeEffect(effectId);
    }

    public void clearPostEffects() {
        postProcessingManager.clearAllEffects();
    }

    public void setPostEffectUniforms(String effectId, Map<String, Object> uniforms) {
        postProcessingManager.updateUniforms(effectId, uniforms);
    }

    public void applyDefaultFogIfNeeded() {
        if (defaultFogApplied.compareAndSet(false, true)) {
            DEFAULT_EFFECTS.forEach(id -> {
                if ("veil:height_fog".equalsIgnoreCase(id)) {
                    postProcessingManager.updateUniforms(id, com.moud.client.rendering.PostEffectUniformRegistry.defaultHeightFog());
                } else {
                    postProcessingManager.updateUniforms(id, com.moud.client.rendering.PostEffectUniformRegistry.defaultFog());
                }
                postProcessingManager.applyEffect(id);
            });
        }
    }
}
