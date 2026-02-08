package com.moud.server.plugin.impl;

import com.moud.plugin.api.events.PluginEvent;
import com.moud.plugin.api.services.EventService;
import com.moud.plugin.api.services.events.EventListener;
import com.moud.plugin.api.services.events.Subscription;
import com.moud.server.plugin.PluginEventBus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventServiceImpl implements EventService {
    private final PluginEventBus eventBus;
    private final Object owner;
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public EventServiceImpl(PluginEventBus eventBus, Object owner) {
        this.eventBus = eventBus;
        this.owner = owner;
    }

    @Override
    public <T extends PluginEvent> Subscription listen(Class<T> eventType, EventListener<T> listener) {
        Subscription subscription = eventBus.subscribe(owner, eventType, listener);
        subscriptions.add(subscription);
        return subscription;
    }

    @Override
    public void removeAll() {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
        eventBus.unregisterOwner(owner);
    }

    public void shutdown() {
        removeAll();
    }
}
