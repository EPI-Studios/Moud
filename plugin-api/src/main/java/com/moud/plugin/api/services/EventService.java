package com.moud.plugin.api.services;

import com.moud.plugin.api.events.PluginEvent;
import com.moud.plugin.api.services.events.EventListener;
import com.moud.plugin.api.services.events.Subscription;

public interface EventService {
    <T extends PluginEvent> Subscription listen(Class<T> eventType, EventListener<T> listener);
    void removeAll();
}
