package com.moud.server.events;

import com.moud.server.MoudEngine;
import com.moud.server.api.exception.APIException;
import com.moud.server.lighting.ServerLightingManager;
import com.moud.server.logging.MoudLogger;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
        if (handler == null) return;

        // TODO: parse this as JSON.
        engine.getRuntime().executeCallback(handler, eventData, player);
    }
}