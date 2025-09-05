package com.moud.server.proxy;

import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerChatEvent;

public class ChatEventProxy {
    private final Player player;
    private final String message;
    private final PlayerChatEvent event;

    public ChatEventProxy(Player player, String message, PlayerChatEvent event) {
        this.player = player;
        this.message = message;
        this.event = event;
    }

    public PlayerProxy getPlayer() {
        return new PlayerProxy(player);
    }

    public String getMessage() {
        return message;
    }

    public void cancel() {
        event.setCancelled(true);
    }

    public boolean isCancelled() {
        return event.isCancelled();
    }
}