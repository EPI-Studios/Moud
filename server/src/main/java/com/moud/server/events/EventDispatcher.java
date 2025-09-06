package com.moud.server.events;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.graalvm.polyglot.Value;

import java.util.concurrent.ConcurrentHashMap;

public class EventDispatcher {
    private final EventNode<Event> eventNode;
    private final ConcurrentHashMap<String, Value> handlers;
    private final EventConverter converter;

    public EventDispatcher() {
        this.eventNode = EventNode.all("moud-events");
        this.handlers = new ConcurrentHashMap<>();
        this.converter = new EventConverter();

        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        registerMinestomListeners();
    }

    private void registerMinestomListeners() {
        eventNode.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                dispatch("player.join", event);
            }
        });

        eventNode.addListener(PlayerChatEvent.class, event ->
                dispatch("player.chat", event));
    }

    public void register(String eventName, Value handler) {
        if (!handler.canExecute()) {
            throw new IllegalArgumentException("Handler must be executable");
        }
        handlers.put(eventName, handler);
    }

    public void unregister(String eventName) {
        handlers.remove(eventName);
    }

    private void dispatch(String eventName, Object minestomEvent) {
        Value handler = handlers.get(eventName);
        if (handler != null && handler.canExecute()) {
            try {
                Object scriptEvent = converter.convert(eventName, minestomEvent);
                handler.execute(scriptEvent);
            } catch (Exception e) {
                throw new RuntimeException("Event handler failed: " + eventName, e);
            }
        }
    }

    public void dispatchScriptEvent(String eventName, String eventData, Player player) {
        Value handler = handlers.get(eventName);
        if (handler != null && handler.canExecute()) {
            try {
                handler.execute(eventData, player);
            } catch (Exception e) {
                throw new RuntimeException("Script event handler failed: " + eventName, e);
            }
        }
    }
}