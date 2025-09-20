package com.moud.server.cursor;

import com.moud.api.math.Vector3;
import com.moud.server.player.PlayerCameraManager;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CursorManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CursorManager.class);
    private static CursorManager instance;

    private final Map<UUID, CursorData> playerCursors = new ConcurrentHashMap<>();
    private final double maxRaycastDistance = 100.0;

    private CursorManager() {}

    public static synchronized CursorManager getInstance() {
        if (instance == null) {
            instance = new CursorManager();
        }
        return instance;
    }

    public CursorData updateCursor(Player player) {

        Instance instance = player.getInstance();
        if (instance == null) {
            LOGGER.warn("Attempted to update cursor for player {} who is not in an instance.", player.getUsername());
            return null;
        }

        Vector3 cameraDirection = PlayerCameraManager.getInstance().getCameraDirection(player);
        Vector3 cameraPosition = new Vector3(
                (float) player.getPosition().x(),
                (float) player.getPosition().y() + 1.62f,
                (float) player.getPosition().z()
        );

        RaycastResult raycast = performRaycast(instance, cameraPosition, cameraDirection);

        CursorData cursorData = new CursorData(
                player.getUuid(),
                cameraPosition,
                cameraDirection,
                raycast.hitPosition,
                raycast.hitBlock,
                raycast.distance,
                raycast.hit
        );

        playerCursors.put(player.getUuid(), cursorData);
        return cursorData;
    }

    public CursorData getCursorData(Player player) {
        return playerCursors.get(player.getUuid());
    }

    public void removeCursor(Player player) {
        playerCursors.remove(player.getUuid());
    }

    private RaycastResult performRaycast(Instance instance, Vector3 origin, Vector3 direction) {
        Vec rayOrigin = new Vec(origin.x, origin.y, origin.z);
        Vec rayDirection = new Vec(direction.x, direction.y, direction.z).normalize();

        double step = 0.1;
        double distance = 0.0;

        while (distance < maxRaycastDistance) {
            Vec currentPos = rayOrigin.add(rayDirection.mul(distance));
            Point blockPos = new Vec(
                    Math.floor(currentPos.x()),
                    Math.floor(currentPos.y()),
                    Math.floor(currentPos.z())
            );

            Block block = instance.getBlock(blockPos);
            if (!block.isAir()) {
                return new RaycastResult(
                        new Vector3((float) currentPos.x(), (float) currentPos.y(), (float) currentPos.z()),
                        block.name(),
                        distance,
                        true
                );
            }

            distance += step;
        }

        Vec endPos = rayOrigin.add(rayDirection.mul(maxRaycastDistance));
        return new RaycastResult(
                new Vector3((float) endPos.x(), (float) endPos.y(), (float) endPos.z()),
                "minecraft:air",
                maxRaycastDistance,
                false
        );
    }

    public static class CursorData {
        private final UUID playerId;
        private final Vector3 cameraPosition;
        private final Vector3 cameraDirection;
        private final Vector3 worldPosition;
        private final String hitBlock;
        private final double distance;
        private final boolean hit;

        public CursorData(UUID playerId, Vector3 cameraPosition, Vector3 cameraDirection,
                          Vector3 worldPosition, String hitBlock, double distance, boolean hit) {
            this.playerId = playerId;
            this.cameraPosition = cameraPosition;
            this.cameraDirection = cameraDirection;
            this.worldPosition = worldPosition;
            this.hitBlock = hitBlock;
            this.distance = distance;
            this.hit = hit;
        }

        public UUID getPlayerId() { return playerId; }
        public Vector3 getCameraPosition() { return cameraPosition; }
        public Vector3 getCameraDirection() { return cameraDirection; }
        public Vector3 getWorldPosition() { return worldPosition; }
        public String getHitBlock() { return hitBlock; }
        public double getDistance() { return distance; }
        public boolean isHit() { return hit; }
    }

    private static class RaycastResult {
        final Vector3 hitPosition;
        final String hitBlock;
        final double distance;
        final boolean hit;

        RaycastResult(Vector3 hitPosition, String hitBlock, double distance, boolean hit) {
            this.hitPosition = hitPosition;
            this.hitBlock = hitBlock;
            this.distance = distance;
            this.hit = hit;
        }
    }
}