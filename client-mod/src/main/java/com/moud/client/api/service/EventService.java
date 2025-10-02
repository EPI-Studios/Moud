

package com.moud.client.api.service;

import com.moud.client.runtime.ClientScriptingRuntime;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

public final class EventService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventService.class);
    private final Map<String, List<Value>> handlers = new ConcurrentHashMap<>();
    private final ClientAPIService apiService;
    private Context jsContext;
    private ExecutorService scriptExecutor;

    public EventService(ClientAPIService apiService) {
        this.apiService = apiService;
    }

    public void setContext(Context context) {
        this.jsContext = context;
        if (apiService.scriptingRuntime != null) {
            this.scriptExecutor = apiService.scriptingRuntime.getExecutor();
        }
    }

    @HostAccess.Export
    public void on(String eventName, Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Event handler must be an executable function.");
        }
        handlers.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(callback);
        LOGGER.debug("Registered client-side event handler for '{}'", eventName);
    }

    public void dispatch(String eventName, Object... args) {
        List<Value> callbacks = handlers.get(eventName);
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }

        if (scriptExecutor == null || scriptExecutor.isShutdown() || jsContext == null) {
            LOGGER.warn("Cannot dispatch event '{}': script runtime is not ready.", eventName);
            return;
        }

        for (Value callback : callbacks) {
            scriptExecutor.execute(() -> {
                try {
                    jsContext.enter();
                    callback.execute(args);
                } catch (Exception e) {
                    LOGGER.error("Error executing event handler for '{}'", eventName, e);
                } finally {
                    try {
                        jsContext.leave();
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    public void cleanUp() {
        handlers.clear();
        jsContext = null;
        scriptExecutor = null;
        LOGGER.info("EventService cleaned up.");
    }
}