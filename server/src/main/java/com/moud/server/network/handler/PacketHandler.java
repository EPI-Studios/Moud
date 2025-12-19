package com.moud.server.network.handler;

import net.minestom.server.entity.Player;

/**
 * Functional interface for handling a specific packet type.
 */
@FunctionalInterface
public interface PacketHandler<T> {
    /**
     * Handle an incoming packet from a player.
     *
     * @param player the player who sent the packet
     * @param packet the packet data
     */
    void handle(Player player, T packet);
}
