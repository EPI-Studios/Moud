package com.moud.server.api;

import com.moud.server.events.EventDispatcher;
import com.moud.server.proxy.ServerProxy;
import com.moud.server.proxy.WorldProxy;
import org.graalvm.polyglot.Value;

public class ScriptingAPI {
    private final EventDispatcher eventDispatcher;
    private final ServerProxy serverProxy;

    public ScriptingAPI(EventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
        this.serverProxy = new ServerProxy();
    }

    public void on(String eventName, Value callback) {
        eventDispatcher.register(eventName, callback);
    }

    public ServerProxy getServer() {
        return serverProxy;
    }

    public WorldProxy createWorld() {
        return new WorldProxy().createInstance();
    }
}