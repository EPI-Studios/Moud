package com.moud.server.plugin.player;

import com.moud.plugin.api.player.PlayerContext;
import net.minestom.server.entity.Player;

import java.util.UUID;

public final class PlayerContextImpl implements PlayerContext {
    private final Player player;

    public PlayerContextImpl(Player player) {
        this.player = player;
    }

    @Override
    public UUID uuid() {
        return player.getUuid();
    }

    @Override
    public String username() {
        return player.getUsername();
    }

    @Override
    public Player player() {
        return player;
    }
}
