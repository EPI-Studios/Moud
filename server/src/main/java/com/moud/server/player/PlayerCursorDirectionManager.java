package com.moud.server.player;

import com.moud.api.math.Vector3;
import net.minestom.server.entity.Player;
import net.minestom.server.coordinate.Vec;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCursorDirectionManager {
    private static final PlayerCursorDirectionManager INSTANCE = new PlayerCursorDirectionManager();
    private final ConcurrentHashMap<UUID, Vec> cursorRotations = new ConcurrentHashMap<>();

    private PlayerCursorDirectionManager() {}

    public static PlayerCursorDirectionManager getInstance() {
        return INSTANCE;
    }

    public void onPlayerJoin(Player player) {
        cursorRotations.put(player.getUuid(), new Vec(player.getPosition().pitch(), player.getPosition().yaw()));
    }

    public void onPlayerDisconnect(Player player) {
        cursorRotations.remove(player.getUuid());
    }

    public void updateFromMouseDelta(Player player, float deltaX, float deltaY) {
        cursorRotations.compute(player.getUuid(), (uuid, oldRotation) -> {
            if (oldRotation == null) {
                oldRotation = new Vec(player.getPosition().pitch(), player.getPosition().yaw());
            }

            double sensitivity = 0.15;
            double newYaw = oldRotation.y() + deltaX * sensitivity;
            double newPitch = oldRotation.x() - deltaY * sensitivity;

            newPitch = Math.max(-90.0, Math.min(90.0, newPitch));

            return new Vec(newPitch, newYaw);
        });
    }

    public Vector3 getCursorDirection(Player player) {
        Vec rotation = cursorRotations.getOrDefault(player.getUuid(), Vec.ZERO);

        double pitchRad = Math.toRadians(rotation.x());
        double yawRad = Math.toRadians(rotation.y());

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vector3(x, y, z).normalize();
    }
}