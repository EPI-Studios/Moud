package com.moud.client.cursor;

import com.moud.api.math.Vector3;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientCursorManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCursorManager.class);
    private static ClientCursorManager instance;

    private final Map<UUID, CursorRenderData> cursors = new ConcurrentHashMap<>();
    private final MinecraftClient client;

    private ClientCursorManager() {
        this.client = MinecraftClient.getInstance();
    }

    public static synchronized ClientCursorManager getInstance() {
        if (instance == null) {
            instance = new ClientCursorManager();
        }
        return instance;
    }

    public void updateCursorPosition(UUID playerId, Vector3 worldPos, boolean hit, double distance) {
        CursorRenderData cursor = cursors.get(playerId);
        if (cursor != null) {
            cursor.worldPosition = worldPos;
            cursor.hit = hit;
            cursor.distance = distance;
            cursor.lastUpdate = System.currentTimeMillis();
        }
    }

    public void updateCursorAppearance(UUID playerId, String texture, Vector3 color, float scale) {
        CursorRenderData cursor = cursors.computeIfAbsent(playerId, k -> new CursorRenderData(playerId));
        cursor.texture = Identifier.tryParse(texture);
        cursor.color = color;
        cursor.scale = scale;
    }

    public void setCursorVisible(UUID playerId, boolean visible) {
        CursorRenderData cursor = cursors.computeIfAbsent(playerId, k -> new CursorRenderData(playerId));
        cursor.visible = visible;
    }

    public void removeCursor(UUID playerId) {
        cursors.remove(playerId);
    }

    public void renderCursors(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (client.world == null || client.player == null) return;

        long currentTime = System.currentTimeMillis();

        cursors.entrySet().removeIf(entry -> {
            return currentTime - entry.getValue().lastUpdate > 5000;
        });

        for (CursorRenderData cursor : cursors.values()) {
            if (cursor.visible && cursor.worldPosition != null) {
                renderCursor(cursor, matrices, vertexConsumers, tickDelta);
            }
        }
    }

    private void renderCursor(CursorRenderData cursor, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        matrices.push();

        Vector3 pos = cursor.worldPosition;
        matrices.translate(pos.x, pos.y, pos.z);

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-client.gameRenderer.getCamera().getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(client.gameRenderer.getCamera().getPitch()));

        matrices.scale(cursor.scale, cursor.scale, cursor.scale);

        float alpha = cursor.hit ? 1.0f : 0.5f;

        matrices.pop();
    }

    public static class CursorRenderData {
        final UUID playerId;
        Vector3 worldPosition;
        Identifier texture = Identifier.of("minecraft", "textures/gui/icons.png");
        Vector3 color = new Vector3(1.0f, 1.0f, 1.0f);
        float scale = 1.0f;
        boolean visible = true;
        boolean hit = false;
        double distance = 0.0;
        long lastUpdate = System.currentTimeMillis();

        CursorRenderData(UUID playerId) {
            this.playerId = playerId;
        }
    }
}