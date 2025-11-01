package com.moud.server.events;

import com.moud.network.MoudPackets;
import com.moud.server.MoudEngine;
import com.moud.server.api.exception.APIException;
import com.moud.server.lighting.ServerLightingManager;
import com.moud.server.logging.MoudLogger;
import com.moud.server.profiler.model.ScriptExecutionMetadata;
import com.moud.server.profiler.model.ScriptExecutionType;
import com.moud.server.proxy.PlayerProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
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
                MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                    if (event.getPlayer().isOnline() && event.getPlayer().getInstance() != null) {
                        dispatch("player.join", event);
                    }
                });
            }
        });

        eventNode.addListener(PlayerChatEvent.class, event ->
                dispatch("player.chat", event));

        eventNode.addListener(PlayerDisconnectEvent.class, event ->
                dispatch("player.leave", event));

        eventNode.addListener(net.minestom.server.event.player.PlayerEntityInteractEvent.class, event -> {
            dispatchEntityInteraction(event.getPlayer(), event.getTarget(), "click");
        });

        eventNode.addListener(PlayerMoveEvent.class, event ->
                dispatch("player.move", event));


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
            String detail = minestomEvent != null ? minestomEvent.getClass().getSimpleName() : "";
            ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                    ScriptExecutionType.EVENT,
                    eventName,
                    detail
            );
            engine.getRuntime().executeCallback(handler, metadata, scriptEvent);
        } catch (Exception e) {
            LOGGER.error("Error during event dispatch for '{}'", eventName, e);
        }
    }

    public void dispatchMovementEvent(Player player, MoudPackets.MovementStatePacket packet) {
        PlayerProxy playerProxy = new PlayerProxy(player);

        Value movementStateHandler = handlers.get("player.movement_state");
        if (movementStateHandler != null) {
            try {
                ProxyObject movementData = ProxyObject.fromMap(Map.of(
                        "forward", packet.forward(),
                        "backward", packet.backward(),
                        "left", packet.left(),
                        "right", packet.right(),
                        "jumping", packet.jumping(),
                        "sneaking", packet.sneaking(),
                        "sprinting", packet.sprinting(),
                        "onGround", packet.onGround(),
                        "speed", packet.speed()
                ));

                ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                        ScriptExecutionType.EVENT,
                        "player.movement_state",
                        player.getUsername()
                );
                engine.getRuntime().executeCallback(movementStateHandler, metadata, playerProxy, movementData);
            } catch (Exception e) {
                LOGGER.error("Error during movement_state event dispatch for player {}", player.getUsername(), e);
            }
        }
    }

    public void dispatchMovementEventType(Player player, String eventType) {
        Value handler = handlers.get(eventType);
        if (handler != null) {
            try {
                PlayerProxy playerProxy = new PlayerProxy(player);
                ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                        ScriptExecutionType.EVENT,
                        eventType,
                        player.getUsername()
                );
                engine.getRuntime().executeCallback(handler, metadata, playerProxy);
                LOGGER.debug("Successfully dispatched {} event for player {}", eventType, player.getUsername());
            } catch (Exception e) {
                LOGGER.error("Error during {} event dispatch for player {}", eventType, player.getUsername(), e);
            }
        } else {
            LOGGER.debug("No handler found for event: {}", eventType);
        }
    }

    public void dispatchMoudReady(Player player) {
        Value handler = handlers.get("moud.player.ready");
        if (handler == null) return;

        try {
            PlayerProxy scriptEvent = new PlayerProxy(player);
            ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                    ScriptExecutionType.EVENT,
                    "moud.player.ready",
                    player.getUsername()
            );
            engine.getRuntime().executeCallback(handler, metadata, scriptEvent);
            LOGGER.debug("Dispatched moud.player.ready event for {}", player.getUsername());
        } catch (Exception e) {
            LOGGER.error("Error during moud.player.ready event dispatch for '{}'", player.getUsername(), e);
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
            ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                    ScriptExecutionType.EVENT,
                    eventName,
                    player.getUsername()
            );
            engine.getRuntime().executeCallback(handler, metadata, playerProxy, eventData);
            LOGGER.debug("Successfully dispatched script event '{}' for player {}", eventName, player.getUsername());
        } catch (Exception e) {
            LOGGER.error("Error during script event dispatch for '{}' from player {}", eventName, player.getUsername(), e);
        }
    }

    public void dispatchEntityInteraction(Player player, Entity entity, String interactionType) {
        Value handler = handlers.get("entity.interact");
        if (handler == null) {
            return;
        }

        try {
            com.moud.server.proxy.EntityInteractionProxy eventProxy =
                    new com.moud.server.proxy.EntityInteractionProxy(entity, player, interactionType);
            ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                    ScriptExecutionType.EVENT,
                    "entity.interact",
                    interactionType
            );
            engine.getRuntime().executeCallback(handler, metadata, eventProxy);
            LOGGER.debug("Dispatched entity interaction '{}' for player {} with entity {}",
                    interactionType, player.getUsername(), entity.getUuid());
        } catch (Exception e) {
            LOGGER.error("Error during entity interaction dispatch for player {}", player.getUsername(), e);
        }
    }

    public void dispatchMouseMoveEvent(Player player, float deltaX, float deltaY) {
        Value handler = handlers.get("player.mousemove");
        if (handler == null) {
            return;
        }

        try {
            ProxyObject data = ProxyObject.fromMap(Map.of("deltaX", deltaX, "deltaY", deltaY));
            ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                    ScriptExecutionType.EVENT,
                    "player.mousemove",
                    player.getUsername()
            );
            engine.getRuntime().executeCallback(handler, metadata, new PlayerProxy(player), data);
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
            ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                    ScriptExecutionType.EVENT,
                    "player.click",
                    player.getUsername()
            );
            engine.getRuntime().executeCallback(handler, metadata, new PlayerProxy(player), data);
            LOGGER.debug("Dispatched player click event for {}: button={}", player.getUsername(), button);
        } catch (Exception e) {
            LOGGER.error("Error during player click event dispatch for player {}", player.getUsername(), e);
        }
    }

    public void dispatchLoadEvent(String eventName) {
        Value handler = handlers.get(eventName);
        if (handler == null) {
            LOGGER.debug("No handler found for initial server load event: {}", eventName);
            return;
        }

        try {
            ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                    ScriptExecutionType.RUNTIME_TICK,
                    eventName,
                    "load"
            );
            engine.getRuntime().executeCallback(handler, metadata);
            LOGGER.success("Dispatched one-time '{}' event.", eventName);
        } catch (Exception e) {
            LOGGER.error("Error during dispatch of '{}' event", eventName, e);
        }
    }
}
