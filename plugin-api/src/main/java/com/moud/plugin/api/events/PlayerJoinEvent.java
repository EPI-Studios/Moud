package com.moud.plugin.api.events;

import com.moud.plugin.api.player.PlayerContext;

import java.time.Instant;
import java.util.Objects;

public record PlayerJoinEvent(Instant timestamp, PlayerContext player) implements PluginEvent {
    public PlayerJoinEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(player, "player");
    }
}
