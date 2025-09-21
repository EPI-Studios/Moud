package com.moud.client.cursor;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientCursorManager {
    private static ClientCursorManager instance;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCursorManager.class);
    private final Map<UUID, RemoteCursor> remoteCursors = new ConcurrentHashMap<>();
    private final CursorRenderer renderer;
    private final MinecraftClient client;

    private ClientCursorManager() {
        this.renderer = new CursorRenderer();
        this.client = MinecraftClient.getInstance();
    }

    public static synchronized ClientCursorManager getInstance() {
        if (instance == null) {
            instance = new ClientCursorManager();
        }
        return instance;
    }

    public void handlePositionUpdates(List<MoudPackets.CursorUpdateData> updates) {
        if (client.player == null) return;

        LOGGER.trace("Handling {} cursor position updates.", updates.size());

        for (MoudPackets.CursorUpdateData update : updates) {
            RemoteCursor cursor = remoteCursors.computeIfAbsent(update.playerId(), id -> {
                LOGGER.info("Creating new remote cursor for player {}", id);
                return new RemoteCursor(id);
            });

            cursor.setTargetPosition(update.position(), update.normal());
        }
    }

    public void handleAppearanceUpdate(UUID playerId, String texture, Vector3 color, float scale, String renderMode) {
        RemoteCursor cursor = remoteCursors.computeIfAbsent(playerId, id -> {
            LOGGER.info("Creating cursor for appearance update: {}", id);
            return new RemoteCursor(id);
        });

        cursor.setAppearance(texture, color, scale, renderMode);
    }

    public void handleVisibilityUpdate(UUID playerId, boolean visible) {
        RemoteCursor cursor = remoteCursors.computeIfAbsent(playerId, id -> {
            LOGGER.info("Creating cursor for visibility update: {}", id);
            return new RemoteCursor(id);
        });

        cursor.setVisible(visible);
    }

    public void handleRemoveCursors(List<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            RemoteCursor removed = remoteCursors.remove(playerId);
            if (removed != null) {
                LOGGER.info("Removed cursor for player {}", playerId);
            }
        }
    }

    public void tick(float tickDelta) {
        for (RemoteCursor cursor : remoteCursors.values()) {
            cursor.update(tickDelta);
        }
    }

    public void render(MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        if (remoteCursors.isEmpty()) return;

        for (RemoteCursor cursor : remoteCursors.values()) {
            if (cursor.isVisible() && cursor.getTexture() != null) {
                renderer.render(cursor, matrices, consumers, tickDelta);
            }
        }
    }

    public void clear() {
        LOGGER.info("Clearing {} cursors", remoteCursors.size());
        remoteCursors.clear();
    }
}