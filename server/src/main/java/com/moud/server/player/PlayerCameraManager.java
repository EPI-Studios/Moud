package com.moud.server.player;

import com.moud.api.math.Vector3;
import com.moud.server.cursor.CursorManager;
import net.minestom.server.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlayerCameraManager {
    private static final PlayerCameraManager INSTANCE = new PlayerCameraManager();
    private final ConcurrentHashMap<UUID, Vector3> cameraDirections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cursorUpdateExecutor = Executors.newScheduledThreadPool(1);

    private PlayerCameraManager() {
        cursorUpdateExecutor.scheduleAtFixedRate(this::updateAllCursors, 0, 50, TimeUnit.MILLISECONDS);
    }

    public static PlayerCameraManager getInstance() {
        return INSTANCE;
    }

    public void updateCameraDirection(Player player, Vector3 direction) {
        cameraDirections.put(player.getUuid(), direction);
    }

    public void onPlayerDisconnect(Player player) {
        cameraDirections.remove(player.getUuid());
        CursorManager.getInstance().removeCursor(player);
    }

    public Vector3 getCameraDirection(Player player) {
        return cameraDirections.getOrDefault(player.getUuid(), new Vector3(0, 0, 1));
    }

    private void updateAllCursors() {
        for (Player player : net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (cameraDirections.containsKey(player.getUuid())) {
                CursorManager.getInstance().updateCursor(player);
            }
        }
    }
}
