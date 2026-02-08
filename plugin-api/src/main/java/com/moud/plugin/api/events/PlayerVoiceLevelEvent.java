package com.moud.plugin.api.events;

import com.moud.plugin.api.player.PlayerContext;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record PlayerVoiceLevelEvent(Instant timestamp,
                                    PlayerContext player,
                                    Map<String, Object> state) implements PluginEvent {
    public PlayerVoiceLevelEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(player, "player");
        state = state == null ? Map.of() : Map.copyOf(state);
    }
}

