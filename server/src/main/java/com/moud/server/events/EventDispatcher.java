package com.moud.server.events;

import com.moud.server.MoudEngine;
import com.moud.server.api.exception.APIException;
import com.moud.server.lighting.ServerLightingManager;
import com.moud.server.logging.MoudLogger;
import com.moud.server.proxy.PlayerProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventDispatcher {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(EventDispatcher.class);

    private final EventNode<Event> eventNode;
    private final ConcurrentHashMap<String, Value> handlers;
    private final EventConverter converter;
    private final MoudEngine engine;

    public EventDispatcher(MoudEngine engine) {
        this.engine = engine;
        this.eventNode = EventNode.all("moud-events");
        this.handlers = new ConcurrentHashMap<>();
        this.converter = new EventConverter();

        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        registerMinestomListeners();

        LOGGER.info("Event dispatcher initialized.");
    }

    private void registerMinestomListeners() {
        eventNode.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                ServerLightingManager.getInstance().syncLightsToPlayer(event.getPlayer());
                dispatch("player.join", event);
            }
        });

        eventNode.addListener(PlayerChatEvent.class, event ->
                dispatch("player.chat", event));

        eventNode.addListener(PlayerDisconnectEvent.class, event ->
                dispatch("player.leave", event));
    }

    public void register(String eventName, Value handler) {
        if (handler == null || !handler.canExecute()) {
            throw new APIException("INVALID_HANDLER", "Handler must be executable for event: " + eventName);
        }
        handlers.put(eventName, handler);
        LOGGER.success("Event handler registered: {}", eventName);
    }

    private void dispatch(String eventName, Object minestomEvent) {
        Value handler = handlers.get(eventName);
        if (handler == null) return;

        try {
            Object scriptEvent = converter.convert(eventName, minestomEvent);
            engine.getRuntime().executeCallback(handler, scriptEvent);
        } catch (Exception e) {
            LOGGER.error("Error during event dispatch for '{}'", eventName, e);
        }
    }

    public void dispatchScriptEvent(String eventName, String eventData, Player player) {
        Value handler = handlers.get(eventName);
        if (handler == null) {
            LOGGER.debug("No handler found for script event: {}", eventName);
            return;
        }

        try {
            PlayerProxy playerProxy = new PlayerProxy(player);
            engine.getRuntime().executeCallback(handler, playerProxy, eventData);
            LOGGER.debug("Successfully dispatched script event '{}' for player {}", eventName, player.getUsername());
        } catch (Exception e) {
            LOGGER.error("Error during script event dispatch for '{}' from player {}", eventName, player.getUsername(), e);
        }
    }

    public void dispatchMouseMoveEvent(Player player, float deltaX, float deltaY) {
        Value handler = handlers.get("player.mousemove");
        if (handler == null) {
            LOGGER.debug("No handler for mouse move event");
            return;
        }

        try {
            ProxyObject data = ProxyObject.fromMap(Map.of("deltaX", deltaX, "deltaY", deltaY));
            engine.getRuntime().executeCallback(handler, new PlayerProxy(player), data);
        } catch (Exception e) {
            LOGGER.error("Error during mouse move event dispatch for player {}", player.getUsername(), e);
        }
    }

    public void dispatchPlayerClickEvent(Player player, int button) {
        Value handler = handlers.get("player.click");
        if (handler == null) {
            LOGGER.debug("No handler for player click event");
            return;
        }

        try {
            ProxyObject data = ProxyObject.fromMap(Map.of("button", button));
            engine.getRuntime().executeCallback(handler, new PlayerProxy(player), data);
            LOGGER.debug("Dispatched player click event for {}: button={}", player.getUsername(), button);
        } catch (Exception e) {
            LOGGER.error("Error during player click event dispatch for player {}", player.getUsername(), e);
        }
    }
}