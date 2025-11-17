package com.moud.plugin.api.event;

import com.moud.plugin.api.events.PluginEvent;
import com.moud.plugin.api.events.ScriptEvent;
import com.moud.plugin.api.services.EventService;
import com.moud.plugin.api.services.events.Subscription;

import java.util.Objects;
import java.util.function.Consumer;

public final class EventsDsl {
    private final EventService events;

    public EventsDsl(EventService events) {
        this.events = events;
    }

    public <T extends PluginEvent> Subscription on(Class<T> type, Consumer<T> handler) {
        Objects.requireNonNull(type, "event type");
        Objects.requireNonNull(handler, "handler");
        return events.listen(type, handler::accept);
    }

    public Subscription onClient(String eventName, ClientEventListener handler) {
        return events.listen(ScriptEvent.class, event -> {
            if (event.eventName().equals(eventName)) {
                handler.handle(event);
            }
        });
    }
}
