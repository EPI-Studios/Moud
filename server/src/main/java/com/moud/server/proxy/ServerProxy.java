package com.moud.server.proxy;

import net.minestom.server.MinecraftServer;

public class ServerProxy {

    public void broadcast(String message) {
        MinecraftServer.getConnectionManager().getOnlinePlayers()
                .forEach(player -> player.sendMessage(message));
    }

    public int getPlayerCount() {
        return MinecraftServer.getConnectionManager().getOnlinePlayerCount();
    }

    public PlayerProxy[] getPlayers() {
        return MinecraftServer.getConnectionManager().getOnlinePlayers()
                .stream()
                .map(PlayerProxy::new)
                .toArray(PlayerProxy[]::new);
    }
}