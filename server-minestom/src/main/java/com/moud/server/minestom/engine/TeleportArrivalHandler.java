package com.moud.server.minestom.engine;

import net.minestom.server.entity.Player;

@FunctionalInterface
public interface TeleportArrivalHandler {
    void onArrive(Player player, ServerScene destination, byte[] payload);
}
