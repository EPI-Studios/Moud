package com.moud.client.runtime;

import com.moud.client.api.service.ClientAPIService;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientScriptingRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientScriptingRuntime.class);
    private static ClientScriptingRuntime instance;

    private final ClientAPIService apiService;
    private Context graalContext;
    private final ExecutorService scriptExecutor;

    private static final Queue<Runnable> scriptExecutionQueue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public ClientScriptingRuntime(ClientAPIService apiService) {
        if (instance != null) {
            LOGGER.warn("ClientScriptingRuntime is already initialized.");
        }
        instance = this;
        this.apiService = apiService;
        this.scriptExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ClientScript-Executor");
            t.setDaemon(true);
            return t;
        });
    }

    public static ClientScriptingRuntime getInstance() {
        return instance;
    }

    public void initialize() {
        LOGGER.info("Initializing GraalVM context for client scripts.");
        try {
            this.graalContext = Context.newBuilder("js")
                    .allowHostAccess(HostAccess.ALL)
                    .build();
            this.graalContext.getBindings("js").putMember("moudAPI", this.apiService);
            LOGGER.info("Client scripting runtime GraalVM context created.");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize GraalVM context!", e);
        }
    }

    public CompletableFuture<Void> loadScripts(Map<String, byte[]> scriptsData) {
        LOGGER.info("Scheduling script loading...");
        return CompletableFuture.runAsync(() -> {
            graalContext.enter();
            try {
                graalContext.getBindings("js").putMember("Moud", this.apiService);

                for (Map.Entry<String, byte[]> entry : scriptsData.entrySet()) {
                    String scriptName = entry.getKey();
                    String scriptContent = new String(entry.getValue());

                    LOGGER.debug("Loading client script: {}", scriptName);
                    graalContext.eval("js", scriptContent);
                }
                LOGGER.info("Client scripts loaded and executed successfully.");
                this.initialized.set(true);
            } catch (Exception e) {
                LOGGER.error("Failed to load client scripts", e);
                this.initialized.set(false);
                throw new RuntimeException(e);
            } finally {
                graalContext.leave();
            }
        }, scriptExecutor);
    }

    public CompletableFuture<Void> loadScripts() {
        LOGGER.info("Scheduling script loading...");
        return CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("Client scripts loaded and executed successfully.");
                this.initialized.set(true);
            } catch (Exception e) {
                LOGGER.error("Failed to load client scripts", e);
                this.initialized.set(false);
                throw new RuntimeException(e);
            }
        }, scriptExecutor);
    }

    public void triggerNetworkEvent(String eventName, String data) {
        if (!isInitialized()) return;

        scriptExecutor.execute(() -> {
            graalContext.enter();
            try {
                graalContext.getBindings("js")
                        .getMember("moudAPI")
                        .getMember("network")
                        .invokeMember("triggerEvent", eventName, data);
            } catch (Exception e) {
                LOGGER.error("Failed to trigger network event '{}' in script", eventName, e);
            } finally {
                graalContext.leave();
            }
        });
    }

    public boolean isInitialized() {
        return this.initialized.get();
    }

    public Context getContext() {
        return graalContext;
    }

    public ExecutorService getExecutor() {
        return scriptExecutor;
    }

    public void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    public static void scheduleScriptTask(Runnable scriptTask) {
        scriptExecutionQueue.add(scriptTask);
    }

    public void processScriptQueue() {
        Runnable task;
        while ((task = scriptExecutionQueue.poll()) != null) {
            try {
                graalContext.enter();
                task.run();
            } catch (Exception e) {
                LOGGER.error("Error executing scheduled script task", e);
            } finally {
                graalContext.leave();
            }
        }
    }

    public void shutdown() {
        scriptExecutor.execute(() -> {
            if (graalContext != null) {
                LOGGER.info("Closing GraalVM context.");
                graalContext.close();
                graalContext = null;
                this.initialized.set(false);
            }
        });
        scriptExecutor.shutdown();
    }
}