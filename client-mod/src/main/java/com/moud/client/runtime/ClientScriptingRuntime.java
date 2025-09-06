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

    public void initialize() {
        if (initialized) {
            return;
        }

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ClientScriptingRuntime");
            thread.setDaemon(true);
            return thread;
        });

        this.initialized = true;
        LOGGER.info("Client scripting runtime initialized");
    }

    public CompletableFuture<Void> loadScripts(Map<String, byte[]> scriptsData) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (this.context != null) {
                    this.context.close();
                }

                this.context = Context.newBuilder(LANGUAGE_ID)
                        .allowIO(false)
                        .allowNativeAccess(false)
                        .allowCreateThread(false)
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

                for (Map.Entry<String, byte[]> entry : scriptsData.entrySet()) {
                    if (entry.getKey().endsWith(".js")) {
                        String content = new String(entry.getValue());
                        executeScript(entry.getKey(), content);
                    }
                }

                LOGGER.info("Client scripts loaded successfully");

            } catch (Exception e) {
                LOGGER.error("Failed to load client scripts", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private void executeScript(String filename, String content) {
        try {
            Source source = Source.newBuilder(LANGUAGE_ID, content, filename).build();
            this.context.eval(source);
            LOGGER.debug("Executed client script: {}", filename);
        } catch (Exception e) {
            LOGGER.error("Failed to execute client script: {}", filename, e);
            throw new RuntimeException(e);
        }
    }

    public void triggerNetworkEvent(String eventName, String eventData) {
        if (apiService != null) {
            apiService.network.triggerEvent(eventName, eventData);
        }
    }

    public void triggerRenderEvent(String eventName, Object data) {
        if (apiService != null) {
            apiService.rendering.triggerRenderEvent(eventName, data);
        }
    }

    public void shutdown() {
        if (apiService != null) {
            apiService.rendering.cleanUp();
        }

        if (context != null) {
            try {
                context.close();
            } catch (Exception ignored) {}
            context = null;
        }

        if (executor != null) {
            executor.shutdown();
            executor = null;
        }

        initialized = false;
    }
}