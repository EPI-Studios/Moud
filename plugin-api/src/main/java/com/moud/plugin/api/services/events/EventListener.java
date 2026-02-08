package com.moud.plugin.api.services.events;

import com.moud.plugin.api.events.PluginEvent;

@FunctionalInterface
public interface EventListener<T extends PluginEvent> {
    void handle(T event);
}
