package com.moud.server.api;

import com.moud.server.MoudEngine;
import com.moud.server.events.EventDispatcher;
import com.moud.server.proxy.LightingAPIProxy;
import com.moud.server.proxy.ServerProxy;
import com.moud.server.proxy.WorldProxy;
import com.moud.server.task.AsyncManager;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.api.exception.APIException;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScriptingAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptingAPI.class);


    private final EventDispatcher eventDispatcher;
    private final ServerProxy serverProxy;
    private final WorldProxy worldProxy;
    private final LightingAPIProxy lightingProxy;
    private final APIValidator validator;
    private final MoudEngine engine;

    public ScriptingAPI(MoudEngine engine) {
        this.engine = engine;
        this.eventDispatcher = engine.getEventDispatcher();
        this.serverProxy = new ServerProxy();
        this.worldProxy = new WorldProxy().createInstance();
        this.lightingProxy = new LightingAPIProxy();
        this.validator = new APIValidator();
        LOGGER.info("Scripting API initialized successfully.");
    }

    @HostAccess.Export
    public void on(String eventName, Value callback) {
        try {
            validator.validateEventName(eventName);
            validator.validateCallback(callback);
            eventDispatcher.register(eventName, callback);
        } catch (APIException e) {
            LOGGER.error("Failed to register event handler for '{}': {}", eventName, e.getMessage());
            throw e;
        }
    }

    @HostAccess.Export
    public ServerProxy getServer() {
        return serverProxy;
    }

    @HostAccess.Export
    public WorldProxy getWorld() {
        return worldProxy;
    }

    @HostAccess.Export
    public LightingAPIProxy getLighting() {
        return lightingProxy;
    }

    @HostAccess.Export
    public AsyncManager getAsync() {
        return engine.getAsyncManager();
    }

    public void shutdown() {
        LOGGER.info("Shutting down Scripting API.");
    }
}

