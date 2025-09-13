package com.moud.server.api;

import com.moud.server.events.EventDispatcher;
import com.moud.server.proxy.ServerProxy;
import com.moud.server.proxy.WorldProxy;
import com.moud.server.proxy.LightingAPIProxy;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.api.exception.APIException;
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

    public ScriptingAPI(EventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
        this.serverProxy = new ServerProxy();
        this.worldProxy = new WorldProxy().createInstance();
        this.lightingProxy = new LightingAPIProxy();
        this.validator = new APIValidator();

        LOGGER.info("Scripting API initialized successfully with lighting support");
    }

    public void on(String eventName, Value callback) {
        try {
            validator.validateEventName(eventName);
            validator.validateCallback(callback);

            eventDispatcher.register(eventName, callback);
            LOGGER.debug("Event handler registered: {}", eventName);

        } catch (APIException e) {
            LOGGER.error("Failed to register event handler for '{}': {}", eventName, e.getMessage());
            throw e;
        } catch (Exception e) {
            LOGGER.error("Unexpected error registering event handler for '{}'", eventName, e);
            throw new APIException("Internal error while registering event handler", e);
        }
    }

    public ServerProxy getServer() {
        try {
            return serverProxy;
        } catch (Exception e) {
            LOGGER.error("Error accessing server proxy", e);
            throw new APIException("Failed to access server", e);
        }
    }

    public WorldProxy createWorld() {
        try {
            WorldProxy newWorld = new WorldProxy().createInstance();
            LOGGER.debug("New world instance created");
            return newWorld;
        } catch (Exception e) {
            LOGGER.error("Failed to create world instance", e);
            throw new APIException("Failed to create world", e);
        }
    }

    public WorldProxy getWorld() {
        try {
            return this.worldProxy;
        } catch (Exception e) {
            LOGGER.error("Error accessing world proxy", e);
            throw new APIException("Failed to access world", e);
        }
    }

    public LightingAPIProxy getLighting() {
        try {
            return this.lightingProxy;
        } catch (Exception e) {
            LOGGER.error("Error accessing lighting proxy", e);
            throw new APIException("Failed to access lighting", e);
        }
    }

    public void shutdown() {
        try {
            LOGGER.info("Shutting down Scripting API");
        } catch (Exception e) {
            LOGGER.error("Error during API shutdown", e);
        }
    }
}