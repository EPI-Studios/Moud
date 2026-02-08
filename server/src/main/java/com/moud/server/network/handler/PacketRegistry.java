package com.moud.server.network.handler;

import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerPacketWrapper;
import net.minestom.server.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;


public final class PacketRegistry {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            PacketRegistry.class,
            LogContext.builder().put("subsystem", "packet-registry").build()
    );

    private final Map<Class<?>, HandlerEntry<?>> handlers = new ConcurrentHashMap<>();
    private Predicate<Player> globalValidator;

    public void setGlobalValidator(Predicate<Player> validator) {
        this.globalValidator = validator;
    }

    public <T> void register(Class<T> packetClass, PacketHandler<T> handler) {
        register(packetClass, handler, null);
    }

    public <T> void register(Class<T> packetClass, PacketHandler<T> handler, Predicate<Player> validator) {
        HandlerEntry<?> previous = handlers.put(packetClass, new HandlerEntry<>(handler, validator));

        if (previous != null) {
            LOGGER.warn("Handler already registered for {}, overwriting implementation", packetClass.getSimpleName());
            return;
        }

        ServerPacketWrapper.registerHandler(packetClass, (playerObj, packet) -> {
            if (!(playerObj instanceof Player player)) {
                LOGGER.warn("Received packet from non-Player source: {}", playerObj.getClass().getName());
                return;
            }
            dispatch(player, packet, packetClass);
        });

        LOGGER.debug("Registered handler for {}", packetClass.getSimpleName());
    }

    public void registerGroup(PacketHandlerGroup group) {
        group.register(this);
        LOGGER.info("Registered handler group: {}", group.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private <T> void dispatch(Player player, T packet, Class<T> packetClass) {
        // validation
        if (globalValidator != null && !globalValidator.test(player)) {
            LOGGER.trace("Global validation failed for {} from {}",
                    packetClass.getSimpleName(), player.getUsername());
            return;
        }

        HandlerEntry<?> rawEntry = handlers.get(packetClass);
        if (rawEntry == null) {
            LOGGER.warn("No handler registered for packet type: {}", packetClass.getSimpleName());
            return;
        }

        HandlerEntry<T> entry = (HandlerEntry<T>) rawEntry;

        // handler specific validation
        if (entry.validator != null && !entry.validator.test(player)) {
            LOGGER.trace("Handler validation failed for {} from {}",
                    packetClass.getSimpleName(), player.getUsername());
            return;
        }

        try {
            entry.handler.handle(player, packet);
        } catch (Exception e) {
            LOGGER.error(LogContext.builder()
                    .put("packet", packetClass.getSimpleName())
                    .put("player", player.getUsername())
                    .build(), "Error handling packet", e);
        }
    }

    public int size() {
        return handlers.size();
    }

    private record HandlerEntry<T>(PacketHandler<T> handler, Predicate<Player> validator) {
    }
}
