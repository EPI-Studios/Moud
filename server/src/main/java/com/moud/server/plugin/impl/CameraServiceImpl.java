package com.moud.server.plugin.impl;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.plugin.api.player.PlayerContext;
import com.moud.plugin.api.services.CameraService;
import com.moud.plugin.api.services.SchedulerService;
import com.moud.server.network.ServerNetworkManager;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CameraServiceImpl implements CameraService {

    @Override
    public void enableCustomCamera(PlayerContext player, String cameraId) {
        if (player == null) return;
        ServerNetworkManager.getInstance().send(player.player(), new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.ENABLE, null, cameraId
        ));
    }

    @Override
    public void disableCustomCamera(PlayerContext player) {
        if (player == null) return;
        ServerNetworkManager.getInstance().send(player.player(), new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.DISABLE, null, null
        ));
    }

    @Override
    public void snapTo(PlayerContext player, CameraState state) {
        if (player == null || state == null) return;
        ServerNetworkManager.getInstance().send(player.player(), new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.SNAP_TO,
                mapFromState(state),
                null
        ));
    }

    @Override
    public void transitionTo(PlayerContext player, CameraTransition transition) {
        if (player == null || transition == null || transition.target() == null) return;
        Map<String, Object> payload = mapFromState(transition.target());
        if (transition.durationMs() != null) {
            payload.put("duration", transition.durationMs());
        }
        ServerNetworkManager.getInstance().send(player.player(), new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.TRANSITION_TO,
                payload,
                null
        ));
    }

    @Override
    public void followPath(PlayerContext player, List<CameraKeyframe> points, long durationMs, boolean loop) {
        if (player == null || points == null || points.isEmpty()) return;
        ServerNetworkManager.getInstance().send(player.player(), new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.FOLLOW_PATH,
                Map.of(
                        "points", points.stream().map(this::mapFromKeyframe).toList(),
                        "duration", durationMs,
                        "loop", loop
                ),
                null
        ));
    }

    @Override
    public void createCinematic(PlayerContext player, List<CameraKeyframe> keyframes) {
        createCinematic(player, keyframes, null, null, null);
    }

    @Override
    public void createCinematic(PlayerContext player, List<CameraKeyframe> keyframes, Runnable onComplete, SchedulerService scheduler) {
        long totalDuration = keyframes.stream()
                .mapToLong(kf -> kf.durationMs() != null ? kf.durationMs() : 0L)
                .sum();
        Duration duration = Duration.of(totalDuration, ChronoUnit.MILLIS);
        createCinematic(player, keyframes, onComplete, scheduler, duration);
    }

    @Override
    public void createCinematic(PlayerContext player, List<CameraKeyframe> keyframes, Runnable onComplete, SchedulerService scheduler, Duration duration) {
        if (player == null || keyframes == null || keyframes.isEmpty()) return;
        ServerNetworkManager.getInstance().send(player.player(), new MoudPackets.CameraControlPacket(
                MoudPackets.CameraControlPacket.Action.CREATE_CINEMATIC,
                Map.of("keyframes", keyframes.stream().map(this::mapFromKeyframe).toList()),
                null
        ));
        if (onComplete == null || scheduler == null || duration == null) return;
        // Schedule the onComplete callback after the total duration
        scheduler.runLater(onComplete, duration);
    }

    @Override
    public void dollyZoom(PlayerContext player, CameraDollyOptions options) {
        if (player == null || options == null) return;
        Map<String, Object> payload = new HashMap<>();
        if (options.targetFov() != null) payload.put("targetFov", options.targetFov());
        if (options.durationMs() != null) payload.put("duration", options.durationMs());
        if (options.distance() != null) payload.put("distance", options.distance());
        if (options.maintainTarget() != null) payload.put("maintainTarget", options.maintainTarget());
        if (options.alignCamera() != null) payload.put("alignCamera", options.alignCamera());
        if (options.targetPosition() != null) payload.put("target", vecToMap(options.targetPosition()));
        if (options.direction() != null) {
            Map<String, Object> dir = new HashMap<>();
            if (options.direction().yaw() != null) dir.put("yaw", options.direction().yaw());
            if (options.direction().pitch() != null) dir.put("pitch", options.direction().pitch());
            if (options.direction().distance() != null) dir.put("distance", options.direction().distance());
            if (options.direction().fromPlayerLook() != null) dir.put("fromPlayerLook", options.direction().fromPlayerLook());
            payload.put("direction", dir);
        }
        if (!payload.isEmpty()) {
            ServerNetworkManager.getInstance().send(player.player(), new MoudPackets.CameraControlPacket(
                    MoudPackets.CameraControlPacket.Action.DOLLY_ZOOM,
                    payload,
                    null
            ));
        }
    }

    private Map<String, Object> mapFromState(CameraState state) {
        Map<String, Object> map = new HashMap<>();
        if (state.position() != null) map.put("position", vecToMap(state.position()));
        if (state.yaw() != null) map.put("yaw", state.yaw());
        if (state.pitch() != null) map.put("pitch", state.pitch());
        if (state.roll() != null) map.put("roll", state.roll());
        if (state.fov() != null) map.put("fov", state.fov());
        return map;
    }

    private Map<String, Object> mapFromKeyframe(CameraKeyframe keyframe) {
        Map<String, Object> map = mapFromState(new CameraState(
                keyframe.position(),
                keyframe.yaw(),
                keyframe.pitch(),
                keyframe.roll(),
                keyframe.fov()
        ));
        if (keyframe.durationMs() != null) {
            map.put("duration", keyframe.durationMs());
        }
        return map;
    }

    private Map<String, Object> vecToMap(Vector3 vec) {
        return Map.of("x", vec.x, "y", vec.y, "z", vec.z);
    }
}
