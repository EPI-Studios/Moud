package com.moud.server.events;

import com.moud.network.MoudPackets;
import com.moud.server.MoudEngine;
import com.moud.server.api.exception.APIException;
import com.moud.server.logging.MoudLogger;
import com.moud.server.profiler.model.ScriptExecutionMetadata;
import com.moud.server.profiler.model.ScriptExecutionType;
import com.moud.server.proxy.PlayerProxy;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventDispatcher {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(EventDispatcher.class);
    private static final Gson GSON = new Gson();

    private record HandlerEntry(Value callback, boolean once) {}

    private final EventNode<Event> eventNode;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<HandlerEntry>> handlers;
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

    public <E> void registerEventMapper(String eventName, Class<E> eventClass, EventConverter.EventMapper<E> mapper) {
        converter.register(eventName, eventClass, mapper);
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
        addHandler(eventName, handler, false);
    }

    public void registerOnce(String eventName, Value handler) {
        addHandler(eventName, handler, true);
    }

    public void unregister(String eventName, Value handler) {
        if (handler == null) {
            throw new APIException("INVALID_HANDLER", "Handler cannot be null for event: " + eventName);
        }
        CopyOnWriteArrayList<HandlerEntry> list = handlers.get(eventName);
        if (list == null) {
            return;
        }
        boolean removed = list.removeIf(entry -> entry.callback() == handler);
        if (removed) {
            LOGGER.success("Event handler removed: {}", eventName);
        }
        if (list.isEmpty()) {
            handlers.remove(eventName, list);
        }
    }

    private void addHandler(String eventName, Value handler, boolean once) {
        if (handler == null || !handler.canExecute()) {
            throw new APIException("INVALID_HANDLER", "Handler must be executable for event: " + eventName);
        }
        handlers.computeIfAbsent(eventName, key -> new CopyOnWriteArrayList<>())
                .add(new HandlerEntry(handler, once));
        LOGGER.success("Event handler registered: {}{}", eventName, once ? " (once)" : "");
    }

    private void dispatch(String eventName, Object minestomEvent) {
        CopyOnWriteArrayList<HandlerEntry> handlerList = handlers.get(eventName);
        if (handlerList == null || handlerList.isEmpty()) return;

        List<HandlerEntry> toRemove = null;
        for (HandlerEntry entry : handlerList) {
            try {
                Object scriptEvent = converter.convert(eventName, minestomEvent);
                String detail = minestomEvent != null ? minestomEvent.getClass().getSimpleName() : "";
                ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                        ScriptExecutionType.EVENT,
                        eventName,
                        detail
                );
                engine.getRuntime().executeCallback(entry.callback(), metadata, scriptEvent);
            } catch (Exception e) {
                LOGGER.error("Error during event dispatch for '{}'", eventName, e);
            }

            if (entry.once()) {
                if (toRemove == null) {
                    toRemove = new ArrayList<>();
                }
                toRemove.add(entry);
            }
        }
        pruneHandlers(eventName, handlerList, toRemove);
    }

    public void dispatchMovementEvent(Player player, MoudPackets.MovementStatePacket packet) {
        CopyOnWriteArrayList<HandlerEntry> handlerList = handlers.get("player.movement_state");
        if (handlerList == null || handlerList.isEmpty()) {
            return;
        }

        PlayerProxy playerProxy = new PlayerProxy(player);
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

            for (HandlerEntry entry : handlerList) {
                ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                        ScriptExecutionType.EVENT,
                        "player.movement_state",
                        player.getUsername()
                );
                engine.getRuntime().executeCallback(entry.callback(), metadata, playerProxy, movementData);
            }
        } catch (Exception e) {
            LOGGER.error("Error during movement_state event dispatch for player {}", player.getUsername(), e);
        }
    }

    public void dispatchMovementEventType(Player player, String eventType) {
        CopyOnWriteArrayList<HandlerEntry> handlerList = handlers.get(eventType);
        if (handlerList != null && !handlerList.isEmpty()) {
            try {
                PlayerProxy playerProxy = new PlayerProxy(player);
                for (HandlerEntry entry : handlerList) {
                    ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                            ScriptExecutionType.EVENT,
                            eventType,
                            player.getUsername()
                    );
                    engine.getRuntime().executeCallback(entry.callback(), metadata, playerProxy);
                }
                LOGGER.debug("Successfully dispatched {} event for player {}", eventType, player.getUsername());
            } catch (Exception e) {
                LOGGER.error("Error during {} event dispatch for player {}", eventType, player.getUsername(), e);
            }
        } else {
            LOGGER.debug("No handler found for event: {}", eventType);
        }
    }

    public void dispatchMoudReady(Player player) {
        CopyOnWriteArrayList<HandlerEntry> handlerList = handlers.get("moud.player.ready");
        if (handlerList == null || handlerList.isEmpty()) return;

        try {
            PlayerProxy scriptEvent = new PlayerProxy(player);
            for (HandlerEntry entry : handlerList) {
                ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                        ScriptExecutionType.EVENT,
                        "moud.player.ready",
                        player.getUsername()
                );
                engine.getRuntime().executeCallback(entry.callback(), metadata, scriptEvent);
            }
            LOGGER.debug("Dispatched moud.player.ready event for {}", player.getUsername());
        } catch (Exception e) {
            LOGGER.error("Error during moud.player.ready event dispatch for '{}'", player.getUsername(), e);
        }
    }

    public void dispatchScriptEvent(String eventName, String eventData, Player player) {
        CopyOnWriteArrayList<HandlerEntry> handlerList = handlers.get(eventName);
        if (handlerList == null || handlerList.isEmpty()) {
            LOGGER.debug("No handler found for script event: {}", eventName);
            return;
        }

        try {
            PlayerProxy playerProxy = new PlayerProxy(player);
            Object payload = parseScriptEventPayload(eventData);
            for (HandlerEntry entry : handlerList) {
                ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                        ScriptExecutionType.EVENT,
                        eventName,
                        player.getUsername()
                );
                engine.getRuntime().executeCallback(entry.callback(), metadata, playerProxy, payload);
            }
            LOGGER.debug("Successfully dispatched script event '{}' for player {}", eventName, player.getUsername());
        } catch (Exception e) {
            LOGGER.error("Error during script event dispatch for '{}' from player {}", eventName, player.getUsername(), e);
        }
    }

    private static Object parseScriptEventPayload(String eventData) {
        if (eventData == null || eventData.isBlank()) {
            return null;
        }

        try {
            Object parsed = GSON.fromJson(eventData, Object.class);
            return toJsProxy(parsed);
        } catch (JsonSyntaxException e) {
            return eventData;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object toJsProxy(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new java.util.LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                converted.put(key, toJsProxy(entry.getValue()));
            }
            return ProxyObject.fromMap(converted);
        }
        if (value instanceof List<?> list) {
            Object[] converted = new Object[list.size()];
            for (int i = 0; i < list.size(); i++) {
                converted[i] = toJsProxy(list.get(i));
            }
            return ProxyArray.fromArray(converted);
        }
        return value;
    }

    public boolean hasHandlers(String eventName) {
        CopyOnWriteArrayList<HandlerEntry> handlerList = handlers.get(eventName);
        return handlerList != null && !handlerList.isEmpty();
    }

    public void dispatchEntityInteraction(Player player, Entity entity, String interactionType) {
        CopyOnWriteArrayList<HandlerEntry> handlerList = handlers.get("entity.interact");
        if (handlerList == null || handlerList.isEmpty()) {
            return;
        }

        try {
            com.moud.server.proxy.EntityInteractionProxy eventProxy =
                    new com.moud.server.proxy.EntityInteractionProxy(entity, player, interactionType);
            for (HandlerEntry entry : handlerList) {
                ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                        ScriptExecutionType.EVENT,
                        "entity.interact",
                        interactionType
                );
                engine.getRuntime().executeCallback(entry.callback(), metadata, eventProxy);
            }
            LOGGER.debug("Dispatched entity interaction '{}' for player {} with entity {}",
                    interactionType, player.getUsername(), entity.getUuid());
        } catch (Exception e) {
            LOGGER.error("Error during entity interaction dispatch for player {}", player.getUsername(), e);
        }
    }

    public void dispatchMouseMoveEvent(Player player, float deltaX, float deltaY) {
        CopyOnWriteArrayList<HandlerEntry> handlerList = handlers.get("player.mousemove");
        if (handlerList == null || handlerList.isEmpty()) {
            return;
        }

        try {
            ProxyObject data = ProxyObject.fromMap(Map.of("deltaX", deltaX, "deltaY", deltaY));
            for (HandlerEntry entry : handlerList) {
                ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                        ScriptExecutionType.EVENT,
                        "player.mousemove",
                        player.getUsername()
                );
                engine.getRuntime().executeCallback(entry.callback(), metadata, new PlayerProxy(player), data);
            }
        } catch (Exception e) {
            LOGGER.error("Error during mouse move event dispatch for player {}", player.getUsername(), e);
        }
    }

    public void dispatchPlayerClickEvent(Player player, int button) {
        CopyOnWriteArrayList<HandlerEntry> handlerList = handlers.get("player.click");
        if (handlerList == null || handlerList.isEmpty()) {
            LOGGER.debug("No handler for player click event");
            return;
        }

        try {
            ProxyObject data = ProxyObject.fromMap(Map.of("button", button));
            for (HandlerEntry entry : handlerList) {
                ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                        ScriptExecutionType.EVENT,
                        "player.click",
                        player.getUsername()
                );
                engine.getRuntime().executeCallback(entry.callback(), metadata, new PlayerProxy(player), data);
            }
            LOGGER.debug("Dispatched player click event for {}: button={}", player.getUsername(), button);
        } catch (Exception e) {
            LOGGER.error("Error during player click event dispatch for player {}", player.getUsername(), e);
        }
    }

    public void dispatchLoadEvent(String eventName) {
        CopyOnWriteArrayList<HandlerEntry> handlerList = handlers.get(eventName);
        if (handlerList == null || handlerList.isEmpty()) {
            LOGGER.debug("No handler found for initial server load event: {}", eventName);
            return;
        }

        try {
            List<HandlerEntry> toRemove = new ArrayList<>();
            for (HandlerEntry entry : handlerList) {
                ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                        ScriptExecutionType.RUNTIME_TICK,
                        eventName,
                        "load"
                );
                engine.getRuntime().executeCallback(entry.callback(), metadata);
                if (entry.once()) {
                    toRemove.add(entry);
                }
            }
            pruneHandlers(eventName, handlerList, toRemove);
            LOGGER.success("Dispatched one-time '{}' event.", eventName);
        } catch (Exception e) {
            LOGGER.error("Error during dispatch of '{}' event", eventName, e);
        }
    }

    private void pruneHandlers(String eventName, CopyOnWriteArrayList<HandlerEntry> handlerList, List<HandlerEntry> toRemove) {
        if (toRemove == null || toRemove.isEmpty()) {
            return;
        }
        handlerList.removeAll(toRemove);
        if (handlerList.isEmpty()) {
            handlers.remove(eventName, handlerList);
        }
    }
}
