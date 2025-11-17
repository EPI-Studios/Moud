package com.moud.server.api;

import com.moud.server.MoudEngine;
import com.moud.server.api.exception.APIException;
import com.moud.server.api.validation.APIValidator;
import com.moud.server.events.EventDispatcher;
import com.moud.server.proxy.*;
import com.moud.server.task.AsyncManager;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScriptingAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptingAPI.class);

    private final EventDispatcher eventDispatcher;
    private final APIValidator validator;
    private final MoudEngine engine;

    @HostAccess.Export
    public final ServerProxy server;
    @HostAccess.Export
    public final WorldProxy world;
    @HostAccess.Export
    public final WorldManagerProxy worlds;
    @HostAccess.Export
    public final LightingAPIProxy lighting;
    @HostAccess.Export
    public final ZoneAPIProxy zones;
    @HostAccess.Export
    public final MathProxy math;
    @HostAccess.Export
    public final CommandProxy commands;

    public ScriptingAPI(MoudEngine engine) {
        this.engine = engine;
        this.eventDispatcher = engine.getEventDispatcher();
        this.validator = new APIValidator();

        this.server = new ServerProxy();
        this.world = new WorldProxy().createInstance();
        this.worlds = new WorldManagerProxy();
        this.lighting = new LightingAPIProxy();
        this.zones = new ZoneAPIProxy(engine.getZoneManager());
        this.math = new MathProxy();
        this.commands = new CommandProxy();

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
    public void once(String eventName, Value callback) {
        try {
            validator.validateEventName(eventName);
            validator.validateCallback(callback);
            eventDispatcher.registerOnce(eventName, callback);
        } catch (APIException e) {
            LOGGER.error("Failed to register one-time handler for '{}': {}", eventName, e.getMessage());
            throw e;
        }
    }

    @HostAccess.Export
    public void off(String eventName, Value callback) {
        try {
            validator.validateEventName(eventName);
            validator.validateCallback(callback);
            eventDispatcher.unregister(eventName, callback);
        } catch (APIException e) {
            LOGGER.error("Failed to unregister handler for '{}': {}", eventName, e.getMessage());
            throw e;
        }
    }

    @HostAccess.Export
    public AsyncManager getAsync() {
        return engine.getAsyncManager();
    }

    public void shutdown() {
        LOGGER.info("Shutting down Scripting API.");
    }
}
