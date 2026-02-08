package com.moud.server.camera;

import com.moud.server.network.ServerNetworkManager;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import net.minestom.server.entity.Player;

public final class CameraService {
    private static final CameraService INSTANCE = new CameraService();

    public static CameraService getInstance() {
        return INSTANCE;
    }

    private CameraService() {
    }

    public boolean setPlayerCamera(Player player, String cameraIdOrLabel) {
        if (player == null || cameraIdOrLabel == null || cameraIdOrLabel.isBlank()) {
            return false;
        }
        SceneCamera camera = CameraRegistry.getInstance().getById(cameraIdOrLabel);
        if (camera == null) {
            camera = CameraRegistry.getInstance().getByLabel(cameraIdOrLabel);
        }
        if (camera == null) {
            return false;
        }
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.ENABLE,
                null,
                camera.id()
        ));
        Vector3 pos = camera.position();
        Vector3 rot = camera.rotation();
        java.util.Map<String, Object> posMap = java.util.Map.of(
                "x", pos != null ? pos.x : 0.0f,
                "y", pos != null ? pos.y : 0.0f,
                "z", pos != null ? pos.z : 0.0f
        );
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.SNAP_TO,
                java.util.Map.of(
                        "position", posMap,
                        "yaw", rot != null ? Math.toDegrees(rot.y) : 0.0f,
                        "pitch", rot != null ? Math.toDegrees(rot.x) : 0.0f,
                        "roll", rot != null ? Math.toDegrees(rot.z) : 0.0f,
                        "fov", camera.fov()
                ),
                camera.id()
        ));
        return true;
    }

    public void clearPlayerCamera(Player player) {
        if (player == null) return;
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.DISABLE,
                null,
                null
        ));
    }
}