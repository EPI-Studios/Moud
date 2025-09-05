package com.moud.server.proxy;

import net.minestom.server.entity.Player;

public class PlayerProxy {
    private final Player player;

    public PlayerProxy(Player player) {
        this.player = player;
    }

    public String getName() {
        return player.getUsername();
    }

    public String getUuid() {
        return player.getUuid().toString();
    }

    public void sendMessage(String message) {
        player.sendMessage(message);
    }

    public void kick(String reason) {
        player.kick(reason);
    }

    public boolean isOnline() {
        return player.isOnline();
    }
}