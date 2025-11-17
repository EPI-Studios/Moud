package com.moud.plugin.api.player;

import net.minestom.server.entity.Player;

import java.util.UUID;

public interface PlayerContext {
    UUID uuid();
    String username();
    Player player();
}
