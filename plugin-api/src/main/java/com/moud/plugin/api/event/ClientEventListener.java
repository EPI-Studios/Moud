package com.moud.plugin.api.event;

import com.moud.plugin.api.events.ScriptEvent;

@FunctionalInterface
public interface ClientEventListener {
    void handle(ScriptEvent event);
}
