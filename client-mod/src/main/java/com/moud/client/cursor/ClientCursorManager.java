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

        for (MoudPackets.CursorUpdateData update : updates) {
            LOGGER.info("[CURSOR-MANAGER] Raw packet data: playerId={}, position={}, normal={}",
                    update.playerId(), update.position(), update.normal());

            RemoteCursor cursor = remoteCursors.computeIfAbsent(update.playerId(), id -> {
                RemoteCursor newCursor = new RemoteCursor(id);
                newCursor.setVisible(true);
                newCursor.setAppearance("moud:textures/gui/cursor_default.png", new Vector3(1.0f, 1.0f, 1.0f), 0.8f, "TEXTURE");
                LOGGER.info("[CURSOR-MANAGER] Created new cursor for player {}", id);
                return newCursor;
            });

            Vector3 beforePos = cursor.getCurrentPosition();
            cursor.setTargetPosition(update.position(), update.normal());
            Vector3 afterPos = cursor.getCurrentPosition();

            LOGGER.info("[CURSOR-MANAGER] Position update: before={}, after={}, target={}",
                    beforePos, afterPos, update.position());
        }

        LOGGER.info("[CURSOR-MANAGER] Total cursors after update: {}", remoteCursors.size());
    }

    public void handleAppearanceUpdate(UUID playerId, String texture, Vector3 color, float scale, String renderMode) {
        RemoteCursor cursor = remoteCursors.computeIfAbsent(playerId, id -> new RemoteCursor(id));
        cursor.setAppearance(texture, color, scale, renderMode);
    }

    public void handleVisibilityUpdate(UUID playerId, boolean visible) {
        RemoteCursor cursor = remoteCursors.computeIfAbsent(playerId, id -> new RemoteCursor(id));
        cursor.setVisible(visible);
    }

    public void handleRemoveCursors(List<UUID> playerIds) {
        playerIds.forEach(remoteCursors::remove);
    }

    public void tick(float tickDelta) {
        for (RemoteCursor cursor : remoteCursors.values()) {
            cursor.update(tickDelta);
        }
    }

    public void render(MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        if (remoteCursors.isEmpty()) return;

        LOGGER.info("[CURSOR-MANAGER] Rendering {} cursors", remoteCursors.size());

        for (Map.Entry<UUID, RemoteCursor> entry : remoteCursors.entrySet()) {
            RemoteCursor cursor = entry.getValue();
            LOGGER.info("[CURSOR-MANAGER] Cursor {}: visible={}, texture={}",
                    entry.getKey(), cursor.isVisible(), cursor.getTexture());

            if (cursor.isVisible()) {
                renderer.render(cursor, matrices, consumers, tickDelta);
            }
        }
    }

    public void clear() {
        remoteCursors.clear();
    }
}