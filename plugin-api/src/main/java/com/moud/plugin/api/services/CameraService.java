package com.moud.plugin.api.services;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.player.PlayerContext;

import java.util.List;

public interface CameraService {
    void enableCustomCamera(PlayerContext player, String cameraId);
    void disableCustomCamera(PlayerContext player);
    void snapTo(PlayerContext player, CameraState state);
    void transitionTo(PlayerContext player, CameraTransition transition);
    void followPath(PlayerContext player, List<CameraKeyframe> points, long durationMs, boolean loop);
    void createCinematic(PlayerContext player, List<CameraKeyframe> keyframes);
    void dollyZoom(PlayerContext player, CameraDollyOptions options);

    record CameraState(Vector3 position, Double yaw, Double pitch, Double roll, Double fov) {}
    record CameraTransition(CameraState target, Long durationMs) {}
    record CameraKeyframe(Vector3 position, Double yaw, Double pitch, Double roll, Double fov, Long durationMs) {}
    record CameraDollyOptions(
            Double targetFov,
            Long durationMs,
            Double distance,
            Boolean maintainTarget,
            Boolean alignCamera,
            Vector3 targetPosition,
            Direction direction
    ) {
        public record Direction(Double yaw, Double pitch, Double distance, Boolean fromPlayerLook) {}
    }
}
