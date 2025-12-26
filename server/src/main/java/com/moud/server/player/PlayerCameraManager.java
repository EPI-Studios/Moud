package com.moud.server.player;

import com.moud.api.math.Vector3;
import net.minestom.server.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCameraManager {
    private static final PlayerCameraManager INSTANCE = new PlayerCameraManager();
    private final ConcurrentHashMap<UUID, Vector3> cameraDirections = new ConcurrentHashMap<>();

    private PlayerCameraManager() {
    }

    public static PlayerCameraManager getInstance() {
        return INSTANCE;
    }

    public void updateCameraDirection(Player player, Vector3 direction) {
        cameraDirections.put(player.getUuid(), direction);
    }

    public void onPlayerDisconnect(Player player) {
        cameraDirections.remove(player.getUuid());
    }

    public Vector3 getCameraDirection(Player player) {
        return getCameraDirection(player != null ? player.getUuid() : null);
    }

    public Vector3 getCameraDirection(UUID playerId) {
        if (playerId == null) {
            return new Vector3(0, 0, 1);
        }
        return cameraDirections.getOrDefault(playerId, new Vector3(0, 0, 1));
    }
}
