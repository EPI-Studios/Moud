package com.moud.server.minestom.runtime;

import com.moud.core.math.Transform;
import com.moud.core.scene.Node;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.session.Session;
import com.moud.net.transport.Lane;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.core.util.MathUtils;
import com.moud.core.util.ParseUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

public final class PlayRuntime {
    private static final float MIN_PITCH = -89.0f;
    private static final float MAX_PITCH = 89.0f;

    private final Map<UUID, Player> players = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeScenes = new ConcurrentHashMap<>();
    private final RuntimeWorldEnvironmentSystem worldEnvironment = new RuntimeWorldEnvironmentSystem();
    private final RuntimeCameraSystem cameraSystem = new RuntimeCameraSystem(MIN_PITCH, MAX_PITCH);
    private final RuntimeBodySystem bodySystem = new RuntimeBodySystem();

    public void onPlayerSpawn(Player player, ServerScene scene) {
        if (player == null) {
            return;
        }
        players.put(player.getUuid(), player);
        if (scene != null) {
            activeScenes.put(player.getUuid(), scene.sceneId());
            teleportToPlayerStart(player, scene);
            syncControllableBodyToPlayer(player, scene, player.getPosition());
        }
    }

    public void onDisconnect(UUID uuid) {
        if (uuid == null) {
            return;
        }
        players.remove(uuid);
        activeScenes.remove(uuid);
        bodySystem.onDisconnect(uuid);
    }

    public void onSceneChanged(UUID uuid, String sceneId) {
        if (uuid == null) {
            return;
        }
        if (sceneId == null || sceneId.isBlank()) {
            activeScenes.remove(uuid);
        } else {
            activeScenes.put(uuid, sceneId);
        }
        bodySystem.onSceneChanged(uuid);
    }

    public void syncControllableBodyToPlayer(Player player, ServerScene scene, Pos pos) {
        if (player == null || scene == null || pos == null) {
            return;
        }
        Node body = bodySystem.findControllableBody(scene, player.getUuid());
        if (body == null) {
            return;
        }
        body.setProperty("x", ParseUtils.trimFloat((float) pos.x()));
        body.setProperty("y", ParseUtils.trimFloat((float) pos.y()));
        body.setProperty("z", ParseUtils.trimFloat((float) pos.z()));
        body.setProperty("ry", ParseUtils.trimFloat(pos.yaw()));
        body.setProperty("owner_uuid", player.getUuid().toString());
        body.setProperty("velocity_x", "0");
        body.setProperty("velocity_y", "0");
        body.setProperty("velocity_z", "0");
        body.setProperty("on_floor", "false");
        body.setProperty("on_wall", "false");
        body.setProperty("on_ceiling", "false");
    }

    public void applyEditorWorldEnvironment(ServerScene scene) {
        if (scene == null) {
            return;
        }
        worldEnvironment.ensureWorldEnvironment(scene);
        worldEnvironment.applyWorldEnvironment(scene, worldEnvironment.readWorldEnvironment(scene));
    }

    public void tick(UUID uuid, Session session, ServerScene scene,
                     Long playerCameraNodeId, float[] followCamera, float[] scriptCamera) {
        if (uuid == null || session == null || scene == null) {
            return;
        }
        Player player = players.get(uuid);
        if (player == null) {
            return;
        }
        activeScenes.put(uuid, scene.sceneId());

        worldEnvironment.ensureWorldEnvironment(scene);
        RuntimeWorldEnvironmentSystem.WorldEnvironment env = worldEnvironment.readWorldEnvironment(scene);
        worldEnvironment.applyWorldEnvironment(scene, env);

        int timeTicks = (int) (scene.instance().getTime() % 24_000L);

        if (followCamera != null && followCamera.length >= 5) {
            // client-side follow camera: send local offset, client adds player pos each frame
            session.send(Lane.STATE, new RuntimeState(
                    scene.engine().ticks(), scene.sceneId(),
                    env.fogEnabled(), env.fogColorR(), env.fogColorG(), env.fogColorB(), env.fogDensity(),
                    timeTicks, env.weather(), env.ambientLight(),
                    false, 0f, 0f, 0f, 0f, 0f, 0f,
                    true, followCamera[0], followCamera[1], followCamera[2], followCamera[3], followCamera[4],
                    false, 0f, 0f, 0f, 0f, 0f, 0f
            ));
            return;
        }
        if (scriptCamera != null && scriptCamera.length >= 6) {
            session.send(Lane.STATE, new RuntimeState(
                    scene.engine().ticks(), scene.sceneId(),
                    env.fogEnabled(), env.fogColorR(), env.fogColorG(), env.fogColorB(), env.fogDensity(),
                    timeTicks, env.weather(), env.ambientLight(),
                    false, 0f, 0f, 0f, 0f, 0f, 0f,
                    false, 0f, 0f, 0f, 0f, 0f,
                    true,
                    scriptCamera[0], scriptCamera[1], scriptCamera[2],
                    scriptCamera[3], scriptCamera[4], scriptCamera[5]
            ));
            return;
        }

        RuntimeCameraSystem.SceneCamera cameraPose;
        if (playerCameraNodeId != null && playerCameraNodeId > 0L) {
            Node camNode = scene.engine().sceneTree().getNode(playerCameraNodeId);
            if (camNode != null && "Camera3D".equals(scene.engine().nodeTypes().typeIdFor(camNode))) {
                cameraPose = cameraSystem.createSceneCameraFromNode(camNode);
            } else {
                cameraPose = null;
            }
        } else {
            RuntimeCameraSystem.SelectedCamera selectedCamera = cameraSystem.selectSceneCamera(scene);
            cameraPose = selectedCamera != null ? selectedCamera.pose() : null;
        }

        session.send(Lane.STATE, new RuntimeState(
                scene.engine().ticks(), scene.sceneId(),
                env.fogEnabled(), env.fogColorR(), env.fogColorG(), env.fogColorB(), env.fogDensity(),
                timeTicks, env.weather(), env.ambientLight(),
                cameraPose != null,
                cameraPose == null ? 0f : cameraPose.x(),
                cameraPose == null ? 0f : cameraPose.y(),
                cameraPose == null ? 0f : cameraPose.z(),
                cameraPose == null ? 0f : cameraPose.yawDeg(),
                cameraPose == null ? 0f : cameraPose.pitchDeg(),
                cameraPose == null ? 0f : cameraPose.rollDeg(),
                false, 0f, 0f, 0f, 0f, 0f,
                false, 0f, 0f, 0f, 0f, 0f, 0f
        ));

        // for static scene cameras, teleport to load chunks around the camera position
        if (playerCameraNodeId == null && cameraPose != null) {
            Pos cur = player.getPosition();
            player.teleport(new Pos(cameraPose.x(), cameraPose.y(), cameraPose.z(),
                    cur.yaw(), cur.pitch()));
        }
    }

    public static Pos findPlayerStartPos(ServerScene scene) {
        Node start = findPlayerStart(scene);
        if (start == null) {
            return null;
        }
        Transform t = RuntimeTransforms.worldTransform(start);
        float ry = ParseUtils.parseFloat(start.getProperty("ry"), 0.0f);
        ry = MathUtils.normalizeYaw(ry);
        return new Pos(t.pos().x(), t.pos().y(), t.pos().z(), ry, 0.0f);
    }

    private static void teleportToPlayerStart(Player player, ServerScene scene) {
        Pos pos = findPlayerStartPos(scene);
        if (pos != null) {
            player.teleport(pos);
        }
    }

    private static Node findPlayerStart(ServerScene scene) {
        if (scene == null) {
            return null;
        }
        Node root = scene.engine().sceneTree().root();
        if (root == null) {
            return null;
        }
        ArrayList<Node> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            if (node == null) {
                continue;
            }
            if ("PlayerStart".equals(scene.engine().nodeTypes().typeIdFor(node))) {
                return node;
            }
            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i));
            }
        }
        return null;
    }

    public static boolean sceneHasNodeType(ServerScene scene, String typeId) {
        if (scene == null || typeId == null) return false;
        Node root = scene.engine().sceneTree().root();
        if (root == null) return false;
        ArrayList<Node> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            Node node = stack.remove(stack.size() - 1);
            if (node == null) continue;
            if (typeId.equals(scene.engine().nodeTypes().typeIdFor(node))) {
                return true;
            }
            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.add(children.get(i));
            }
        }
        return false;
    }

}
