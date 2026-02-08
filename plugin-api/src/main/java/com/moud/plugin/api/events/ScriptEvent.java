package com.moud.plugin.api.events;

import com.moud.plugin.api.player.PlayerContext;

import java.time.Instant;
import java.util.Objects;

public record ScriptEvent(Instant timestamp,
                          PlayerContext player,
                          String eventName,
                          String payload) implements PluginEvent {
    public ScriptEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(eventName, "eventName");
        payload = payload == null ? "" : payload;
    }
}
