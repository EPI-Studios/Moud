package com.moud.client.runtime;

import com.moud.client.api.service.ClientAPIService;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientScriptingRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientScriptingRuntime.class);
    private static ClientScriptingRuntime instance;

    private final ClientAPIService apiService;
    private Context graalContext;

    private static final Queue<Runnable> scriptExecutionQueue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public ClientScriptingRuntime(ClientAPIService apiService) {
        if (instance != null) {
            LOGGER.warn("ClientScriptingRuntime is already initialized.");
        }
        instance = this;
        this.apiService = apiService;
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

    public void loadScripts() {
        LOGGER.info("Scheduling script loading...");
        scheduleScriptTask(() -> {
            try {
                LOGGER.info("Client scripts loaded and executed successfully.");
                this.initialized.set(true);
            } catch (Exception e) {
                LOGGER.error("Failed to load client scripts", e);
                this.initialized.set(false);
            }
        });
    }

    public void triggerNetworkEvent(String eventName, String data) {
        scheduleScriptTask(() -> {
            if (!isInitialized()) return;
            try {
                graalContext.getBindings("js")
                        .getMember("moudAPI")
                        .getMember("network")
                        .invokeMember("triggerEvent", eventName, data);
            } catch (Exception e) {
                LOGGER.error("Failed to trigger network event '{}' in script", eventName, e);
            }
        });
    }

    public boolean isInitialized() {
        return this.initialized.get();
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
        scheduleScriptTask(() -> {
            if (graalContext != null) {
                LOGGER.info("Closing GraalVM context.");
                graalContext.close();
                graalContext = null;
                this.initialized.set(false);
            }
        });
    }
}