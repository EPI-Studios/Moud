package com.moud.plugin.api.event;

import com.moud.plugin.api.events.PluginEvent;
import com.moud.plugin.api.events.ScriptEvent;
import com.moud.plugin.api.services.EventService;
import com.moud.plugin.api.services.events.Subscription;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Helpers for subscribing to server and client events from plugins.
 */
public final class EventsDsl {
    private final EventService events;

    public EventsDsl(EventService events) {
        this.events = events;
    }

    /**
     * Listen for a server-side plugin event.
     */
    public <T extends PluginEvent> Subscription on(Class<T> type, Consumer<T> handler) {
        Objects.requireNonNull(type, "event type");
        Objects.requireNonNull(handler, "handler");
        return events.listen(type, handler::accept);
    }

    /**
     * Listen for a named client event emitted by scripts/UI. The handler is invoked when names match.
     */
    public Subscription onClient(String eventName, ClientEventListener handler) {
        return events.listen(ScriptEvent.class, event -> {
            if (event.eventName().equals(eventName)) {
                handler.handle(event);
            }
        });
    }
}
