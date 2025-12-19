package com.moud.server.events;

import com.moud.server.api.exception.APIException;
import com.moud.server.logging.MoudLogger;
import com.moud.server.proxy.BlockEventProxy;
import com.moud.server.proxy.ChatEventProxy;
import com.moud.server.proxy.PlayerLeaveEventProxy;
import com.moud.server.proxy.PlayerMoveEventProxy;
import com.moud.server.proxy.PlayerProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class EventConverter {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(EventConverter.class);

    @FunctionalInterface
    public interface EventMapper<E> {
        Object map(E minestomEvent);
    }

    private record MapperEntry<E>(Class<E> eventClass, EventMapper<E> mapper) {
    }

    private final Map<String, MapperEntry<?>> mappers = new ConcurrentHashMap<>();

    public EventConverter() {
        registerDefaults();
    }

    public <E> void register(String eventName, Class<E> eventClass, EventMapper<E> mapper) {
        Objects.requireNonNull(eventName, "eventName");
        Objects.requireNonNull(eventClass, "eventClass");
        Objects.requireNonNull(mapper, "mapper");
        mappers.put(eventName, new MapperEntry<>(eventClass, mapper));
    }

    public Object convert(String eventName, Object minestomEvent) {
        if (eventName == null) {
            throw new APIException("INVALID_EVENT_NAME", "Event name cannot be null");
        }
        if (minestomEvent == null) {
            throw new APIException("INVALID_EVENT_DATA", "Event data cannot be null for event: " + eventName);
        }

        MapperEntry<?> entry = mappers.get(eventName);
        if (entry == null) {
            LOGGER.warn("Unknown event type for conversion: {}", eventName);
            return minestomEvent;
        }

        if (!entry.eventClass().isInstance(minestomEvent)) {
            LOGGER.error("Invalid event type for '{}': expected {}, got {}",
                    eventName, entry.eventClass().getSimpleName(), minestomEvent.getClass().getSimpleName());
            throw new APIException("EVENT_TYPE_MISMATCH", "Invalid event type for '" + eventName + "'");
        }

        try {
            return convertWithMapper(eventName, minestomEvent, entry);
        } catch (Exception e) {
            LOGGER.error("Failed to convert event '{}': {}", eventName, e.getMessage(), e);
            throw new APIException("EVENT_CONVERSION_FAILED", "Failed to convert event: " + eventName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <E> Object convertWithMapper(String eventName, Object minestomEvent, MapperEntry<?> rawEntry) {
        MapperEntry<E> entry = (MapperEntry<E>) rawEntry;
        E castEvent = entry.eventClass().cast(minestomEvent);
        try {
            return entry.mapper().map(castEvent);
        } catch (Exception e) {
            LOGGER.error("Event mapper failed for '{}'", eventName, e);
            throw e;
        }
    }

    private void registerDefaults() {
        register("player.join", PlayerSpawnEvent.class, this::convertPlayerJoinEvent);
        register("player.chat", PlayerChatEvent.class, this::convertPlayerChatEvent);
        register("player.leave", PlayerDisconnectEvent.class, this::convertPlayerLeaveEvent);
        register("player.move", PlayerMoveEvent.class, this::convertPlayerMoveEvent);
        register("block.break", PlayerBlockBreakEvent.class, this::convertBlockBreakEvent);
        register("block.place", PlayerBlockPlaceEvent.class, this::convertBlockPlaceEvent);
    }

    private PlayerProxy convertPlayerJoinEvent(PlayerSpawnEvent event) {
        if (event.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in join event");
        }

        PlayerProxy playerProxy = new PlayerProxy(event.getPlayer());

        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            if (event.getPlayer().isOnline()) {
                LOGGER.debug("Player {} fully spawned, ready for scripting", event.getPlayer().getUsername());
            }
        });

        LOGGER.debug("Converted player.join event for: {}", playerProxy.getName());
        return playerProxy;
    }

    private ChatEventProxy convertPlayerChatEvent(PlayerChatEvent event) {
        if (event.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in chat event");
        }
        if (event.getMessage() == null) {
            throw new APIException("INVALID_MESSAGE", "Message cannot be null in chat event");
        }
        ChatEventProxy chatProxy = new ChatEventProxy(
                event.getPlayer(),
                event.getMessage(),
                event
        );
        LOGGER.debug("Converted player.chat event for: {} with message: '{}'",
                chatProxy.getPlayer().getName(), chatProxy.getMessage());
        return chatProxy;
    }

    private PlayerLeaveEventProxy convertPlayerLeaveEvent(PlayerDisconnectEvent event) {
        if (event.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in leave event");
        }

        PlayerLeaveEventProxy leaveProxy = new PlayerLeaveEventProxy(event.getPlayer());
        LOGGER.debug("Converted player.leave event for: {}", leaveProxy.getName());
        return leaveProxy;
    }

    private PlayerMoveEventProxy convertPlayerMoveEvent(PlayerMoveEvent event) {
        if (event.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in move event");
        }
        PlayerMoveEventProxy moveProxy = new PlayerMoveEventProxy(
                event.getPlayer(),
                event.getNewPosition(),
                event
        );
        LOGGER.debug("Converted player.move event for: {} to position: {}",
                moveProxy.getPlayer().getName(), moveProxy.getNewPosition());
        return moveProxy;
    }

    private BlockEventProxy convertBlockBreakEvent(PlayerBlockBreakEvent event) {
        if (event.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in block break event");
        }
        if (event.getBlockPosition() == null) {
            throw new APIException("INVALID_BLOCK_POSITION", "Block position cannot be null in block break event");
        }
        BlockEventProxy blockProxy = new BlockEventProxy(
                event.getPlayer(),
                event.getBlockPosition(),
                event.getBlock(),
                event,
                "break"
        );
        LOGGER.debug("Converted block.break event for: {} at position: {}",
                blockProxy.getPlayer().getName(), blockProxy.getBlockPosition());
        return blockProxy;
    }

    private BlockEventProxy convertBlockPlaceEvent(PlayerBlockPlaceEvent event) {
        if (event.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in block place event");
        }
        if (event.getBlockPosition() == null) {
            throw new APIException("INVALID_BLOCK_POSITION", "Block position cannot be null in block place event");
        }
        BlockEventProxy blockProxy = new BlockEventProxy(
                event.getPlayer(),
                event.getBlockPosition(),
                event.getBlock(),
                event,
                "place"
        );
        LOGGER.debug("Converted block.place event for: {} at position: {}",
                blockProxy.getPlayer().getName(), blockProxy.getBlockPosition());
        return blockProxy;
    }
}
