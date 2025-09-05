package com.moud.server.events;

import com.moud.server.proxy.ChatEventProxy;
import com.moud.server.proxy.PlayerProxy;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;

public class EventConverter {

    public static Object convert(String eventName, Object minestomEvent) {
        return switch (eventName) {
            case "player.join" -> {
                PlayerSpawnEvent event = (PlayerSpawnEvent) minestomEvent;
                yield new PlayerProxy(event.getPlayer());
            }
            case "player.chat" -> {
                PlayerChatEvent event = (PlayerChatEvent) minestomEvent;
                yield new ChatEventProxy(event.getPlayer(), event.getRawMessage(), event);
            }
            default -> throw new IllegalArgumentException("Unknown event: " + eventName);
        };
    }
}