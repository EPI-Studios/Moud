package com.moud.server.events;

import com.moud.server.api.exception.APIException;
import com.moud.server.logging.MoudLogger;
import com.moud.server.proxy.BlockEventProxy;
import com.moud.server.proxy.ChatEventProxy;
import com.moud.server.proxy.PlayerMoveEventProxy;
import com.moud.server.proxy.PlayerProxy;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;

public class EventConverter {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(EventConverter.class);

    public Object convert(String eventName, Object minestomEvent) {
        if (eventName == null) {
            throw new APIException("INVALID_EVENT_NAME", "Event name cannot be null");
        }

        if (minestomEvent == null) {
            throw new APIException("INVALID_EVENT_DATA", "Event data cannot be null for event: " + eventName);
        }

        try {
            return switch (eventName) {
                case "player.join" -> convertPlayerJoinEvent(minestomEvent);
                case "player.chat" -> convertPlayerChatEvent(minestomEvent);
                case "player.leave" -> convertPlayerLeaveEvent(minestomEvent);
                case "player.move" -> convertPlayerMoveEvent(minestomEvent);
                case "block.break" -> convertBlockBreakEvent(minestomEvent);
                case "block.place" -> convertBlockPlaceEvent(minestomEvent);
                default -> {
                    LOGGER.warn("Unknown event type for conversion: {}", eventName);
                    yield minestomEvent;
                }
            };
        } catch (ClassCastException e) {
            LOGGER.error("Invalid event type for '{}': expected {}, got {}",
                    eventName, getExpectedType(eventName), minestomEvent.getClass().getSimpleName());
            throw new APIException("EVENT_TYPE_MISMATCH",
                    "Invalid event type for '" + eventName + "'", e);
        } catch (Exception e) {
            LOGGER.error("Failed to convert event '{}': {}", eventName, e.getMessage(), e);
            throw new APIException("EVENT_CONVERSION_FAILED",
                    "Failed to convert event: " + eventName, e);
        }
    }

    private Object convertPlayerJoinEvent(Object event) {
        if (!(event instanceof PlayerSpawnEvent playerSpawnEvent)) {
            throw new ClassCastException("Expected PlayerSpawnEvent");
        }

        if (playerSpawnEvent.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in join event");
        }

        PlayerProxy playerProxy = new PlayerProxy(playerSpawnEvent.getPlayer());
        LOGGER.debug("Converted player.join event for: {}", playerProxy.getName());
        return playerProxy;
    }

    private Object convertPlayerChatEvent(Object event) {
        if (!(event instanceof PlayerChatEvent playerChatEvent)) {
            throw new ClassCastException("Expected PlayerChatEvent");
        }

        if (playerChatEvent.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in chat event");
        }

        if (playerChatEvent.getMessage() == null) {
            throw new APIException("INVALID_MESSAGE", "Message cannot be null in chat event");
        }

        ChatEventProxy chatProxy = new ChatEventProxy(
                playerChatEvent.getPlayer(),
                playerChatEvent.getMessage(),
                playerChatEvent
        );

        LOGGER.debug("Converted player.chat event for: {} with message: '{}'",
                chatProxy.getPlayer().getName(), chatProxy.getMessage());
        return chatProxy;
    }

    private Object convertPlayerLeaveEvent(Object event) {
        if (!(event instanceof net.minestom.server.event.player.PlayerDisconnectEvent playerDisconnectEvent)) {
            throw new ClassCastException("Expected PlayerDisconnectEvent");
        }

        if (playerDisconnectEvent.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in leave event");
        }

        PlayerProxy playerProxy = new PlayerProxy(playerDisconnectEvent.getPlayer());
        LOGGER.debug("Converted player.leave event for: {}", playerProxy.getName());
        return playerProxy;
    }

    private Object convertPlayerMoveEvent(Object event) {
        if (!(event instanceof net.minestom.server.event.player.PlayerMoveEvent playerMoveEvent)) {
            throw new ClassCastException("Expected PlayerMoveEvent");
        }

        if (playerMoveEvent.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in move event");
        }

        PlayerMoveEventProxy moveProxy = new PlayerMoveEventProxy(
                playerMoveEvent.getPlayer(),
                playerMoveEvent.getNewPosition(),
                playerMoveEvent
        );

        LOGGER.debug("Converted player.move event for: {} to position: {}",
                moveProxy.getPlayer().getName(), moveProxy.getNewPosition());
        return moveProxy;
    }

    private Object convertBlockBreakEvent(Object event) {
        if (!(event instanceof net.minestom.server.event.player.PlayerBlockBreakEvent blockBreakEvent)) {
            throw new ClassCastException("Expected PlayerBlockBreakEvent");
        }

        if (blockBreakEvent.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in block break event");
        }

        if (blockBreakEvent.getBlockPosition() == null) {
            throw new APIException("INVALID_BLOCK_POSITION", "Block position cannot be null in block break event");
        }

        BlockEventProxy blockProxy = new BlockEventProxy(
                blockBreakEvent.getPlayer(),
                blockBreakEvent.getBlockPosition(),
                blockBreakEvent.getBlock(),
                blockBreakEvent,
                "break"
        );

        LOGGER.debug("Converted block.break event for: {} at position: {}",
                blockProxy.getPlayer().getName(), blockProxy.getBlockPosition());
        return blockProxy;
    }

    private Object convertBlockPlaceEvent(Object event) {
        if (!(event instanceof net.minestom.server.event.player.PlayerBlockPlaceEvent blockPlaceEvent)) {
            throw new ClassCastException("Expected PlayerBlockPlaceEvent");
        }

        if (blockPlaceEvent.getPlayer() == null) {
            throw new APIException("INVALID_PLAYER", "Player cannot be null in block place event");
        }

        if (blockPlaceEvent.getBlockPosition() == null) {
            throw new APIException("INVALID_BLOCK_POSITION", "Block position cannot be null in block place event");
        }

        BlockEventProxy blockProxy = new BlockEventProxy(
                blockPlaceEvent.getPlayer(),
                blockPlaceEvent.getBlockPosition(),
                blockPlaceEvent.getBlock(),
                blockPlaceEvent,
                "place"
        );

        LOGGER.debug("Converted block.place event for: {} at position: {}",
                blockProxy.getPlayer().getName(), blockProxy.getBlockPosition());
        return blockProxy;
    }

    private String getExpectedType(String eventName) {
        return switch (eventName) {
            case "player.join" -> "PlayerSpawnEvent";
            case "player.chat" -> "PlayerChatEvent";
            case "player.leave" -> "PlayerDisconnectEvent";
            case "player.move" -> "PlayerMoveEvent";
            case "block.break" -> "PlayerBlockBreakEvent";
            case "block.place" -> "PlayerBlockPlaceEvent";
            default -> "Unknown";
        };
    }
}