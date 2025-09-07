package com.moud.server.api;

import com.moud.server.events.EventDispatcher;
import com.moud.server.proxy.ServerProxy;
import com.moud.server.proxy.WorldProxy;
import org.graalvm.polyglot.Value;

public class ScriptingAPI {
    private final EventDispatcher eventDispatcher;
    private final ServerProxy serverProxy;
    private final WorldProxy worldProxy;

    public ScriptingAPI(EventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
        this.serverProxy = new ServerProxy();
        this.worldProxy = new WorldProxy().createInstance();
    }

    public void on(String eventName, Value callback) {
        eventDispatcher.register(eventName, callback);
    }

    public ServerProxy getServer() {
        return serverProxy;
    }

    /**
     * Gets the primary game world API.
     * @return The WorldProxy instance for interacting with the world.
     */
    public WorldProxy getWorld() {
        return this.worldProxy;
    }
}