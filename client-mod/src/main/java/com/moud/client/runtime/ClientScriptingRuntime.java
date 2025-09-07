package com.moud.client.runtime;

import com.moud.client.api.service.ClientAPIService;
import org.graalvm.polyglot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClientScriptingRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientScriptingRuntime.class);
    private static final String LANGUAGE_ID = "js";

    private final ClientAPIService apiService;
    private Context context;
    private ExecutorService executor;
    private boolean initialized = false;

    public ClientScriptingRuntime(ClientAPIService apiService) {
        this.apiService = apiService;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Context getContext() {
        return context;
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        this.context = Context.newBuilder(LANGUAGE_ID)
                .allowIO(false)
                .allowNativeAccess(false)
                .allowCreateThread(true)
                .allowCreateProcess(false)
                .allowHostAccess(HostAccess.newBuilder()
                        .allowPublicAccess(true)
                        .allowAllImplementations(true)
                        .allowArrayAccess(true)
                        .allowListAccess(true)
                        .allowMapAccess(true)
                        .build())
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        this.context.getBindings(LANGUAGE_ID).putMember("Moud", apiService);
        LOGGER.info("Client scripting runtime GraalVM context created and API bound.");

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ClientScriptingRuntime-Executor");
            thread.setDaemon(true);
            return thread;
        });

        if (apiService != null) {
            apiService.updateScriptingContext(this.context);
        }

        this.initialized = true;
        LOGGER.info("Client scripting runtime initialized successfully.");
    }

    public CompletableFuture<Void> loadScripts(Map<String, byte[]> scriptsData) {

        return CompletableFuture.runAsync(() -> {
            if (!initialized || this.context == null) {
                LOGGER.error("GraalVM Context is not initialized. Cannot load scripts.");
                throw new IllegalStateException("GraalVM Context not initialized.");
            }

            try {

                if (apiService != null) {
                    apiService.cleanup();
                }

                if (this.context != null) {
                    this.context.close();
                    LOGGER.info("Closed previous GraalVM context for script reload.");
                }

                this.context = Context.newBuilder(LANGUAGE_ID)
                        .allowIO(false)
                        .allowNativeAccess(false)
                        .allowCreateThread(true)
                        .allowCreateProcess(false)
                        .allowHostAccess(HostAccess.newBuilder()
                                .allowPublicAccess(true)
                                .allowAllImplementations(true)
                                .allowArrayAccess(true)
                                .allowListAccess(true)
                                .allowMapAccess(true)
                                .build())
                        .allowEnvironmentAccess(EnvironmentAccess.NONE)
                        .allowPolyglotAccess(PolyglotAccess.NONE)
                        .option("engine.WarnInterpreterOnly", "false")
                        .build();

                this.context.getBindings(LANGUAGE_ID).putMember("Moud", apiService);

                if (apiService != null) {
                    apiService.updateScriptingContext(this.context);
                }

                this.context.enter();
                try {
                    for (Map.Entry<String, byte[]> entry : scriptsData.entrySet()) {
                        if (entry.getKey().endsWith(".js")) {
                            String content = new String(entry.getValue());
                            executeScript(entry.getKey(), content);
                        }
                    }
                } finally {
                    this.context.leave();
                }

                LOGGER.info("Client scripts loaded successfully.");

            } catch (Exception e) {
                LOGGER.error("Failed to load client scripts", e);

                throw new RuntimeException("Script loading failed.", e);
            }
        }, executor);
    }

    private void executeScript(String filename, String content) {

        try {
            Source source = Source.newBuilder(LANGUAGE_ID, content, filename).build();
            this.context.eval(source);
            LOGGER.debug("Executed client script: {}", filename);
        } catch (PolyglotException e) {
            LOGGER.error("Failed to execute client script: {}: {}", filename, e.getMessage());
            if (e.isGuestException()) {
                LOGGER.error("Guest stack trace:\n{}", e.getStackTrace());
            }
            throw new RuntimeException("Script execution failed for " + filename, e);
        } catch (Exception e) {
            LOGGER.error("Failed to execute client script: {}", filename, e);
            throw new RuntimeException(e);
        }
    }

    public void triggerNetworkEvent(String eventName, String eventData) {
        if (executor != null && initialized && context != null) {
            executor.execute(() -> {
                if (apiService != null) {

                    context.enter();
                    try {
                        apiService.network.triggerEvent(eventName, eventData);
                    } finally {
                        context.leave();
                    }
                }
            });
        } else {
            LOGGER.warn("Attempted to trigger network event '{}' but scripting runtime or context is not ready.", eventName);
        }
    }

    public void triggerRenderEvent(String eventName, Object data) {
        if (executor != null && initialized && context != null) {
            executor.execute(() -> {
                if (apiService != null) {

                    context.enter();
                    try {
                        apiService.rendering.triggerRenderEvent(eventName, data);
                    } finally {
                        context.leave();
                    }
                }
            });
        } else {
            LOGGER.warn("Attempted to trigger render event '{}' but scripting runtime or context is not ready.", eventName);
        }
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
            LOGGER.info("ClientScriptingRuntime-Executor shutdown.");
        }

        if (context != null) {

            if (apiService != null) {
                apiService.cleanup();
            }
            try {
                context.close();
            } catch (Exception ignored) {
                LOGGER.warn("Error closing GraalVM context during shutdown: {}", ignored.getMessage());
            }
            context = null;
            LOGGER.info("GraalVM context closed.");
        }

        initialized = false;
        LOGGER.info("Client scripting runtime shutdown completed.");
    }
}