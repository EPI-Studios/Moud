package com.moud.server.ui;

import com.moud.network.MoudPackets;
import com.moud.server.MoudEngine;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.profiler.model.ScriptExecutionMetadata;
import com.moud.server.profiler.model.ScriptExecutionType;
import com.moud.server.proxy.PlayerProxy;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class UIOverlayService {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(UIOverlayService.class);
    private static final UIOverlayService INSTANCE = new UIOverlayService();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BiConsumer<Player, MoudPackets.UIInteractionPacket>> javaInteractionCallbacks = new ConcurrentHashMap<>();
    private UIOverlayService() {
    }

    public static UIOverlayService getInstance() {
        return INSTANCE;
    }

    private Session session(Player player) {
        return sessions.computeIfAbsent(player.getUuid(), ignored -> new Session());
    }

    public void upsert(Player player, List<MoudPackets.UIElementDefinition> definitions) {
        if (player == null || definitions == null || definitions.isEmpty()) {
            return;
        }
        Session session = session(player);
        definitions.forEach(def -> session.elements.put(def.id(), def));
        ServerNetworkManager.getInstance().send(player, new MoudPackets.UIOverlayUpsertPacket(definitions));
    }

    public void remove(Player player, List<String> elementIds) {
        if (player == null || elementIds == null || elementIds.isEmpty()) {
            return;
        }
        Session session = session(player);
        elementIds.forEach(session.elements::remove);
        ServerNetworkManager.getInstance().send(player, new MoudPackets.UIOverlayRemovePacket(elementIds));
    }

    public void clear(Player player) {
        if (player == null) return;
        sessions.remove(player.getUuid());
        javaInteractionCallbacks.remove(player.getUuid());
        ServerNetworkManager.getInstance().send(player, new MoudPackets.UIOverlayClearPacket());
    }

    public void resend(Player player) {
        Session session = sessions.get(player.getUuid());
        if (session == null || session.elements.isEmpty()) {
            return;
        }
        ServerNetworkManager.getInstance().send(
                player,
                new MoudPackets.UIOverlayUpsertPacket(new ArrayList<>(session.elements.values()))
        );
    }

    public void handleInteraction(Player player, MoudPackets.UIInteractionPacket packet) {
        Session session = sessions.get(player.getUuid());
        Value callback = session != null ? session.interactionCallback : null;
        BiConsumer<Player, MoudPackets.UIInteractionPacket> javaHandler = javaInteractionCallbacks.get(player.getUuid());

        boolean hasJsHandler = callback != null && callback.canExecute();
        if (!hasJsHandler && javaHandler == null) {
            return;
        }

        ScriptExecutionMetadata metadata = ScriptExecutionMetadata.of(
                ScriptExecutionType.EVENT,
                "ui.interaction",
                player.getUsername()
        );

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("elementId", packet.elementId());
        payload.put("action", packet.action());
        if (packet.payload() != null) {
            payload.put("data", packet.payload());
        }

        if (hasJsHandler) {
            try {
                MoudEngine.getInstance().getRuntime().executeCallback(
                        callback,
                        metadata,
                        new PlayerProxy(player),
                        ProxyObject.fromMap(payload)
                );
            } catch (Exception e) {
                LOGGER.error("Failed to dispatch UI interaction for player {}", player.getUsername(), e);
            }
        }

        if (javaHandler != null) {
            try {
                javaHandler.accept(player, packet);
            } catch (Exception e) {
                LOGGER.error("Java UI interaction handler failed for player {}", player.getUsername(), e);
            }
        }
    }

    public void setInteractionCallback(Player player, Value callback) {
        if (player == null) return;
        Session session = session(player);
        session.interactionCallback = callback != null && callback.canExecute() ? callback : null;
    }

    public void setJavaInteractionHandler(Player player, BiConsumer<Player, MoudPackets.UIInteractionPacket> handler) {
        if (player == null) {
            return;
        }
        if (handler == null) {
            javaInteractionCallbacks.remove(player.getUuid());
            return;
        }
        javaInteractionCallbacks.put(player.getUuid(), handler);
    }

    private static final class Session {
        final Map<String, MoudPackets.UIElementDefinition> elements = new ConcurrentHashMap<>();
        volatile Value interactionCallback;
    }
}
