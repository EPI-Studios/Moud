package com.moud.server.events;

import com.moud.server.api.exception.APIException;
import com.moud.server.logging.MoudLogger;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class EventDispatcher {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(EventDispatcher.class);
    private static final int MAX_ERROR_COUNT = 3;
    private static final long ERROR_RESET_INTERVAL = 60000;

    private final EventNode<Event> eventNode;
    private final ConcurrentHashMap<String, Value> handlers;
    private final ConcurrentHashMap<String, AtomicInteger> errorCounts;
    private final ConcurrentHashMap<String, Long> lastErrorTimes;
    private final EventConverter converter;

    public EventDispatcher() {
        this.eventNode = EventNode.all("moud-events");
        this.handlers = new ConcurrentHashMap<>();
        this.errorCounts = new ConcurrentHashMap<>();
        this.lastErrorTimes = new ConcurrentHashMap<>();
        this.converter = new EventConverter();

        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        registerMinestomListeners();

        LOGGER.info("Event dispatcher initialized with error tolerance: {} errors per minute", MAX_ERROR_COUNT);
    }

    private void registerMinestomListeners() {
        eventNode.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                dispatch("player.join", event);
            }
        });

        eventNode.addListener(PlayerChatEvent.class, event ->
                dispatch("player.chat", event));

        LOGGER.debug("Minestom event listeners registered successfully");
    }

    public void register(String eventName, Value handler) {
        if (handler == null || !handler.canExecute()) {
            throw new APIException("INVALID_HANDLER", "Handler must be executable for event: " + eventName);
        }

        handlers.put(eventName, handler);
        errorCounts.put(eventName, new AtomicInteger(0));
        lastErrorTimes.put(eventName, System.currentTimeMillis());

        LOGGER.success("Event handler registered successfully: {}", eventName);
    }

    public void unregister(String eventName) {
        handlers.remove(eventName);
        errorCounts.remove(eventName);
        lastErrorTimes.remove(eventName);

        LOGGER.info("Event handler unregistered: {}", eventName);
    }

    private void dispatch(String eventName, Object minestomEvent) {
        Value handler = handlers.get(eventName);
        if (handler == null) {
            return;
        }

        if (isHandlerDisabled(eventName)) {
            LOGGER.warn("Handler disabled due to repeated errors: {}", eventName);
            return;
        }

        try {
            Object scriptEvent = converter.convert(eventName, minestomEvent);
            if (scriptEvent == null) {
                LOGGER.warn("Event converter returned null for event: {}", eventName);
                return;
            }

            handler.execute(scriptEvent);
            resetErrorCount(eventName);

        } catch (PolyglotException e) {
            handleScriptError(eventName, e);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid event conversion for '{}': {}", eventName, e.getMessage());
        } catch (Exception e) {
            handleUnexpectedError(eventName, e);
        }
    }

    public void dispatchScriptEvent(String eventName, String eventData, Player player) {
        Value handler = handlers.get(eventName);
        if (handler == null) {
            LOGGER.debug("No handler found for script event: {}", eventName);
            return;
        }

        if (isHandlerDisabled(eventName)) {
            LOGGER.warn("Handler disabled for script event: {}", eventName);
            return;
        }

        try {
            Object parsedData = parseEventData(eventData);
            handler.execute(parsedData, player);
            resetErrorCount(eventName);

        } catch (PolyglotException e) {
            handleScriptError(eventName, e);
        } catch (Exception e) {
            handleUnexpectedError(eventName, e);
        }
    }

    private void handleScriptError(String eventName, PolyglotException e) {
        incrementErrorCount(eventName);

        String errorLocation = extractErrorLocation(e);
        String errorMessage = e.getMessage();

        LOGGER.scriptError("Script error in event '{}' at {}: {}",
                eventName, errorLocation, errorMessage);

        if (isHandlerDisabled(eventName)) {
            LOGGER.critical("Handler '{}' has been disabled due to repeated errors", eventName);
        }
    }

    private void handleUnexpectedError(String eventName, Exception e) {
        incrementErrorCount(eventName);

        LOGGER.error("Unexpected error in event handler '{}': {}", eventName, e.getMessage(), e);

        if (isHandlerDisabled(eventName)) {
            LOGGER.critical("Handler '{}' has been disabled due to repeated errors", eventName);
        }
    }

    private String extractErrorLocation(PolyglotException e) {
        if (e.getSourceLocation() != null) {
            return String.format("line %d", e.getSourceLocation().getStartLine());
        }
        return "unknown location";
    }

    private Object parseEventData(String eventData) {
        if (eventData == null || eventData.trim().isEmpty()) {
            return null;
        }
        return eventData;
    }

    private boolean isHandlerDisabled(String eventName) {
        AtomicInteger count = errorCounts.get(eventName);
        Long lastError = lastErrorTimes.get(eventName);

        if (count == null || lastError == null) {
            return false;
        }

        long timeSinceLastError = System.currentTimeMillis() - lastError;
        if (timeSinceLastError > ERROR_RESET_INTERVAL) {
            count.set(0);
            return false;
        }

        return count.get() >= MAX_ERROR_COUNT;
    }

    private void incrementErrorCount(String eventName) {
        errorCounts.computeIfAbsent(eventName, k -> new AtomicInteger(0)).incrementAndGet();
        lastErrorTimes.put(eventName, System.currentTimeMillis());
    }

    private void resetErrorCount(String eventName) {
        AtomicInteger count = errorCounts.get(eventName);
        if (count != null && count.get() > 0) {
            count.set(0);
            LOGGER.debug("Error count reset for event: {}", eventName);
        }
    }

    public int getHandlerCount() {
        return handlers.size();
    }

    public boolean hasHandler(String eventName) {
        return handlers.containsKey(eventName);
    }
}