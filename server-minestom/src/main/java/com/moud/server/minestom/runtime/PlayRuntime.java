package com.moud.server.minestom.runtime;

import com.moud.core.physics.CharacterPhysics;
import com.moud.core.scene.Node;
import com.moud.core.util.MathUtils;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.PlayerInput;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.session.Session;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.physics.JoltScenePhysics;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayRuntime {
    private static final float MIN_PITCH = -89.0f;
    private static final float MAX_PITCH = 89.0f;
    private static final float DT = 1.0f / 20.0f;

    private final Map<UUID, Player> players = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerInput> lastInputs = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeScenes = new ConcurrentHashMap<>();
    private final Map<UUID, CharacterPhysics.State> physicsStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastProcessedTicks = new ConcurrentHashMap<>();
    private final RuntimeWorldEnvironmentSystem worldEnvironment = new RuntimeWorldEnvironmentSystem();
    private final RuntimeCameraSystem cameraSystem = new RuntimeCameraSystem(MIN_PITCH, MAX_PITCH);
    private final RuntimeBodySystem bodySystem = new RuntimeBodySystem();

    public void onPlayerSpawn(Player player, String sceneId) {
        if (player == null) {
            return;
        }
        players.put(player.getUuid(), player);
        if (sceneId != null && !sceneId.isBlank()) {
            activeScenes.put(player.getUuid(), sceneId);
        }
    }

    public void onDisconnect(UUID uuid) {
        if (uuid == null) {
            return;
        }
        players.remove(uuid);
        lastInputs.remove(uuid);
        activeScenes.remove(uuid);
        physicsStates.remove(uuid);
        lastProcessedTicks.remove(uuid);
        bodySystem.onDisconnect(uuid);
    }

    public void onSceneChanged(UUID uuid, String sceneId) {
        if (uuid == null) {
            return;
        }
        if (sceneId == null || sceneId.isBlank()) {
            activeScenes.remove(uuid);
            return;
        }
        activeScenes.put(uuid, sceneId);
        physicsStates.remove(uuid);
        bodySystem.onSceneChanged(uuid);
    }

    public void onInput(UUID uuid, PlayerInput input) {
        if (uuid == null || input == null) {
            return;
        }
        lastInputs.put(uuid, input);
    }

    public void applyEditorWorldEnvironment(ServerScene scene) {
        if (scene == null) {
            return;
        }
        worldEnvironment.ensureWorldEnvironment(scene);
        worldEnvironment.applyWorldEnvironment(scene, worldEnvironment.readWorldEnvironment(scene));
    }

    public void tick(UUID uuid, Session session, ServerScene scene) {
        if (uuid == null || session == null || scene == null) {
            return;
        }
        Player player = players.get(uuid);
        if (player == null) {
            return;
        }
        String sceneId = scene.sceneId();
        activeScenes.put(uuid, sceneId);

        worldEnvironment.ensureWorldEnvironment(scene);
        PlayerInput input = lastInputs.getOrDefault(uuid, new PlayerInput(0L, 0.0f, 0.0f, 0.0f, 0.0f, false, false));

        RuntimeCameraSystem.SelectedCamera selectedCamera = cameraSystem.selectSceneCamera(scene);
        Node body = bodySystem.findControllableBody(scene, uuid);
        boolean cameraIsUnderBody = selectedCamera != null && body != null && RuntimeSceneQueries.isDescendantOf(selectedCamera.node(), body);
        boolean allowControl = body != null && (selectedCamera == null || cameraIsUnderBody);
        boolean scriptedBody = body != null && RuntimeSceneQueries.hasScript(body);

        float x = 0.0f;
        float y = 0.0f;
        float z = 0.0f;
        float velX = 0.0f;
        float velY = 0.0f;
        float velZ = 0.0f;
        boolean onFloor = false;
        float yaw = 0.0f;
        float pitch = 0.0f;

        if (allowControl) {
            yaw = MathUtils.normalizeYaw(input.yawDeg());
            pitch = MathUtils.clampPitch(input.pitchDeg(), MIN_PITCH, MAX_PITCH);
            if (scriptedBody) {
                physicsStates.remove(uuid);
                lastProcessedTicks.put(uuid, input.clientTick());

                var authored = RuntimeTransforms.worldTransform(body);
                x = (float) authored.pos().x();
                y = (float) authored.pos().y();
                z = (float) authored.pos().z();
            } else {
                float speed = ParseUtils.parseFloat(body.getProperty("speed"), 6.0f);

                JoltScenePhysics.MovementResult moved = null;
                JoltScenePhysics physics = scene.physics();
                if (physics != null) {
                    try {
                        moved = physics.moveCharacter(uuid, body,
                                input.moveX(), input.moveZ(), yaw, speed,
                                input.jump(), input.sprint(), DT);
                    } catch (Throwable ignored) {
                    }
                }

                if (moved != null) {
                    physicsStates.remove(uuid);
                    lastProcessedTicks.put(uuid, input.clientTick());
                    x = moved.x();
                    y = moved.y();
                    z = moved.z();
                    velX = moved.velX();
                    velY = moved.velY();
                    velZ = moved.velZ();
                    onFloor = moved.onFloor();
                } else {
                    CharacterPhysics.State phys = physicsStates.get(uuid);
                    if (phys == null) {
                        var authored = RuntimeTransforms.worldTransform(body);
                        float startX = (float) authored.pos().x();
                        float startY = (float) authored.pos().y();
                        float startZ = (float) authored.pos().z();
                        phys = CharacterPhysics.State.at(startX, startY, startZ);
                    }

                    phys = CharacterPhysics.simulate(phys, input.moveX(), input.moveZ(),
                            yaw, speed, input.jump(), input.sprint(), DT);

                    physicsStates.put(uuid, phys);
                    lastProcessedTicks.put(uuid, input.clientTick());

                    x = phys.x();
                    y = phys.y();
                    z = phys.z();
                    velX = phys.velX();
                    velY = phys.velY();
                    velZ = phys.velZ();
                    onFloor = phys.onFloor();
                }
            }
        } else {
            physicsStates.remove(uuid);
            lastProcessedTicks.put(uuid, input.clientTick());
            if (selectedCamera != null) {
                x = selectedCamera.pose().x();
                y = selectedCamera.pose().y();
                z = selectedCamera.pose().z();
                yaw = selectedCamera.pose().yawDeg();
                pitch = selectedCamera.pose().pitchDeg();
            }
        }

        RuntimeWorldEnvironmentSystem.WorldEnvironment env = worldEnvironment.readWorldEnvironment(scene);
        worldEnvironment.applyWorldEnvironment(scene, env);
        RuntimeCameraSystem.SceneCamera cameraPose = null;
        if (selectedCamera != null) {
            if (allowControl && cameraIsUnderBody && body != null) {
                cameraPose = cameraSystem.createRuntimeCameraFromBody(body, selectedCamera.node(), x, y, z, yaw, pitch);
            } else {
                cameraPose = selectedCamera.pose();
            }
        }
        boolean hasCamera = true;
        boolean useSceneCamera = cameraPose != null;
        int timeTicks = (int) (scene.instance().getTime() % 24_000L);
        session.send(Lane.STATE, new RuntimeState(
                scene.engine().ticks(),
                input.clientTick(),
                sceneId,
                x,
                y,
                z,
                velX,
                velY,
                velZ,
                onFloor,
                yaw,
                pitch,
                env.fogEnabled(),
                env.fogColorR(),
                env.fogColorG(),
                env.fogColorB(),
                env.fogDensity(),
                timeTicks,
                env.weather(),
                env.ambientLight(),
                hasCamera,
                useSceneCamera,
                cameraPose == null ? 0.0f : cameraPose.x(),
                cameraPose == null ? 0.0f : cameraPose.y(),
                cameraPose == null ? 0.0f : cameraPose.z(),
                cameraPose == null ? 0.0f : cameraPose.yawDeg(),
                cameraPose == null ? 0.0f : cameraPose.pitchDeg(),
                cameraPose == null ? 0.0f : cameraPose.rollDeg(),
                body == null ? 0L : body.nodeId()
        ));

        if (cameraPose != null) {
            player.teleport(new Pos(cameraPose.x(), cameraPose.y(), cameraPose.z(), cameraPose.yawDeg(), cameraPose.pitchDeg()));
        } else {
            player.teleport(new Pos(x, y, z, yaw, pitch));
        }
    }

}
